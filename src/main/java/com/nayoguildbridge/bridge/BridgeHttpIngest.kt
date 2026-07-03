package com.nayoguildbridge.bridge

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nayoguildbridge.NayoGuildBridge
import com.nayoguildbridge.config.NgbConfig
import com.nayoguildbridge.util.BridgeOutboundFilter
import com.nayoguildbridge.util.ItemStackJson
import com.nayoguildbridge.util.PlayerIdentity
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

object BridgeHttpIngest {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ngb-ingest").apply { isDaemon = true }
    }

    fun enqueueGuildLine(rawGuildLine: String) {
        val parsed = BridgeOutboundFilter.parseGuildLine(rawGuildLine) ?: return
        enqueueGuild(parsed.first, parsed.second)
    }

    fun enqueueGuild(author: String, text: String) {
        if (author.isBlank() || text.isBlank()) return
        postIngest(author, text, kind = "guild", combinedBridge = false)
    }

    fun enqueueCombined(author: String, text: String) {
        if (author.isBlank() || text.isBlank()) return
        postIngest(author, text, kind = "combined", combinedBridge = true)
    }

    fun enqueueShowItem(author: String, stack: ItemStack) {
        if (author.isBlank() || stack.isEmpty) return
        val qty = if (stack.count > 1) " x${stack.count}" else ""
        val display = "is holding [${stack.hoverName.string}$qty]"
        val jsonStack = ItemStackJson.toJsonStack(stack)
        postIngest(
            author = author,
            text = display,
            kind = "show",
            combinedBridge = NgbConfig.config.imsCombinedBridgeEnabled,
            show = true,
            jsonStack = jsonStack
        )
    }

    private fun postIngest(
        author: String,
        text: String,
        kind: String,
        combinedBridge: Boolean,
        show: Boolean = false,
        jsonStack: String? = null
    ) {
        val cfg = NgbConfig.config
        val client = Minecraft.getInstance()
        val playerKey = PlayerIdentity.playerName(client).ifBlank { author }
        val instanceId = PlayerIdentity.instanceId(client)
        val originInstance = PlayerIdentity.mcInstanceName(client)
        val channelId = cfg.bridgePollChannelId.ifEmpty { "default" }

        val body = JsonObject().apply {
            addProperty("channelId", channelId)
            addProperty("kind", kind)
            addProperty("author", author)
            addProperty("text", text)
            addProperty("source", "minecraft")
            addProperty("instanceId", instanceId)
            addProperty("originInstance", originInstance)
            addProperty("ts", System.currentTimeMillis())
            if (combinedBridge) addProperty("combinedBridge", true)
            if (show) addProperty("show", true)
            jsonStack?.let { add("jsonStack", JsonParser.parseString(it)) }
        }

        CompletableFuture.runAsync({
            for (base in BridgeRouter.httpApiBases()) {
                if (base.isBlank()) continue
                try {
                    val builder = HttpRequest.newBuilder()
                        .uri(URI.create("$base/api/ingest"))
                        .timeout(Duration.ofSeconds(6))
                        .header("content-type", "application/json")
                        .header("x-player-key", playerKey)
                        .header("x-bridge-instance", instanceId)
                    PlayerIdentity.playerUuid(client)?.let {
                        builder.header("x-player-uuid", it)
                    }
                    val req = builder
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build()
                    val resp = http.send(req, HttpResponse.BodyHandlers.discarding())
                    if (resp.statusCode() in 200..299) return@runAsync
                } catch (t: Throwable) {
                    NayoGuildBridge.logger.debug("[NGB] ingest $kind ($base): ${t.message}")
                }
            }
        }, executor)
    }
}
