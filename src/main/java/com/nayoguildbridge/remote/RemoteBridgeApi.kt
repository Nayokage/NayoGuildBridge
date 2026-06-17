package com.nayoguildbridge.remote

import com.nayoguildbridge.NayoGuildBridge
import com.nayoguildbridge.bridge.BridgeRouter
import com.nayoguildbridge.config.NgbConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.nio.charset.StandardCharsets

object RemoteBridgeApi {
    private val gson = Gson()
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
    private val executor: Executor = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "ngb-remote").apply { isDaemon = true }
    }

    @Volatile
    private var lastPollAtMs: Long = 0L

    private fun baseUrls(): List<String> = BridgeRouter.httpApiBases()

    private fun playerKey(): String {
        val player = Minecraft.getInstance().player
        return player?.name?.string?.trim().orEmpty().ifBlank { "unknown-player" }
    }

    private fun isRemoteBridgeActive(): Boolean {
        val cfg = NgbConfig.config
        return cfg.bridgeEnabled && cfg.remoteBridgeEnabled
    }

    fun enqueueIngest(
        kind: String,
        author: String,
        text: String,
        source: String,
        replyToUser: String? = null,
        replyToPreview: String? = null,
        playerName: String? = null,
        playerUuid: String? = null,
        server: String? = null
    ) {
        if (!isRemoteBridgeActive()) return
        val cfg = NgbConfig.config
        val baseUrls = baseUrls()
        val playerKey = playerKey()
        if (baseUrls.isEmpty()) return

        val body = JsonObject().apply {
            addProperty("channelId", cfg.remoteBridgeChannelId.ifEmpty { "default" })
            addProperty("kind", kind)
            addProperty("author", author)
            addProperty("text", text)
            addProperty("source", source)
            if (!replyToUser.isNullOrBlank()) addProperty("replyToUser", replyToUser)
            if (!replyToPreview.isNullOrBlank()) addProperty("replyToPreview", replyToPreview)
            if (!playerName.isNullOrBlank()) addProperty("playerName", playerName)
            if (!playerUuid.isNullOrBlank()) addProperty("playerUuid", playerUuid)
            if (!server.isNullOrBlank()) addProperty("server", server)
            addProperty("ts", System.currentTimeMillis())
        }

        CompletableFuture.runAsync({
            val json = gson.toJson(body)
            var sent = false
            for (baseUrl in baseUrls) {
                try {
                    val req = HttpRequest.newBuilder()
                        .uri(URI.create("$baseUrl/api/ingest"))
                        .timeout(Duration.ofSeconds(5))
                        .header("content-type", "application/json")
                        .header("x-player-key", playerKey)
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build()
                    val resp = http.send(req, HttpResponse.BodyHandlers.discarding())
                    if (resp.statusCode() in 200..299) {
                        sent = true
                        break
                    }
                } catch (_: Throwable) {
                }
            }
            if (!sent) {
                NayoGuildBridge.logger.debug("[NayoGuildBridge] Remote ingest failed on all domains")
            }
        }, executor)
    }

    fun tickPoll(client: Minecraft) {
        if (!isRemoteBridgeActive()) return
        val cfg = NgbConfig.config
        val baseUrls = baseUrls()
        val playerKey = playerKey()
        if (baseUrls.isEmpty()) return

        val now = System.currentTimeMillis()
        val interval = cfg.remoteBridgePollMs.coerceIn(250, 10000)
        if (now - lastPollAtMs < interval) return
        lastPollAtMs = now

        val channel = cfg.remoteBridgeChannelId.ifEmpty { "default" }
        val channelEnc = URLEncoder.encode(channel, StandardCharsets.UTF_8)
        CompletableFuture.supplyAsync({
            for (baseUrl in baseUrls) {
                try {
                    val req = HttpRequest.newBuilder()
                        .uri(URI.create("$baseUrl/api/poll?channelId=$channelEnc&limit=20"))
                        .timeout(Duration.ofSeconds(8))
                        .header("x-player-key", playerKey)
                        .GET()
                        .build()
                    val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
                    if (resp.statusCode() in 200..299) {
                        return@supplyAsync resp.body()
                    }
                } catch (_: Throwable) {
                }
            }
            null
        }, executor).thenAccept { body ->
            if (body.isNullOrBlank()) return@thenAccept
            try {
                val root = gson.fromJson(body, JsonObject::class.java) ?: return@thenAccept
                val items = root.getAsJsonArray("items") ?: return@thenAccept
                if (items.size() <= 0) return@thenAccept

                client.execute {
                    for (el in items) {
                        if (!el.isJsonObject) continue
                        val obj = el.asJsonObject
                        val text = obj.get("text")?.asString ?: continue
                        val mode = obj.get("mode")?.asString ?: "chat"
                        when (mode) {
                            "suggest" -> client.player?.displayClientMessage(
                                Component.literal("§b◇ §f$text"),
                                false
                            )
                            else -> client.player?.displayClientMessage(
                                Component.literal("§7◇ §f$text"),
                                false
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                NayoGuildBridge.logger.debug("[NayoGuildBridge] Remote poll parse failed: ${t.message}")
            }
        }
    }
}

