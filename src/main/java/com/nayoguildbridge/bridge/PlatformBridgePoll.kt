package com.nayoguildbridge.bridge

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nayoguildbridge.NayoGuildBridge
import com.nayoguildbridge.config.NgbConfig
import com.nayoguildbridge.ims.ImsBridgeClient
import com.nayoguildbridge.util.PlayerIdentity
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors

object PlatformBridgePoll {
    private val gson = Gson()
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val executor: Executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ngb-platform-poll").apply { isDaemon = true }
    }

    private val seenIds = ConcurrentHashMap.newKeySet<String>()
    private const val SEEN_CAP = 200

    @Volatile
    private var lastPollAtMs = 0L

    fun isActive(): Boolean {
        if (!com.nayoguildbridge.guard.EnvironmentGuard.isOperational()) return false
        val cfg = NgbConfig.config
        if (!cfg.bridgePollEnabled || !cfg.bridgeEnabled) return false
        if (cfg.bridgePollOnlyWhenWsOffline && ImsBridgeClient.isConnected()) return false
        return BridgeRouter.httpApiBase().isNotBlank()
    }

    fun tick(client: Minecraft) {
        if (!isActive()) return
        val cfg = NgbConfig.config
        val now = System.currentTimeMillis()
        val interval = cfg.bridgePollMs.coerceIn(500, 10000)
        if (now - lastPollAtMs < interval) return
        lastPollAtMs = now

        val bases = BridgeRouter.httpApiBases()
        val channel = cfg.bridgePollChannelId.ifEmpty { "default" }
        val playerKey = client.player?.name?.string?.trim().orEmpty().ifBlank { "bridge-client" }
        val instanceId = PlayerIdentity.instanceId(client)
        val channelEnc = URLEncoder.encode(channel, StandardCharsets.UTF_8)
        val instanceEnc = URLEncoder.encode(instanceId, StandardCharsets.UTF_8)

        CompletableFuture.supplyAsync({
            for (base in bases) {
                if (base.isBlank()) continue
                try {
                    val builder = HttpRequest.newBuilder()
                        .uri(URI.create("$base/api/poll?channelId=$channelEnc&limit=20&instanceId=$instanceEnc"))
                        .timeout(Duration.ofSeconds(8))
                        .header("x-player-key", playerKey)
                        .header("x-bridge-instance", instanceId)
                    PlayerIdentity.playerUuid(client)?.let {
                        builder.header("x-player-uuid", it)
                    }
                    val req = builder.GET().build()
                    val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
                    if (resp.statusCode() in 200..299) return@supplyAsync resp.body()
                } catch (t: Throwable) {
                    NayoGuildBridge.logger.debug("[NayoGuildBridge] poll failed ($base): ${t.message}")
                }
            }
            null
        }, executor).thenAccept { body ->
            if (body.isNullOrBlank()) return@thenAccept
            try {
                val root = gson.fromJson(body, JsonObject::class.java) ?: return@thenAccept
                val items = root.getAsJsonArray("items") ?: return@thenAccept
                if (items.size() == 0) return@thenAccept

                val tag = ImsBridgeClient.guildDisplayName().ifBlank { cfg.imsGuildTag }
                val color = cfg.imsGuildColor

                client.execute {
                    for (el in items) {
                        if (!el.isJsonObject) continue
                        val obj = el.asJsonObject
                        val id = obj.get("id")?.asString
                        if (!id.isNullOrBlank()) {
                            if (!seenIds.add(id)) continue
                            trimSeen()
                        } else {
                            val text = obj.get("text")?.asString ?: continue
                            val dedupeKey = "${obj.get("ts")?.asString}:$text"
                            if (!seenIds.add(dedupeKey)) continue
                            trimSeen()
                        }
                        val text = obj.get("text")?.asString ?: continue
                        val mode = obj.get("mode")?.asString ?: "chat"
                        val incoming = IncomingBridgeFormatter.fromPollText(text, mode) ?: continue
                        if (!cfg.bridgePollDisplayInChat) continue
                        BridgeChatDedupe.remember(
                            BridgeChatDedupe.keyFor(incoming.username, incoming.body)
                        )
                        val formatted = IncomingBridgeFormatter.format(incoming, tag, color)
                        client.player?.displayClientMessage(formatted, false)
                    }
                }
            } catch (t: Throwable) {
                NayoGuildBridge.logger.debug("[NayoGuildBridge] poll parse: ${t.message}")
            }
        }
    }

    private fun trimSeen() {
        if (seenIds.size > SEEN_CAP) {
            val drop = seenIds.take(seenIds.size - SEEN_CAP)
            drop.forEach { seenIds.remove(it) }
        }
    }
}
