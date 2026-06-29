package com.nayoguildbridge.quote

import com.nayoguildbridge.QuoteClickHelper
import com.nayoguildbridge.util.BridgeSourceTags
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

object QuoteDisplay {
    private const val INLINE_PREVIEW_MAX = 48

    private val barColor = TextColor.fromRgb(0x5865F2)
    private val quoteMetaColor = TextColor.fromRgb(0x949BA4)
    private val quoteTextColor = TextColor.fromRgb(0xB5BAC1)

    fun buildInlineQuoteReply(
        quotedText: String,
        replyText: String,
        quotedFromUser: String? = null,
        sourceId: String? = null,
        replyBuilder: (String) -> Component
    ): Component {
        val preview = truncate(quotedText)
        val sourceLabel = sourceLabelFor(sourceId)
        val author = quotedFromUser?.trim()?.takeIf { it.isNotEmpty() }

        val metaLine = if (author != null) "$sourceLabel $author" else sourceLabel

        val out: MutableComponent = Component.empty()
        out.append(
            Component.literal("▎ ")
                .withStyle(
                    Style.EMPTY
                        .withColor(barColor)
                        .withBold(true)
                )
        )
        out.append(
            Component.literal(metaLine)
                .withStyle(
                    Style.EMPTY
                        .withColor(quoteMetaColor)
                        .withItalic(true)
                        .withHoverEvent(
                            HoverEvent.ShowText(
                                Component.literal("§7Цитата:\n§f$quotedText")
                            )
                        )
                )
        )
        out.append(
            Component.literal(": \"$preview\"")
                .withStyle(
                    QuoteClickHelper.quotePreviewStyle(quotedText, preview)
                        .withColor(quoteTextColor)
                )
        )
        out.append(Component.literal(" "))
        out.append(replyBuilder(replyText))
        return out
    }

    private fun sourceLabelFor(sourceId: String?): String {
        return when (BridgeSourceTags.normalizeSourceId(sourceId ?: "discord")) {
            "telegram" -> BridgeSourceTags.TELEGRAM_DISPLAY.trim()
            "minecraft" -> BridgeSourceTags.MINECRAFT_DISPLAY.trim()
            else -> BridgeSourceTags.DISCORD_DISPLAY.trim()
        }
    }

    private fun truncate(text: String): String {
        val oneLine = text.replace('\n', ' ').trim()
        if (oneLine.length <= INLINE_PREVIEW_MAX) return oneLine
        return oneLine.take(INLINE_PREVIEW_MAX - 1) + "…"
    }
}
