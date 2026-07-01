package com.nayoguildbridge.platform

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nayoguildbridge.NayoGuildBridge
import com.nayoguildbridge.config.NgbConfig
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object BridgePlatformClient {
    private val gson = Gson()
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ngb-platform-ws").apply { isDaemon = true }
    }

    private val pendingChat = ConcurrentLinkedQueue<Component>()

    @Volatile private var ws: PlatformSocket? = null
    @Volatile private var connected = false
    @Volatile private var lastConnectAttempt = 0L

    fun isActive(): Boolean {
        val c = NgbConfig.config
        return c.platformBridgeEnabled && c.platformInstanceToken.isNotBlank()
    }

    fun tick(client: Minecraft) {
        if (!isActive()) return

        val now = System.currentTimeMillis()
        if (!connected && now - lastConnectAttempt > NgbConfig.config.platformReconnectMs.coerceIn(2000, 60000)) {
            lastConnectAttempt = now
            connectAsync()
        }

        while (true) {
            val msg = pendingChat.poll() ?: break
            client.execute {
                client.player?.sendSystemMessage(msg)
            }
        }
    }

    fun sendChat(
        body: String,
        quoted: Boolean = false,
        quotedText: String? = null,
        quotedFromUser: String? = null,
        replyToMessageId: String? = null
    ) {
        if (!isActive()) return
        if (connected && ws != null) {
            val payload = JsonObject().apply {
                addProperty("type", "chat.out")
                addProperty("body", body)
                addProperty("quoted", quoted)
                if (!quotedText.isNullOrBlank()) addProperty("quotedText", quotedText)
                if (!quotedFromUser.isNullOrBlank()) addProperty("quotedFromUser", quotedFromUser)
                if (!replyToMessageId.isNullOrBlank()) addProperty("replyToMessageId", replyToMessageId)
            }
            ws?.send(payload.toString())
            return
        }
        postMessageHttp(body, quoted, quotedText, quotedFromUser, replyToMessageId)
    }

    fun syncGuildMembers(members: List<JsonObject>) {
        if (!isActive() || !connected || ws == null) return
        val arr = com.google.gson.JsonArray()
        members.forEach { arr.add(it) }
        val payload = JsonObject().apply {
            addProperty("type", "guild.sync")
            add("members", arr)
        }
        ws?.send(payload.toString())
    }

    private fun connectAsync() {
        executor.execute {
            try {
                val cfg = NgbConfig.config
                val player = Minecraft.getInstance().player
                val socket = PlatformSocket(URI(cfg.platformWsUrl.trim()))
                socket.connectBlocking(8, TimeUnit.SECONDS)
                val auth = JsonObject().apply {
                    addProperty("type", "auth")
                    addProperty("token", cfg.platformInstanceToken.trim())
                    addProperty("modVersion", NgbConfig::class.java.`package`?.implementationVersion ?: "dev")
                    player?.let {
                        addProperty("playerName", it.name.string)
                        addProperty("minecraftUuid", it.uuid.toString())
                    }
                }
                socket.send(auth.toString())
            } catch (t: Throwable) {
                NayoGuildBridge.logger.warn("[NayoGuildBridge] Platform WS connect failed: ${t.message}")
                connected = false
            }
        }
    }

    private fun postMessageHttp(
        body: String,
        quoted: Boolean,
        quotedText: String?,
        quotedFromUser: String?,
        replyToMessageId: String?
    ) {
        executor.execute {
            try {
                val cfg = NgbConfig.config
                val base = cfg.platformApiUrl.trim().trimEnd('/')
                val json = JsonObject().apply {
                    addProperty("body", body)
                    addProperty("quoted", quoted)
                    addProperty("source", "minecraft")
                    if (!quotedText.isNullOrBlank()) addProperty("quotedText", quotedText)
                    if (!quotedFromUser.isNullOrBlank()) addProperty("quotedFromUser", quotedFromUser)
                    if (!replyToMessageId.isNullOrBlank()) addProperty("replyToMessageId", replyToMessageId)
                }
                val req = HttpRequest.newBuilder()
                    .uri(URI.create("$base/api/v1/messages"))
                    .timeout(Duration.ofSeconds(8))
                    .header("content-type", "application/json")
                    .header("x-instance-token", cfg.platformInstanceToken.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build()
                http.send(req, HttpResponse.BodyHandlers.discarding())
            } catch (t: Throwable) {
                NayoGuildBridge.logger.warn("[NayoGuildBridge] Platform HTTP message failed: ${t.message}")
            }
        }
    }

    private class PlatformSocket(uri: URI) : WebSocketClient(uri) {
        override fun onOpen(handshakedata: ServerHandshake?) {
            BridgePlatformClient.ws = this
        }

        override fun onMessage(message: String?) {
            if (message.isNullOrBlank()) return
            try {
                val root = gson.fromJson(message, JsonObject::class.java) ?: return
                when (root.get("type")?.asString) {
                    "auth.ok" -> {
                        BridgePlatformClient.connected = true
                        NayoGuildBridge.logger.info("[NayoGuildBridge] Platform bridge connected.")
                    }
                    "auth.error" -> {
                        BridgePlatformClient.connected = false
                        NayoGuildBridge.logger.warn("[NayoGuildBridge] Platform auth error: ${root.get("reason")?.asString}")
                    }
                    "chat.in" -> {
                        val payload = root.getAsJsonObject("payload") ?: return
                        pendingChat.add(BridgeChatFormatter.formatIncoming(payload))
                    }
                    "pong" -> { /* ok */ }
                }
            } catch (t: Throwable) {
                NayoGuildBridge.logger.debug("[NayoGuildBridge] WS parse error: ${t.message}")
            }
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
            BridgePlatformClient.connected = false
            BridgePlatformClient.ws = null
        }

        override fun onError(ex: Exception?) {
            BridgePlatformClient.connected = false
        }
    }
}
