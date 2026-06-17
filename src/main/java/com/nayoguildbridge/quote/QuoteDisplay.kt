package com.nayoguildbridge.quote

import com.nayoguildbridge.QuoteClickHelper
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

object QuoteDisplay {
    private const val INLINE_PREVIEW_MAX = 42

    fun buildInlineQuoteReply(
        quotedText: String,
        replyText: String,
        replyBuilder: (String) -> Component
    ): Component {
        val preview = truncate(quotedText)
        val hover = Component.literal(quotedText)
        val quoteStyle = QuoteClickHelper.quotePreviewStyle(quotedText, preview)

        val out: MutableComponent = Component.empty()
        out.append(
            Component.literal("❝")
                .withStyle(
                    quoteStyle.withHoverEvent(
                        HoverEvent.ShowText(
                            Component.literal("§7Цитата:\n§f$quotedText")
                        )
                    )
                )
        )
        out.append(
            Component.literal(" \"$preview\" ")
                .withStyle(quoteStyle)
        )
        out.append(replyBuilder(replyText))
        return out
    }

    private fun truncate(text: String): String {
        val oneLine = text.replace('\n', ' ').trim()
        if (oneLine.length <= INLINE_PREVIEW_MAX) return oneLine
        return oneLine.take(INLINE_PREVIEW_MAX - 1) + "…"
    }
}
