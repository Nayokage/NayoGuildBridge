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
    private val seenTextAt = ConcurrentHashMap<String, Long>()
    private const val SEEN_CAP = 200
    private const val TEXT_DEDUPE_MS = 86_400_000L

    @Volatile
    private var lastPollAtMs = 0L

    @Volatile
    private var lastProcessedPollTs = 0L

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
                        val text = obj.get("text")?.asString ?: continue

                        val ts = run {
                            val elTs = obj.get("ts") ?: return@run 0L
                            if (!elTs.isJsonPrimitive || !elTs.asJsonPrimitive.isNumber) 0L
                            else elTs.asLong
                        }
                        if (ts > 0 && ts <= lastProcessedPollTs) continue

                        val textKey = text.trim().lowercase().replace(Regex("\\s+"), " ")
                        if (textKey.isNotBlank()) {
                            val now = System.currentTimeMillis()
                            val prev = seenTextAt[textKey]
                            if (prev != null && now - prev < TEXT_DEDUPE_MS) continue
                        }

                        val id = obj.get("id")?.asString
                        if (!id.isNullOrBlank()) {
                            if (!seenIds.add(id)) continue
                            trimSeen()
                        } else {
                            val dedupeKey = "${obj.get("ts")?.asString}:$text"
                            if (!seenIds.add(dedupeKey)) continue
                            trimSeen()
                        }

                        val mode = obj.get("mode")?.asString ?: "chat"
                        val fromInstance = obj.get("fromInstanceId")?.asString?.trim()?.ifBlank { null }
                        val incoming = IncomingBridgeFormatter.fromPollText(text, mode, fromInstance) ?: continue
                        if (!cfg.bridgePollDisplayInChat) continue
                        if (!BridgeChatDedupe.claimDisplay(incoming.username, incoming.body)) continue

                        if (textKey.isNotBlank()) {
                            seenTextAt[textKey] = System.currentTimeMillis()
                            trimTextSeen()
                        }
                        if (ts > lastProcessedPollTs) lastProcessedPollTs = ts

                        val formatted = IncomingBridgeFormatter.format(incoming, tag, color)
                        client.player?.sendSystemMessage(formatted)
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

    private fun trimTextSeen() {
        if (seenTextAt.size <= SEEN_CAP) return
        val now = System.currentTimeMillis()
        seenTextAt.entries.removeIf { now - it.value > TEXT_DEDUPE_MS }
    }
}
