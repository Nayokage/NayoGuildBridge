package com.nayoguildbridge

import com.nayoguildbridge.bridge.BridgeChatDedupe
import com.nayoguildbridge.bridge.BridgeOutboundForwarder
import com.nayoguildbridge.bridge.BridgeRouter
import com.nayoguildbridge.bridge.IncomingBridgeFormatter
import com.nayoguildbridge.bridge.BridgeHealthCache
import com.nayoguildbridge.bridge.PlatformBridgePoll
import com.nayoguildbridge.guard.EnvironmentGuard
import com.nayoguildbridge.preview.ImagePreviewHandler
import com.nayoguildbridge.config.NgbConfig
import com.nayoguildbridge.config.NgbConfigManager
import com.nayoguildbridge.ims.ImsBridgeClient
import com.nayoguildbridge.qol.ChatQoL
import com.nayoguildbridge.qol.UpdateCheck
import com.nayoguildbridge.quote.ConfigManager as QuoteConfigManager
import com.nayoguildbridge.quote.QuoteDetector
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

object NayoGuildBridgeClient : ClientModInitializer {
    private lateinit var menuKey: KeyMapping
    private var pendingOpenFromCommand: Boolean = false
    private var muteHintShown: Boolean = false

    override fun onInitializeClient() {
        NayoGuildBridge.logger.info("[NayoGuildBridge] Initializing client entrypoint.")
        ImagePreviewHandler.register()
        EnvironmentGuard.register()
        menuKey = KeyBindingHelper.registerKeyBinding(
            KeyMapping(
                "key.ngb.open_menu",
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.MISC
            )
        )

        ClientSendMessageEvents.ALLOW_CHAT.register { message ->
            if (!EnvironmentGuard.isOperational()) return@register true
            if (message.startsWith("/")) return@register true

            val cfg = NgbConfig.config
            if (QuoteConfigManager.quoteSystemEnabled()) {
                val quote = QuoteDetector.detect(message)
                if (quote.quoted) {
                    if (quote.body.isBlank()) return@register false
                    BridgeRouter.sendQuote(quote)
                    showLocalQuoteOutgoing(quote)
                    return@register false
                }
            }

            if (
                cfg.imsCombinedBridgeEnabled &&
                cfg.imsCombinedBridgeChatEnabled &&
                BridgeRouter.canSendCombined()
            ) {
                val body = ChatQoL.applyOutgoingBody(message)
                BridgeRouter.sendCombined(body)
                showLocalCombinedOutgoing(body)
                return@register false
            }
            return@register true
        }

        ClientSendMessageEvents.CHAT.register { message ->
            if (!EnvironmentGuard.isOperational()) return@register
            if (message.startsWith("/")) return@register
            val cfg = NgbConfig.config
            if (cfg.imsCombinedBridgeEnabled && cfg.imsCombinedBridgeChatEnabled) return@register
            if (!BridgeRouter.isConnected()) return@register
            if (cfg.imsWebOnlyMode) {
                BridgeOutboundForwarder.forwardPlayerChat(message)
            }
        }

        registerCommands()

        ClientReceiveMessageEvents.ALLOW_CHAT.register { message, _, sender, _, _ ->
            if (!EnvironmentGuard.isOperational()) return@register true
            if (!NgbConfig.config.bridgeEnabled) return@register true

            val senderName = sender?.name
            when (val transform = NayoGuildBridge.transformIncomingMessage(message, false, senderName)) {
                is NayoGuildBridge.ChatTransform.Keep -> true
                is NayoGuildBridge.ChatTransform.Hide -> false
                is NayoGuildBridge.ChatTransform.Replace -> {
                    // Game overlay messages are already replaced by MODIFY_GAME; chat packets need injection.
                    val client = Minecraft.getInstance()
                    client.execute {
                        client.gui.chat.addMessage(transform.component)
                    }
                    false
                }
            }
        }

        ClientReceiveMessageEvents.ALLOW_GAME.register { message, _ ->
            if (!EnvironmentGuard.isOperational()) return@register true
            val content = message.string
            if (!NgbConfig.config.bridgeEnabled) return@register true

            if (isHypixelMuteMessage(content)) {
                maybeShowWebOnlyHint()
            }

            val cfg = NgbConfig.config
            val isGuildLine =
                content.contains("Guild >") || content.contains("§2Guild >") ||
                    content.contains("Officer >")

            when {
                isGuildLine -> {
                    when {
                        cfg.imsWebOnlyMode -> BridgeOutboundForwarder.forwardGuildLine(content)
                        NayoGuildBridge.isLocalPlayerGuildLine(content) ->
                            BridgeOutboundForwarder.forwardGuildLine(content)
                    }
                }
                content.endsWith(" joined the guild!") || content.endsWith(" left the guild!") ->
                    ImsBridgeClient.sendGuildMemberChange(content)
                cfg.imsPartyBridgeEnabled && (content.contains("Party >") || content.contains("§9Party >")) -> {
                    if (BridgeRouter.isConnected()) {
                        if (cfg.imsWebOnlyMode) BridgeRouter.sendWebOnlyRelay(content)
                        else BridgeRouter.sendBridgeChat(content)
                    }
                }
            }
            true
        }

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            muteHintShown = false
            UpdateCheck.checkOnJoin()
            if (BridgeRouter.isActive()) {
                ImsBridgeClient.tick(Minecraft.getInstance())
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (menuKey.consumeClick()) {
                client.execute { openConfig(client) }
            }
            if (pendingOpenFromCommand && client.screen !is ChatScreen) {
                pendingOpenFromCommand = false
                client.execute { openConfig(client) }
            }
            if (EnvironmentGuard.isOperational()) {
                if (BridgeRouter.isActive()) {
                    ImsBridgeClient.tick(client)
                }
                PlatformBridgePoll.tick(client)
            }
        }
    }

    private fun showLocalCombinedOutgoing(body: String) {
        val player = Minecraft.getInstance().player?.name?.string?.trim().orEmpty()
        if (player.isBlank() || body.isBlank()) return
        BridgeChatDedupe.remember(BridgeChatDedupe.keyFor(player, body))
        val formatted = IncomingBridgeFormatter.formatLocalOutgoing(player, body, combined = true)
        Minecraft.getInstance().execute {
            Minecraft.getInstance().player?.displayClientMessage(formatted, false)
        }
    }

    private fun showLocalQuoteOutgoing(quote: QuoteDetector.Result) {
        val player = Minecraft.getInstance().player?.name?.string?.trim().orEmpty()
        if (player.isBlank() || quote.body.isBlank()) return
        val cfg = NgbConfig.config
        val combined = cfg.imsCombinedBridgeEnabled && cfg.imsCombinedBridgeChatEnabled
        BridgeChatDedupe.remember(BridgeChatDedupe.keyFor(player, quote.body))
        val formatted = IncomingBridgeFormatter.formatLocalQuoteOutgoing(player, quote, combined)
        Minecraft.getInstance().execute {
            Minecraft.getInstance().player?.displayClientMessage(formatted, false)
        }
    }

    private fun isHypixelMuteMessage(content: String): Boolean {
        val lower = content.lowercase()
        return lower.contains("mute") ||
            lower.contains("you are not allowed") ||
            lower.contains("cannot speak") ||
            lower.contains("заглушен") ||
            lower.contains("замучен")
    }

    private fun maybeShowWebOnlyHint() {
        if (muteHintShown || NgbConfig.config.imsWebOnlyMode) return
        muteHintShown = true
        Minecraft.getInstance().player?.displayClientMessage(
            Component.literal(
                "§e[NGB] §fНа Hypixel вы в mute. Включите §bimsWebOnlyMode§f в конфиге — bridge через WS + гильд-чат останется."
            ),
            false
        )
    }

    private fun registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            val bridge = ClientCommandManager.literal("bridge")
            dispatcher.register(bridge.executes { openConfigFromCommand(it.source) })
            dispatcher.register(
                bridge
                    .then(ClientCommandManager.literal("help").executes {
                        it.source.sendFeedback(
                            Component.literal(
                                "§9/bridge status|fix|reconnect|toggle|online|show|colour|copy|ignore\n" +
                                    "§9/bc <msg> §7— guild bridge\n§9/cbc <msg> §7— combined\n" +
                                    "§9/cbridge toggle|chat|party §7| §9/chat b"
                            )
                        )
                        1
                    })
                    .then(ClientCommandManager.literal("status").executes {
                        val poll = if (PlatformBridgePoll.isActive()) "§apoll ON" else "§7poll OFF"
                        val ws = when {
                            !BridgeRouter.isActive() -> "§7ws OFF"
                            BridgeRouter.isConnected() -> "§aws OK"
                            else -> "§ews…"
                        }
                        val cbRecv = if (NgbConfig.config.imsCombinedBridgeEnabled) "§dCB recv ON" else "§7CB recv OFF"
                        val cbSend = if (NgbConfig.config.imsCombinedBridgeChatEnabled) {
                            val via = if (NgbConfig.config.imsBridgeEnabled) "ws" else "http"
                            "§dCB send ON ($via)"
                        } else "§7CB send OFF"
                        val quotes = if (QuoteConfigManager.quoteSystemEnabled()) "§aquotes ON" else "§cquotes OFF"
                        val recv = if (NgbConfig.config.imsBridgeReceiveEnabled) "§arecv ON" else "§crecv OFF"
                        it.source.sendFeedback(
                            Component.literal(
                                "§a[NGB] §f$poll §7| $ws §7| $cbRecv §7| $cbSend §7| $quotes §7| $recv\n" +
                                    "§7${com.nayoguildbridge.config.BridgeEndpoints.statusLine()}\n" +
                                    "§7${ImsBridgeClient.statusLine()} §7| ${BridgeHealthCache.statusSuffix()}\n" +
                                    "§7Discord→MC: overlay WS. DS/TG fanout: §fbr1dgebtw§7 (§fdiscordBot:false§7 = норма).\n" +
                                    "§7Цитаты: §f> [Dis] Nick: | ответ§7 или клик §f[q]§7. CB send: §f/cbridge chat"
                            )
                        )
                        1
                    })
                    .then(ClientCommandManager.literal("fix").executes {
                        NgbConfig.applyWorkingDefaults()
                        it.source.sendFeedback(
                            Component.literal(
                                "§a[NGB] §fСброс: poll ON, WS OFF, CB OFF, guild-guard OFF.\n" +
                                    "§7Обычный bridge (бот в guild + цитаты HTTP) работает без WS."
                            )
                        )
                        1
                    })
                    .then(ClientCommandManager.literal("reconnect").executes {
                        ImsBridgeClient.reconnectNow()
                        it.source.sendFeedback(Component.literal("§a[NGB] §fПереподключаю bridge WS..."))
                        1
                    })
                    .then(ClientCommandManager.literal("toggle").executes {
                        val cfg = NgbConfig.config
                        cfg.imsBridgeEnabled = !cfg.imsBridgeEnabled
                        NgbConfig.save()
                        if (cfg.imsBridgeEnabled) {
                            ImsBridgeClient.reconnectNow()
                        } else {
                            ImsBridgeClient.disconnect()
                        }
                        it.source.sendFeedback(
                            Component.literal(
                                "§a[NGB] §fWebSocket: ${if (cfg.imsBridgeEnabled) "ON (переподключение…)" else "OFF"}\n" +
                                    "§7Не путать с CB (combined). Guild + цитаты работают и без WS."
                            )
                        )
                        1
                    })
                    .then(ClientCommandManager.literal("online").executes {
                        ImsBridgeClient.requestOnlinePlayers()
                        1
                    })
                    .then(ClientCommandManager.literal("copy").executes {
                        if (!NgbConfig.config.copyChatEnabled) {
                            it.source.sendFeedback(Component.literal("§c[NGB] §fcopyChat выключен в конфиге."))
                        } else {
                            ChatQoL.copyLastLineToClipboard()
                            it.source.sendFeedback(Component.literal("§a[NGB] §fПоследняя строка чата скопирована (Ctrl+V)."))
                        }
                        1
                    })
                    .then(ClientCommandManager.literal("show").executes {
                        val stack = Minecraft.getInstance().player?.mainHandItem
                        if (stack == null || stack.isEmpty) {
                            it.source.sendFeedback(Component.literal("§c[NGB] §fНужно держать предмет в руке."))
                        } else {
                            BridgeRouter.sendShowItem(stack)
                            it.source.sendFeedback(Component.literal("§a[NGB] §fПредмет отправлен в bridge (jsonStack)."))
                        }
                        1
                    })
                    .then(
                        ClientCommandManager.literal("colour")
                            .then(
                                ClientCommandManager.literal("reset").executes {
                                    resetBridgeColours()
                                    it.source.sendFeedback(Component.literal("§a[NGB] §fЦвета bridge сброшены."))
                                    1
                                }
                            )
                            .then(
                                ClientCommandManager.argument("prefix", StringArgumentType.word())
                                    .then(
                                        ClientCommandManager.argument("name", StringArgumentType.word())
                                            .then(
                                                ClientCommandManager.argument("message", StringArgumentType.word())
                                                    .executes { ctx ->
                                                        NgbConfig.config.prefixColor = StringArgumentType.getString(ctx, "prefix")
                                                        NgbConfig.config.nameColor = StringArgumentType.getString(ctx, "name")
                                                        NgbConfig.config.messageColor = StringArgumentType.getString(ctx, "message")
                                                        NgbConfig.save()
                                                        ctx.source.sendFeedback(Component.literal("§a[NGB] §fЦвета bridge обновлены."))
                                                        1
                                                    }
                                            )
                                    )
                            )
                    )
                    .then(buildIgnoreCommands())
            )

            dispatcher.register(ClientCommandManager.literal("ngb").executes { openConfigFromCommand(it.source) })
            dispatcher.register(ClientCommandManager.literal("bridgemenu").executes { openConfigFromCommand(it.source) })
            dispatcher.register(ClientCommandManager.literal("ngbonline").executes { ImsBridgeClient.requestOnlinePlayers(); 1 })
            dispatcher.register(ClientCommandManager.literal("bl").executes { ImsBridgeClient.requestOnlinePlayers(); 1 })

            dispatcher.register(
                ClientCommandManager.literal("ngbimage")
                    .then(
                        ClientCommandManager.literal("open")
                            .then(
                                ClientCommandManager.argument("token", StringArgumentType.word()).executes { ctx ->
                                    ImagePreviewHandler.openPreview(StringArgumentType.getString(ctx, "token"))
                                    1
                                }
                            )
                    )
            )

            dispatcher.register(
                ClientCommandManager.literal("bc")
                    .then(
                        ClientCommandManager.argument("message", StringArgumentType.greedyString()).executes { ctx ->
                            val msg = StringArgumentType.getString(ctx, "message")
                            BridgeRouter.sendBridgeChat(ChatQoL.applyOutgoingBody(msg))
                            ctx.source.sendFeedback(Component.literal("§a[NGB] §fОтправлено в guild bridge."))
                            1
                        }
                    )
            )
            dispatcher.register(
                ClientCommandManager.literal("cbc")
                    .then(
                        ClientCommandManager.argument("message", StringArgumentType.greedyString()).executes { ctx ->
                            val msg = StringArgumentType.getString(ctx, "message")
                            val body = ChatQoL.applyOutgoingBody(msg)
                            BridgeRouter.sendCombined(body)
                            showLocalCombinedOutgoing(body)
                            ctx.source.sendFeedback(Component.literal("§a[NGB] §fОтправлено в combined bridge."))
                            1
                        }
                    )
            )

            dispatcher.register(
                ClientCommandManager.literal("cbridge")
                    .then(ClientCommandManager.literal("toggle").executes {
                        NgbConfig.config.imsCombinedBridgeEnabled = !NgbConfig.config.imsCombinedBridgeEnabled
                        NgbConfig.save()
                        it.source.sendFeedback(
                            Component.literal("§a[NGB] §fCombined bridge: ${if (NgbConfig.config.imsCombinedBridgeEnabled) "ON" else "OFF"}")
                        )
                        1
                    })
                    .then(ClientCommandManager.literal("chat").executes {
                        NgbConfig.config.imsCombinedBridgeChatEnabled = !NgbConfig.config.imsCombinedBridgeChatEnabled
                        NgbConfig.save()
                        val via = if (NgbConfig.config.imsBridgeEnabled) "WS" else "HTTP"
                        it.source.sendFeedback(
                            Component.literal(
                                "§a[NGB] §fCombined chat: ${if (NgbConfig.config.imsCombinedBridgeChatEnabled) "ON ($via)" else "OFF"}"
                            )
                        )
                        1
                    })
                    .then(ClientCommandManager.literal("party").executes {
                        NgbConfig.config.imsPartyBridgeEnabled = !NgbConfig.config.imsPartyBridgeEnabled
                        NgbConfig.save()
                        it.source.sendFeedback(
                            Component.literal("§a[NGB] §fParty bridge relay: ${if (NgbConfig.config.imsPartyBridgeEnabled) "ON" else "OFF"}")
                        )
                        1
                    })
            )

            dispatcher.register(
                ClientCommandManager.literal("chat")
                    .then(ClientCommandManager.literal("b").executes {
                        NgbConfig.config.imsCombinedBridgeChatEnabled = true
                        NgbConfig.config.imsCombinedBridgeEnabled = true
                        NgbConfig.save()
                        val via = if (NgbConfig.config.imsBridgeEnabled) "WS" else "HTTP"
                        it.source.sendFeedback(
                            Component.literal("§a[NGB] §fCB ON — отправка через $via (настройка WebSocket мост).")
                        )
                        1
                    })
            )
        }
    }

    private fun buildIgnoreCommands() = ClientCommandManager.literal("ignore")
        .then(
            ClientCommandManager.literal("add")
                .then(
                    ClientCommandManager.literal("player")
                        .then(ClientCommandManager.argument("value", StringArgumentType.word()).executes { ctx ->
                            val v = StringArgumentType.getString(ctx, "value")
                            NgbConfig.config.imsIgnorePlayers = (NgbConfig.config.imsIgnorePlayers + v).distinct()
                            NgbConfig.save()
                            ctx.source.sendFeedback(Component.literal("§a[NGB] §fИгнор игрока: $v"))
                            1
                        })
                )
                .then(
                    ClientCommandManager.literal("origin")
                        .then(ClientCommandManager.argument("value", StringArgumentType.word()).executes { ctx ->
                            val v = StringArgumentType.getString(ctx, "value")
                            NgbConfig.config.imsIgnoreOrigins = (NgbConfig.config.imsIgnoreOrigins + v).distinct()
                            NgbConfig.save()
                            ctx.source.sendFeedback(Component.literal("§a[NGB] §fИгнор источника: $v"))
                            1
                        })
                )
        )
        .then(
            ClientCommandManager.literal("remove")
                .then(
                    ClientCommandManager.literal("player")
                        .then(ClientCommandManager.argument("value", StringArgumentType.word()).executes { ctx ->
                            val v = StringArgumentType.getString(ctx, "value")
                            NgbConfig.config.imsIgnorePlayers =
                                NgbConfig.config.imsIgnorePlayers.filterNot { it.equals(v, true) }
                            NgbConfig.save()
                            ctx.source.sendFeedback(Component.literal("§a[NGB] §fИгнор игрока удалён: $v"))
                            1
                        })
                )
                .then(
                    ClientCommandManager.literal("origin")
                        .then(ClientCommandManager.argument("value", StringArgumentType.word()).executes { ctx ->
                            val v = StringArgumentType.getString(ctx, "value")
                            NgbConfig.config.imsIgnoreOrigins =
                                NgbConfig.config.imsIgnoreOrigins.filterNot { it.equals(v, true) }
                            NgbConfig.save()
                            ctx.source.sendFeedback(Component.literal("§a[NGB] §fИгнор источника удалён: $v"))
                            1
                        })
                )
        )
        .then(
            ClientCommandManager.literal("list").executes { ctx ->
                ctx.source.sendFeedback(
                    Component.literal(
                        "§eИгнор players: ${NgbConfig.config.imsIgnorePlayers.joinToString(", ").ifBlank { "—" }}\n" +
                            "§eИгнор origins: ${NgbConfig.config.imsIgnoreOrigins.joinToString(", ").ifBlank { "—" }}"
                    )
                )
                1
            }
        )

    private fun resetBridgeColours() {
        NgbConfig.config.prefixColor = "#55FF55"
        NgbConfig.config.nameColor = "#8F99FF"
        NgbConfig.config.messageColor = "#C1C3C7"
        NgbConfig.config.officerPrefixColor = "#FF5555"
        NgbConfig.config.imsGuildColor = "§a"
        NgbConfig.save()
    }

    private fun openConfigFromCommand(source: FabricClientCommandSource): Int {
        pendingOpenFromCommand = true
        source.sendFeedback(Component.literal("§a[NayoGuildBridge] §fОткрываю меню настроек..."))
        return 1
    }

    private fun openConfig(client: Minecraft) {
        try {
            if (!NayoGuildBridge.hasYacl()) {
                client.player?.displayClientMessage(
                    Component.literal("§c[NayoGuildBridge] §fУстановите YACL для меню настроек."),
                    false
                )
                return
            }
            val screen = NgbConfigManager.build(client.screen) ?: return
            client.setScreen(screen)
        } catch (t: Throwable) {
            client.player?.displayClientMessage(
                Component.literal("§c[NayoGuildBridge] §fОшибка меню: ${t.message}"),
                false
            )
        }
    }
}
