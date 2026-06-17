package com.nayoguildbridge.platform

import com.google.gson.JsonObject
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

object BridgeChatFormatter {
    fun formatIncoming(payload: JsonObject): Component {
        val author = payload.get("authorName")?.asString ?: "?"
        val body = payload.get("body")?.asString ?: ""
        val quoted = payload.get("quoted")?.asBoolean == true
        val quotedText = payload.get("quotedText")?.asString?.trim().orEmpty()
        val quotedFrom = payload.get("quotedFromUser")?.asString?.trim().orEmpty()

        val out: MutableComponent = Component.empty()

        if (quoted && (quotedText.isNotEmpty() || quotedFrom.isNotEmpty())) {
            val target = quotedFrom.ifEmpty { "?" }
            val preview = if (quotedText.length > 48) quotedText.take(45) + "…" else quotedText
            out.append(
                Component.literal("[Reply to $target]: ")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x8A8D93)).withItalic(true))
            )
            if (preview.isNotEmpty()) {
                out.append(
                    Component.literal("$preview ")
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA)).withItalic(true))
                )
            }
        }

        out.append(
            Component.literal("$author: ")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x8F99FF)))
        )
        out.append(
            Component.literal(body)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xC1C3C7)))
        )
        return out
    }
}
