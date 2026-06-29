package com.nayoguildbridge

import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

object QuoteClickHelper {
    fun quoteActionButton(senderNick: String, sourceId: String = "discord", quotedText: String? = null): Component {
        if (!com.nayoguildbridge.config.NgbConfig.config.quoteSystemEnabled) return Component.empty()
        val cleanNick = senderNick.replace(":", "").trim()
        if (cleanNick.isBlank()) return Component.empty()
        return Component.literal(" [q]")
            .withStyle(quoteButtonStyle(quotePrefill(cleanNick, sourceId, quotedText)))
    }

    fun quotePrefill(senderNick: String, sourceId: String = "discord", quotedText: String? = null): String {
        val cleanNick = senderNick.replace(":", "").trim()
        val sourceLabel = when (sourceId.lowercase()) {
            "telegram" -> "Telegram"
            "minecraft" -> "Minecraft"
            else -> "Discord"
        }
        val quoteBody = quotedText
            ?.replace(Regex("""\s*\[q]\s*$""", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
        return if (quoteBody.isBlank()) {
            "> [$sourceLabel] $cleanNick: | "
        } else {
            "> [$sourceLabel] $cleanNick: $quoteBody | "
        }
    }

    fun quoteButtonStyle(prefill: String): Style {
        return Style.EMPTY
            .withClickEvent(ClickEvent.SuggestCommand(prefill))
            .withHoverEvent(
                HoverEvent.ShowText(
                    Component.literal("§7Нажми — вставится шаблон\n§7цитата §f|§7 ответ")
                )
            )
    }

    fun quotePreviewStyle(fullQuotedText: String, shortLabel: String): Style {
        return Style.EMPTY
            .withHoverEvent(HoverEvent.ShowText(Component.literal(fullQuotedText)))
            .withColor(TextColor.fromRgb(0x8A8D93))
            .withItalic(true)
    }
}
