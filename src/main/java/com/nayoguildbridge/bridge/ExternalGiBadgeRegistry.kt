package com.nayoguildbridge.bridge

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.nayoguildbridge.NayoGuildBridge
import com.nayoguildbridge.config.BridgeEndpoints
import com.nayoguildbridge.util.PlayerIdentity
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

data class ExternalGiBadge(
    val badge: String,
    val guildLabel: String,
    val note: String? = null
)

object ExternalGiBadgeRegistry {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build()
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ngb-ext-gi").apply { isDaemon = true }
    }

    private val badges = ConcurrentHashMap<String, ExternalGiBadge>()
    @Volatile private var lastFetchMs = 0L

    private const val TTL_MS = 60_000L

    fun badgeFor(nick: String): ExternalGiBadge? {
        maybeRefresh()
        if (nick.isBlank()) return null
        return badges[nick.trim().lowercase()]
    }

    fun appendBadgePrefix(nick: String, base: Component): Component {
        val entry = badgeFor(nick) ?: return base
        val hover = entry.note?.takeIf { it.isNotBlank() }
            ?: "Внешняя гильдия: ${entry.guildLabel}"
        return Component.empty()
            .append(
                Component.literal("${entry.badge} ")
                    .withStyle(
                        Style.EMPTY
                            .withColor(TextColor.fromRgb(0xFFAA00))
                    )
            )
            .append(base)
    }

    private fun maybeRefresh() {
        val now = System.currentTimeMillis()
        if (now - lastFetchMs < TTL_MS) return
        lastFetchMs = now

        executor.execute {
            val client = Minecraft.getInstance()
            val playerKey = client.player?.name?.string?.trim().orEmpty().ifBlank { "bridge-client" }
            val instanceId = PlayerIdentity.instanceId(client)

            for (base in BridgeEndpoints.httpBases()) {
                if (base.isBlank()) continue
                try {
                    val builder = HttpRequest.newBuilder()
                        .uri(URI.create("$base/api/external-gi-members"))
                        .timeout(Duration.ofSeconds(6))
                        .header("x-player-key", playerKey)
                        .header("x-bridge-instance", instanceId)
                    PlayerIdentity.playerUuid(client)?.let {
                        builder.header("x-player-uuid", it)
                    }
                    val resp = http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
                    if (resp.statusCode() !in 200..299) continue

                    val root = JsonParser.parseString(resp.body()).asJsonObject
                    val arr: JsonArray = root.getAsJsonArray("members") ?: continue
                    val next = ConcurrentHashMap<String, ExternalGiBadge>()
                    for (el in arr) {
                        if (!el.isJsonObject) continue
                        val obj = el.asJsonObject
                        val name = obj.get("name")?.asString?.trim().orEmpty()
                        if (name.isBlank()) continue
                        val badge = obj.get("badge")?.asString?.trim().orEmpty().ifBlank { "⬡" }
                        val guildLabel = obj.get("guildLabel")?.asString?.trim().orEmpty().ifBlank { "Ext GI" }
                        val note = obj.get("note")?.asString?.trim()?.takeIf { it.isNotEmpty() }
                        next[name.lowercase()] = ExternalGiBadge(badge, guildLabel, note)
                    }
                    badges.clear()
                    badges.putAll(next)
                    return@execute
                } catch (t: Throwable) {
                    NayoGuildBridge.logger.debug("[NGB] external-gi fetch: ${t.message}")
                }
            }
        }
    }
}
