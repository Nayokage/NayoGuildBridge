package com.nayoguildbridge.quote

import com.nayoguildbridge.QuoteClickHelper
import com.nayoguildbridge.config.NgbConfig
import com.nayoguildbridge.util.BridgeSourceTags
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

object QuoteDisplay {
    private const val INLINE_PREVIEW_MAX = 48

    private val pipeColor = TextColor.fromRgb(0x5865F2)
    private val quoteMetaColor = TextColor.fromRgb(0xB5BAC1)

    fun buildInlineQuoteReply(
        quotedText: String,
        replyText: String,
        quotedFromUser: String? = null,
        sourceId: String? = null,
        replyBuilder: (String) -> Component
    ): Component {
        val preview = truncate(quotedText)
        val author = quotedFromUser?.trim()?.takeIf { it.isNotEmpty() } ?: "?"
        val normalizedSource = BridgeSourceTags.normalizeSourceId(sourceId ?: "discord")

        val out: MutableComponent = Component.empty()
        out.append(
            Component.literal("| ")
                .withStyle(
                    Style.EMPTY
                        .withColor(pipeColor)
                        .withBold(true)
                )
        )
        out.append(sourceLabelComponent(normalizedSource))
        out.append(
            Component.literal("$author: \"")
                .withStyle(
                    Style.EMPTY
                        .withColor(quoteMetaColor)
                        .withItalic(true)
                )
        )
        out.append(
            Component.literal(preview)
                .withStyle(
                    QuoteClickHelper.quotePreviewStyle(quotedText, preview)
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
            Component.literal("\" ")
                .withStyle(
                    Style.EMPTY
                        .withColor(quoteMetaColor)
                        .withItalic(true)
                )
        )
        out.append(replyBuilder(replyText))
        return out
    }

    private fun sourceLabelComponent(sourceId: String): Component {
        val cfg = NgbConfig.config
        val (label, colorHex) = when (BridgeSourceTags.normalizeSourceId(sourceId)) {
            "telegram" -> BridgeSourceTags.TELEGRAM_DISPLAY to cfg.telegramLabelColor
            "minecraft" -> BridgeSourceTags.MINECRAFT_DISPLAY to cfg.minecraftLabelColor
            else -> BridgeSourceTags.DISCORD_DISPLAY to cfg.discordLabelColor
        }
        return Component.literal(label)
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(configColorHex(colorHex))))
    }

    private fun configColorHex(hex: String): Int = try {
        Integer.decode(hex)
    } catch (_: Throwable) {
        0x55FF55
    }

    private fun truncate(text: String): String {
        val oneLine = text.replace('\n', ' ').trim()
        if (oneLine.length <= INLINE_PREVIEW_MAX) return oneLine
        return oneLine.take(INLINE_PREVIEW_MAX - 1) + "…"
    }
}
