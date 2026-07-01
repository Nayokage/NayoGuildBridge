package com.nayoguildbridge.quote

import com.nayoguildbridge.NayoGuildBridge
import com.nayoguildbridge.util.BridgeTextUtil
import com.nayoguildbridge.util.PlayerIdentity
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture

object ApiClient {
    private const val SOURCE_MOD = "nayoguildbridge-fabric"
    private const val QUOTE_DENIED_DEFAULT = "§cФункция цитирования у вас не доступна"
    private val gson = Gson()
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(4))
        .build()

    fun sendQuotedMessage(quote: QuoteDetector.Result, urlsOverride: List<String>? = null) {
        val urls = urlsOverride?.filter { it.isNotBlank() }?.distinct().takeUnless { it.isNullOrEmpty() }
            ?: ConfigManager.apiUrls()
        if (urls.isEmpty()) return

        val client = Minecraft.getInstance()
        val player = client.player ?: return

        val messageId = UUID.randomUUID().toString()
        val quotedMessageEffective =
            quote.quotedMessage?.let { BridgeTextUtil.stripBridgeFormatting(it) }?.takeIf { it.isNotEmpty() } ?: "—"
        var quotedFromUserEffective =
            quote.quotedFromUser?.trim()?.takeIf { it.isNotEmpty() }
        val quotedFromInstanceEffective =
            BridgeTextUtil.normalizeSourceTag(quote.quotedFromInstance)
        val replyBody = BridgeTextUtil.stripBridgeFormatting(quote.body).trim()
        if (replyBody.isEmpty()) return
        if (quotedFromUserEffective == null && quotedFromInstanceEffective == null) {
            quotedFromUserEffective = player.name.string
        }

        val bodyJson = JsonObject().apply {
            addProperty("id", messageId)
            addProperty("message_id", messageId)
            addProperty("source", "minecraft")
            addProperty("source_mod", SOURCE_MOD)
            addProperty("originInstance", PlayerIdentity.originInstance(client))
            addProperty("username", player.name.string)
            addProperty("message", replyBody)
            addProperty("body", replyBody)
            addProperty("text", replyBody)
            addProperty("msg", replyBody)
            addProperty("quoted", true)
            addProperty("timestamp", System.currentTimeMillis())
            addProperty("quotedMessage", quotedMessageEffective)
            addProperty("quotedText", quotedMessageEffective)
            PlayerIdentity.playerUuid(client)?.let { addProperty("minecraftUuid", it) }
            if (quotedFromInstanceEffective != null) {
                addProperty("quotedFromInstance", quotedFromInstanceEffective)
                addProperty("quotedSource", quotedFromInstanceEffective)
            }
            if (quotedFromUserEffective != null) {
                addProperty("quotedFromUser", quotedFromUserEffective)
                addProperty("replyToUser", quotedFromUserEffective)
            }
            if (!quote.replyToMessageId.isNullOrBlank()) {
                addProperty("reply_to_message_id", quote.replyToMessageId)
                addProperty("replyToMessageId", quote.replyToMessageId)
            }
        }

        CompletableFuture.runAsync {
            val body = gson.toJson(bodyJson)
            var handled = false
            for (url in urls) {
                try {
                    val builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .header("content-type", "application/json")
                        .header("x-player-key", player.name.string)
                    PlayerIdentity.playerUuid(client)?.let {
                        builder.header("x-player-uuid", it)
                    }
                    val req = builder
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build()
                    val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
                    if (resp.statusCode() in 200..299) {
                        showInGame("§a[NayoGuildBridge] §fЦитата отправлена в Discord/Telegram.")
                        handled = true
                        break
                    }
                    val apiMessage = parseApiMessage(resp.body())
                    when (resp.statusCode()) {
                        403 -> {
                            showInGame(
                                apiMessage?.let { "§c$it" }
                                    ?: QUOTE_DENIED_DEFAULT
                            )
                            handled = true
                            break
                        }
                        429 -> {
                            showInGame(
                                apiMessage?.let { "§c$it" }
                                    ?: "§cСлишком много цитат. Подождите несколько минут."
                            )
                            handled = true
                            break
                        }
                        else -> {
                            NayoGuildBridge.logger.warn(
                                "[NayoGuildBridge][QuoteOut] HTTP {} url={}",
                                resp.statusCode(),
                                url
                            )
                        }
                    }
                } catch (t: Throwable) {
                    NayoGuildBridge.logger.warn(
                        "[NayoGuildBridge][QuoteOut] error url={} err={}",
                        url,
                        t.message ?: t.javaClass.simpleName
                    )
                }
            }
            if (!handled) {
                NayoGuildBridge.logger.error("[NayoGuildBridge][QuoteOut] failed message_id={}", messageId)
            }
        }
    }

    private fun parseApiMessage(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val root = JsonParser.parseString(raw).asJsonObject
            root.get("message")?.asString?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun showInGame(text: String) {
        val mc = Minecraft.getInstance()
        mc.execute {
            mc.player?.sendSystemMessage(Component.literal(text))
        }
    }
}
