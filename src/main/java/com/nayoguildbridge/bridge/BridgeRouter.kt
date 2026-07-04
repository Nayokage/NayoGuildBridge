package com.nayoguildbridge.bridge

import com.nayoguildbridge.config.BridgeEndpoints
import com.nayoguildbridge.config.NgbConfig
import com.nayoguildbridge.ims.ImsBridgeClient
import com.nayoguildbridge.quote.ApiClient
import com.nayoguildbridge.quote.ConfigManager
import com.nayoguildbridge.quote.QuoteContextRegistry
import com.nayoguildbridge.quote.QuoteDetector
import com.nayoguildbridge.util.BridgeOutboundFilter
import net.minecraft.client.Minecraft

object BridgeRouter {
    fun isActive(): Boolean = ImsBridgeClient.isActive()

    fun isConnected(): Boolean = ImsBridgeClient.isConnected()

    fun wsUrl(): String = BridgeEndpoints.PRIMARY_WS

    fun httpApiBase(): String = BridgeEndpoints.httpBases().first()

    fun httpApiBases(): List<String> = BridgeEndpoints.httpBases()

    fun sendQuote(quote: QuoteDetector.Result) {
        if (!ConfigManager.quoteSystemEnabled()) return
        val enriched = QuoteContextRegistry.enrich(quote, ImsBridgeClient.localGuildId().ifBlank { null })
        if (ImsBridgeClient.isConnected()) {
            ImsBridgeClient.sendQuote(enriched)
            return
        }
        ApiClient.sendQuotedMessage(enriched, ConfigManager.apiUrls())
    }

    fun canSendCombined(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return player.name.string.isNotBlank()
    }

    fun sendCombined(text: String) {
        if (ImsBridgeClient.isConnected()) {
            ImsBridgeClient.sendCombinedMessage(text)
            return
        }
        val player = Minecraft.getInstance().player?.name?.string?.trim().orEmpty()
        if (player.isNotBlank()) {
            BridgeHttpIngest.enqueueCombined(player, text)
        }
    }

    fun sendBridgeChat(text: String) {
        if (ImsBridgeClient.isConnected()) {
            ImsBridgeClient.sendGuildMessage(text)
        } else {
            BridgeHttpIngest.enqueueGuildLine(toGuildLineForIngest(text))
        }
    }

    fun sendWebOnlyRelay(text: String) {
        if (ImsBridgeClient.isConnected()) {
            ImsBridgeClient.sendWebOnlyRelay(text)
        } else {
            BridgeHttpIngest.enqueueGuildLine(toGuildLineForIngest(text))
        }
    }

    fun sendShowItem(stack: net.minecraft.world.item.ItemStack) {
        if (ImsBridgeClient.isConnected()) {
            ImsBridgeClient.sendShowItem(stack)
            return
        }
        val player = Minecraft.getInstance().player?.name?.string?.trim().orEmpty()
        if (player.isNotBlank()) {
            BridgeHttpIngest.enqueueShowItem(player, stack)
        }
    }

    private fun toGuildLineForIngest(text: String): String {
        if (BridgeOutboundFilter.parseGuildLine(text) != null) return text
        val player = Minecraft.getInstance().player?.name?.string?.trim().orEmpty()
        if (player.isBlank()) return text
        return "Guild > $player: $text"
    }
}
