package com.nayoguildbridge.qol

import com.nayoguildbridge.NayoGuildBridge
import com.nayoguildbridge.config.NgbConfig
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

object UpdateCheck {
    private const val VERSION_URL = "https://api.modrinth.com/v2/project/nayoguildbridge/version"
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build()

    fun checkOnJoin() {
        if (!NgbConfig.config.updateCheckEnabled) return
        CompletableFuture.runAsync {
            try {
                val req = HttpRequest.newBuilder()
                    .uri(URI.create(VERSION_URL))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build()
                val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
                if (resp.statusCode() !in 200..299) return@runAsync
                val body = resp.body()
                val latest = Regex(""""version_number"\s*:\s*"([^"]+)"""")
                    .find(body)?.groupValues?.getOrNull(1) ?: return@runAsync
                val current = FabricLoader.getInstance().getModContainer("nayoguildbridge")
                    .map { it.metadata.version.friendlyString }
                    .orElse("dev")
                if (latest != current && compareSemver(latest, current) > 0) {
                    notify("§e[NGB] §fДоступна новая версия: §a$latest §7(у вас $current)")
                }
            } catch (t: Throwable) {
                NayoGuildBridge.logger.debug("[NGB] update check: {}", t.message)
            }
        }
    }

    private fun compareSemver(a: String, b: String): Int {
        val pa = a.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val da = pa.getOrElse(i) { 0 }
            val db = pb.getOrElse(i) { 0 }
            if (da != db) return da.compareTo(db)
        }
        return 0
    }

    private fun notify(text: String) {
        Minecraft.getInstance().execute {
            Minecraft.getInstance().player?.displayClientMessage(Component.literal(text), false)
        }
    }
}
