package com.nayoguildbridge.bridge

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nayoguildbridge.config.BridgeEndpoints
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

object BridgeHealthCache {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build()
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ngb-health").apply { isDaemon = true }
    }

    @Volatile private var relayMode: String = "?"
    @Volatile private var lastFetchMs = 0L
    @Volatile private var lastError: String = ""

    private const val TTL_MS = 60_000L

    fun statusSuffix(): String {
        maybeRefresh()
        val mode = relayMode
        return when {
            mode == "br1dgebtw" -> "§arelay=br1dgebtw"
            mode == "platform-direct" -> "§erelay=platform"
            mode == "?" && lastError.isNotBlank() -> "§7relay=? ($lastError)"
            else -> "§7relay=?"
        }
    }

    private fun maybeRefresh() {
        val now = System.currentTimeMillis()
        if (now - lastFetchMs < TTL_MS) return
        lastFetchMs = now

        CompletableFuture.runAsync({
            for (base in listOf(BridgeEndpoints.PRIMARY_HTTP) + BridgeEndpoints.httpBases().distinct()) {
                if (base.isBlank()) continue
                try {
                    val req = HttpRequest.newBuilder()
                        .uri(URI.create("$base/api/health"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build()
                    val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
                    if (resp.statusCode() !in 200..299) continue
                    val root = JsonParser.parseString(resp.body()).asJsonObject
                    relayMode = root.get("relayMode")?.asString?.trim().orEmpty().ifBlank { "?" }
                    lastError = ""
                    return@runAsync
                } catch (t: Throwable) {
                    lastError = t.message ?: "err"
                }
            }
        }, executor)
    }
}
