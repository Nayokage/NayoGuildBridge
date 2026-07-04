package com.nayoguildbridge.ims

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nayoguildbridge.NayoGuildBridge
import com.nayoguildbridge.config.BridgeEndpoints
import com.nayoguildbridge.config.NgbConfig
import com.nayoguildbridge.util.BridgeTextUtil
import com.nayoguildbridge.util.PlayerIdentity
import com.nayoguildbridge.quote.QuoteContextRegistry
import com.nayoguildbridge.quote.QuoteDetector
import com.nayoguildbridge.util.ItemStackJson
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object ImsBridgeClient {
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ngb-ims-ws").apply { isDaemon = true }
    }

    @Volatile private var ws: ImsSocket? = null
    @Volatile private var connected = false
    @Volatile private var lastConnectAttempt = 0L
    @Volatile private var guildTag: String = ""
    @Volatile private var guildColor: String = "§a"
    @Volatile private var guildName: String = ""
    @Volatile private var guildId: String = ""
    @Volatile private var lastError: String = ""
    @Volatile private var authBlockedUntil = 0L
    @Volatile private var wsEndpointIndex = 0
    @Volatile private var connectInProgress = false
    @Volatile private var lastStatusChatAt = 0L
    @Volatile private var connectedNoticeShown = false
    @Volatile private var lastOnlineRequestAt = 0L
    @Volatile private var cachedOnlineJson: JsonObject? = null
    @Volatile private var pendingOnlineDisplay = false

    private const val STATUS_CHAT_COOLDOWN_MS = 60_000L
    private const val ONLINE_REFRESH_MS = 45_000L

    private val wsEndpoints = BridgeEndpoints.wsEndpoints()

    fun isActive(): Boolean {
        if (!com.nayoguildbridge.guard.EnvironmentGuard.isOperational()) return false
        val c = NgbConfig.config
        val needsWs =
            c.imsBridgeEnabled ||
                c.imsCombinedBridgeEnabled ||
                c.imsCombinedBridgeChatEnabled ||
                c.imsWebOnlyMode
        if (!needsWs) return false
        if (c.imsAuthByNick) return true
        return c.bridgeKey.isNotBlank()
    }

    fun isConnected(): Boolean = connected

    fun guildDisplayName(): String = guildName.ifBlank { guildTag.ifBlank { NgbConfig.config.imsGuildTag } }

    fun localGuildId(): String = guildId

    fun statusLine(): String {
        val cfg = NgbConfig.config
        val poll = if (com.nayoguildbridge.bridge.PlatformBridgePoll.isActive()) "poll-on" else "poll-off"
        val wsNote = if (cfg.imsBridgeEnabled) "ws-on" else "ws-off"
        return "poll=$poll $wsNote api=${BridgeEndpoints.PRIMARY_HTTP} err=${lastError.ifBlank { "none" }}"
    }

    fun reconnectNow() {
        authBlockedUntil = 0L
        disconnect()
        lastConnectAttempt = 0L
        connectInProgress = false
        connectedNoticeShown = false
        connectAsync()
    }

    fun disconnect() {
        try {
            ws?.close()
        } catch (_: Throwable) {
        } finally {
            ws = null
            connected = false
        }
    }

    fun tick(client: Minecraft) {
        if (!isActive()) return
        val now = System.currentTimeMillis()
        if (now < authBlockedUntil) return
        val delay = NgbConfig.config.platformReconnectMs.coerceIn(5000, 60000)
        if (!connected && now - lastConnectAttempt > delay) {
            lastConnectAttempt = now
            connectAsync()
        }
        if (connected && now - lastOnlineRequestAt > ONLINE_REFRESH_MS) {
            lastOnlineRequestAt = now
            requestOnlinePlayers(silent = true)
        }
    }

    fun sendGuildMessage(rawContent: String) {
        if (!canSend()) return
        val client = Minecraft.getInstance()
        val payload = JsonObject().apply {
            addProperty("from", "mc")
            addProperty("msg", rawContent)
            addProperty("originInstance", PlayerIdentity.mcInstanceName(client))
        }
        ws?.send(payload.toString())
    }

    fun sendWebOnlyRelay(rawContent: String) {
        if (!canSend()) return
        val payload = JsonObject().apply {
            addProperty("from", "mc")
            addProperty("msg", rawContent)
            addProperty("webOnlyRelay", "true")
        }
        ws?.send(payload.toString())
    }

    fun sendGuildMemberChange(content: String) {
        if (!canSend()) return
        val payload = JsonObject().apply {
            addProperty("from", "mc")
            addProperty("msg", content)
            addProperty("guildMemberChange", "true")
        }
        ws?.send(payload.toString())
    }

    fun sendCombinedMessage(text: String) {
        if (!canSend()) return
        val client = Minecraft.getInstance()
        val payload = JsonObject().apply {
            addProperty("from", "mc")
            addProperty("msg", text)
            addProperty("combinedbridge", "true")
            addProperty("originInstance", PlayerIdentity.mcInstanceName(client))
        }
        ws?.send(payload.toString())
    }

    fun sendShowItem(stack: ItemStack) {
        if (!canSend()) return
        val qty = if (stack.count > 1) " x${stack.count}" else ""
        val display = "is holding [${stack.hoverName.string}$qty]"
        val payload = JsonObject().apply {
            addProperty("from", "show")
            addProperty("msg", display)
            addProperty("show", "true")
            addProperty("combinedbridge", if (NgbConfig.config.imsCombinedBridgeEnabled) "true" else "false")
            ItemStackJson.toJsonStack(stack)?.let { addProperty("jsonStack", it) }
        }
        ws?.send(payload.toString())
    }

    fun sendQuote(quote: QuoteDetector.Result) {
        if (!canSend()) return
        val player = Minecraft.getInstance().player ?: return
        val enriched = QuoteContextRegistry.enrich(quote, guildId.ifBlank { null })
        val messageId = UUID.randomUUID().toString()
        val quotedMessageEffective = enriched.quotedMessage?.let { BridgeTextUtil.stripBridgeFormatting(it) }?.takeIf { it.isNotEmpty() } ?: "—"
        var quotedFromUser = enriched.quotedFromUser?.trim()?.takeIf { it.isNotEmpty() } ?: player.name.string
        val replyBody = BridgeTextUtil.stripBridgeFormatting(enriched.body).trim()
        if (replyBody.isEmpty()) return

        val payload = JsonObject().apply {
            addProperty("from", "mc")
            addProperty("type", "quote")
            addProperty("quoted", true)
            addProperty("id", messageId)
            addProperty("message_id", messageId)
            addProperty("msg", replyBody)
            addProperty("message", replyBody)
            addProperty("body", replyBody)
            addProperty("text", replyBody)
            addProperty("source", "minecraft")
            addProperty("source_mod", "nayoguildbridge-fabric")
            addProperty("quotedMessage", quotedMessageEffective)
            addProperty("quotedText", quotedMessageEffective)
            addProperty("quotedFromUser", quotedFromUser)
            addProperty("replyToUser", quotedFromUser)
            BridgeTextUtil.normalizeSourceTag(enriched.quotedFromInstance)?.let {
                addProperty("quotedFromInstance", it)
                addProperty("quotedSource", it)
            }
            addProperty("timestamp", System.currentTimeMillis())
            if (!enriched.replyToMessageId.isNullOrBlank()) {
                addProperty("reply_to_message_id", enriched.replyToMessageId)
                addProperty("replyToMessageId", enriched.replyToMessageId)
            }
            if (enriched.isCrossGuildQuote) {
                addProperty("isCrossGuildQuote", true)
                enriched.originalGuildId?.let { addProperty("originalGuildId", it) }
                enriched.originalGuildName?.let { addProperty("originalGuildName", it) }
            }
        }
        ws?.send(payload.toString())
    }

    fun requestOnlinePlayers(silent: Boolean = false) {
        if (!connected || ws == null) return
        if (!silent && cachedOnlineJson != null) {
            ImsChatDisplay.showOnlinePlayers(cachedOnlineJson!!)
            return
        }
        pendingOnlineDisplay = !silent
        ws?.send("""{"request":"getOnlinePlayers"}""")
    }

    private fun canSend(): Boolean = isActive() && connected && ws != null

    private fun connectAsync() {
        if (System.currentTimeMillis() < authBlockedUntil) return
        if (connectInProgress || connected) return
        connectInProgress = true
        lastConnectAttempt = System.currentTimeMillis()
        executor.execute {
            try {
                val cfg = NgbConfig.config
                val player = Minecraft.getInstance().player
                val wsUrl = wsEndpoints[wsEndpointIndex.coerceIn(0, wsEndpoints.lastIndex)]
                val uri = URI(wsUrl)
                val socket = ImsSocket(uri)
                socket.connectBlocking(8, TimeUnit.SECONDS)
                val auth = JsonObject().apply {
                    addProperty("from", "mc")
                    if (cfg.imsAuthByNick) {
                        val nick = player?.name?.string?.trim().orEmpty()
                        if (nick.isNotBlank()) addProperty("minecraftName", nick)
                        val slug = cfg.imsGuildSlug.trim()
                        if (slug.isNotBlank()) addProperty("guildSlug", slug)
                    } else if (cfg.bridgeKey.isNotBlank()) {
                        addProperty("key", cfg.bridgeKey.trim())
                    }
                }
                socket.send(auth.toString())
            } catch (t: Throwable) {
                NayoGuildBridge.logger.warn("[NayoGuildBridge][IMS] connect failed: ${t.message}")
                lastError = t.message ?: "connect_failed"
                connected = false
                wsEndpointIndex = (wsEndpointIndex + 1) % wsEndpoints.size
            } finally {
                connectInProgress = false
            }
        }
    }

    private fun showInGame(text: String) {
        val mc = Minecraft.getInstance()
        mc.execute {
            mc.player?.sendSystemMessage(Component.literal(text))
        }
    }

    private fun showStatusThrottled(text: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastStatusChatAt < STATUS_CHAT_COOLDOWN_MS) return
        lastStatusChatAt = now
        showInGame(text)
    }

    private class ImsSocket(uri: URI) : WebSocketClient(uri) {
        override fun onOpen(handshakedata: ServerHandshake?) {
            ImsBridgeClient.ws = this
        }

        override fun onMessage(message: String?) {
            if (message.isNullOrBlank()) return
            try {
                val root = JsonParser.parseString(message).asJsonObject
                when {
                    root.get("from")?.asString == "server" &&
                        root.get("type")?.asString == "auth_success" -> {
                        ImsBridgeClient.connected = true
                        lastConnectAttempt = System.currentTimeMillis()
                        lastError = ""
                        guildTag = root.get("guildTag")?.asString ?: NgbConfig.config.imsGuildTag
                        guildColor = root.get("guildColor")?.asString ?: NgbConfig.config.imsGuildColor
                        guildName = root.get("guild")?.asString ?: ""
                        guildId = root.get("guildId")?.asString ?: ""
                        val g = guildDisplayName()
                        val notice = "§a[NayoGuildBridge] §fМост подключён. §7Гильдия: §e$g"
                        if (!connectedNoticeShown) {
                            connectedNoticeShown = true
                            showStatusThrottled(notice, force = true)
                        } else {
                            showStatusThrottled(notice)
                        }
                    }
                    root.get("from")?.asString == "server" &&
                        root.get("type")?.asString == "auth_failed" -> {
                        ImsBridgeClient.connected = false
                        lastError = root.get("message")?.asString ?: "auth_failed"
                        authBlockedUntil = System.currentTimeMillis() + 5 * 60_000L
                        showInGame("§c[NayoGuildBridge] §f$lastError §7(повтор через 5 мин или /bridge reconnect)")
                        try {
                            close()
                        } catch (_: Throwable) {
                        }
                    }
                    root.get("from")?.asString == "server" &&
                        root.get("type")?.asString == "quote_ack" -> {
                        showStatusThrottled("§a[NayoGuildBridge] §fЦитата отправлена в Discord/Telegram.")
                    }
                    root.get("from")?.asString == "server" &&
                        root.get("type")?.asString == "quote_error" -> {
                        val msg = root.get("message")?.asString ?: "Ошибка цитирования"
                        showInGame("§c$msg")
                    }
                    root.has("response") && root.get("request")?.asString == "getOnlinePlayers" -> {
                        cachedOnlineJson = root.getAsJsonObject("response")
                        if (pendingOnlineDisplay) {
                            pendingOnlineDisplay = false
                            ImsChatDisplay.showOnlinePlayers(cachedOnlineJson!!)
                        }
                    }
                    root.get("from")?.asString == "server" &&
                        root.get("type")?.asString == "online_update" &&
                        root.has("response") -> {
                        cachedOnlineJson = root.getAsJsonObject("response")
                    }
                    root.has("from") && root.has("msg") -> {
                        ImsChatDisplay.displayIncoming(root, guildTag, guildColor)
                    }
                }
            } catch (t: Throwable) {
                lastError = t.message ?: "parse_error"
                NayoGuildBridge.logger.debug("[NayoGuildBridge][IMS] parse: ${t.message}")
            }
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
            ImsBridgeClient.connected = false
            ImsBridgeClient.ws = null
            connectInProgress = false
            lastError = reason ?: "closed"
        }

        override fun onError(ex: Exception?) {
            ImsBridgeClient.connected = false
            lastError = ex?.message ?: "socket_error"
        }
    }
}
