package com.nayoguildbridge

import com.nayoguildbridge.config.ChatBridgeConfigManager
import com.nayoguildbridge.config.ChatBridgeConfig
import com.nayoguildbridge.remote.RemoteBridgeApi
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

// Ет отвечает за клиент часть: хоткей и открытие меню
object ChatBridgeClient : ClientModInitializer {
    private lateinit var menuKey: KeyMapping
    private var pendingOpenFromCommand: Boolean = false

    override fun onInitializeClient() {
        ChatBridge.logger.info("[NayoGuildBridge] Initializing client entrypoint.")
        menuKey = KeyBindingHelper.registerKeyBinding(
            KeyMapping(
                "key.chatbridge.open_menu",
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.MISC
            )
        )

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal("bridge").executes { openConfigFromCommand(it.source) }
            )
            dispatcher.register(
                ClientCommandManager.literal("chatbridge").executes { openConfigFromCommand(it.source) }
            )
            dispatcher.register(
                ClientCommandManager.literal("bridgemenu").executes { openConfigFromCommand(it.source) }
            )
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (menuKey.consumeClick()) {
                client.execute { openConfig(client) }
            }

            if (pendingOpenFromCommand) {
                // Ет отвечает за отложенное открытие меню после команды из чата
                if (client.screen !is ChatScreen) {
                    pendingOpenFromCommand = false
                    client.execute { openConfig(client) }
                }
            }

            RemoteBridgeApi.tickPoll(client)
        }
    }

    private fun openConfigFromCommand(source: FabricClientCommandSource): Int {
        pendingOpenFromCommand = true
        source.sendFeedback(Component.literal("§a[NayoGuildBridge] §fОткрываю меню настроек..."))
        return 1
    }

    private fun openConfig(client: Minecraft) {
        try {
            if (!ChatBridge.hasYacl()) {
                client.player?.displayClientMessage(
                    Component.literal("§c[NayoGuildBridge] §fУ Вас не установлен YACL. Установите его иначе меню не откроетца."),
                    false
                )
                return
            }
            val parent = client.screen
            val screen = buildYaclScreen(parent) ?: run {
                client.player?.displayClientMessage(
                    Component.literal("§c[NayoGuildBridge] §fНе удалось открыть YACL меню."),
                    false
                )
                return
            }
            client.setScreen(screen)
        } catch (t: Throwable) {
            client.player?.displayClientMessage(
                Component.literal("§c[NayoGuildBridge] §fНе удалось открыть меню: ${t.javaClass.simpleName}: ${t.message ?: "unknown error"}"),
                false
            )
            t.printStackTrace()
        }
    }

    private fun buildYaclScreen(parent: net.minecraft.client.gui.screens.Screen?): net.minecraft.client.gui.screens.Screen? {
        return try {
            ChatBridgeConfigManager.build(parent)
        } catch (t: Throwable) {
            ChatBridge.logger.error("[NayoGuildBridge] Failed to create YACL screen.", t)
            null
        }
    }
}

