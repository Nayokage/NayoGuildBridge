package com.nayoguildbridge.bridge

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nayoguildbridge.QuoteClickHelper
import com.nayoguildbridge.config.NgbConfig
import com.nayoguildbridge.util.ItemStackJson
import com.nayoguildbridge.qol.ChatQoL
import com.nayoguildbridge.quote.QuoteDetector
import com.nayoguildbridge.quote.QuoteDisplay
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.world.item.ItemStack

object IncomingBridgeFormatter {
    data class IncomingMessage(
        val source: String,
        val username: String,
        val body: String,
        val combined: Boolean = false,
        val quoted: Boolean = false,
        val quotedText: String? = null,
        val quotedFromUser: String? = null,
        val imageUrls: List<String> = emptyList(),
        val isCommand: Boolean = false,
        val hasJsonStack: Boolean = false,
        val jsonStackRaw: String? = null
    )

    fun fromWsJson(root: JsonObject, defaultTag: String): IncomingMessage? {
        val msg = root.get("msg")?.asString ?: return null
        val fromField = root.get("from")?.asString ?: return null
        val combined = root.get("combinedbridge")?.asString == "true"
        val hasStack = root.has("jsonStack") && !root.get("jsonStack").isJsonNull
        val stackRaw = if (hasStack) root.get("jsonStack").toString() else null

        val parsed = parseSourceLine(msg.trim())
        val (source, username, body) = if (parsed != null) {
            parsed
        } else {
            val parts = msg.split(": ", limit = 2)
            Triple(
                normalizeSourceId(fromField),
                parts.getOrNull(0)?.trim()?.ifBlank { "?" } ?: "?",
                parts.getOrNull(1)?.trim()?.ifBlank { msg } ?: msg
            )
        }

        val quoted = root.get("quoted")?.asString == "true" || root.get("quoted")?.asBoolean == true
        val quotedText = root.get("quotedText")?.asString
        val quotedFromUser = root.get("quotedFromUser")?.asString
        val isCommand = root.get("isCommand")?.asString == "true" || root.get("isCommand")?.asBoolean == true

        val images = mutableListOf<String>()
        if (root.has("imageUrls") && root.get("imageUrls").isJsonArray) {
            for (el in root.getAsJsonArray("imageUrls")) {
                val url = el.asString?.trim().orEmpty()
                if (url.isNotBlank()) images.add(url)
            }
        }
        ChatQoL.extractImageUrls(body).forEach { if (!images.contains(it)) images.add(it) }

        return IncomingMessage(
            source = source,
            username = username,
            body = body,
            combined = combined,
            quoted = quoted,
            quotedText = quotedText,
            quotedFromUser = quotedFromUser,
            imageUrls = images,
            isCommand = isCommand,
            hasJsonStack = hasStack,
            jsonStackRaw = stackRaw
        )
    }

    fun fromPollText(text: String, mode: String): IncomingMessage? {
        val raw = text.trim()
        if (raw.isBlank()) return null

        if (mode == "suggest" || mode == "command") {
            return IncomingMessage(
                source = "command",
                username = "Bridge",
                body = raw,
                isCommand = true
            )
        }

        if (raw.startsWith("[QUOTE]", ignoreCase = true)) {
            return parseQuoteBlock(raw)
        }

        val quoteInline = Regex("""^>\s*\[([^\]]+)]\s+([^:]+):\s*(.+?)\s+(\[[^\]]+]\s+[^:]+:.*)$""").find(raw)
        if (quoteInline != null) {
            val source = quoteInline.groupValues[1].trim()
            val quotedUser = quoteInline.groupValues[2].trim()
            val quotedBody = quoteInline.groupValues[3].trim()
            val replyPart = quoteInline.groupValues[4].trim()
            val replyParsed = parseSourceLine(replyPart) ?: return null
            return IncomingMessage(
                source = replyParsed.first,
                username = replyParsed.second,
                body = replyParsed.third,
                quoted = true,
                quotedText = quotedBody,
                quotedFromUser = quotedUser,
                imageUrls = ChatQoL.extractImageUrls(replyParsed.third)
            )
        }

        val parsed = parseSourceLine(raw) ?: return IncomingMessage(
            source = "minecraft",
            username = "?",
            body = raw,
            imageUrls = ChatQoL.extractImageUrls(raw)
        )

        val (source, user, body) = parsed
        val quotePair = QuoteDetector.parseIncomingQuoteBody(body)
        return IncomingMessage(
            source = source,
            username = user,
            body = quotePair?.second ?: body,
            quoted = quotePair != null,
            quotedText = quotePair?.first,
            quotedFromUser = quotePair?.let { extractAuthorFromQuoteLine(it.first) } ?: user,
            imageUrls = ChatQoL.extractImageUrls(body)
        )
    }

    fun format(msg: IncomingMessage, defaultTag: String, defaultColor: String): Component {
        if (msg.isCommand || msg.source == "command") {
            return formatCommand(msg.body)
        }
        if (isExternalSource(msg.source)) {
            return formatGuildRelay(msg)
        }
        return when {
            msg.combined -> formatCombined(msg, defaultTag, defaultColor)
            else -> formatImsPeer(msg, defaultTag, defaultColor)
        }
    }

    fun formatLocalOutgoing(username: String, body: String, combined: Boolean): Component {
        return format(
            IncomingMessage(
                source = "minecraft",
                username = username,
                body = body,
                combined = combined
            ),
            NgbConfig.config.imsGuildTag,
            NgbConfig.config.imsGuildColor
        )
    }

    private fun isExternalSource(source: String): Boolean {
        return when (source.lowercase()) {
            "discord", "ds", "dc", "telegram", "tg" -> true
            else -> false
        }
    }

    private fun formatGuildRelay(msg: IncomingMessage): Component {
        val cfg = NgbConfig.config
        val nameColor = configColorHex(cfg.nameColor)
        val msgColorHex = cfg.messageColor
        val msgColorRgb = configColorHex(msgColorHex)

        val bodyText = ChatQoL.applyIncomingBody(stripEmbeddedImageUrls(msg.body, msg.imageUrls))
        val stack = msg.jsonStackRaw?.let { ItemStackJson.fromJsonStack(it) }
        val bodyComponent = if (stack != null) {
            buildShowItemBody(stack, msgColorHex)
        } else {
            buildBody(msg, bodyText, msgColorHex)
        }

        val out = Component.empty()
            .append(sourceLabelComponent(msg.source))
            .append(
                Component.literal(msg.username)
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColor)))
            )
            .append(
                Component.literal(": ")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(msgColorRgb)))
            )
            .append(bodyComponent)

        appendExtras(out, msg, stack, msg.source)
        return out
    }

    private fun formatCombined(msg: IncomingMessage, defaultTag: String, defaultColor: String): Component {
        val cfg = NgbConfig.config

        val tag = when {
            msg.source == "discord" -> "DISC"
            msg.source == "telegram" -> cfg.imsGuildTag.ifBlank { "TG" }
            else -> defaultTag.ifBlank { "BR" }
        }
        val nameColorRgb = when {
            msg.source == "discord" -> 0x5555FF
            msg.source == "telegram" -> 0x55FFFF
            else -> legacyColorToRgb(defaultColor.ifBlank { "§a" })
        }

        val prefix = cfg.imsCombinedPrefix
        val msgColor = cfg.imsCombinedMessageColor

        val header = Component.empty()
            .append(parseLegacyColoredText(prefix))
            .append(sourceLabelComponent(msg.source))
            .append(
                Component.literal(msg.username)
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColorRgb)))
            )
            .append(
                Component.literal(" [$tag]")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColorRgb)))
            )
            .append(
                Component.literal(": ")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF)))
            )

        val bodyText = ChatQoL.applyIncomingBody(stripEmbeddedImageUrls(msg.body, msg.imageUrls))
        val stack = msg.jsonStackRaw?.let { ItemStackJson.fromJsonStack(it) }
        val bodyComponent = if (stack != null) {
            buildShowItemBody(stack, msgColor)
        } else {
            buildBody(msg, bodyText, msgColor)
        }

        val out = Component.empty()
            .append(header)
            .append(bodyComponent)

        appendExtras(out, msg, stack, msg.source)
        return out
    }

    private fun formatImsPeer(msg: IncomingMessage, defaultTag: String, defaultColor: String): Component {
        val cfg = NgbConfig.config
        val tag = defaultTag.ifBlank { "BR" }
        val nameColorRgb = legacyColorToRgb(defaultColor.ifBlank { "§a" })
        val prefix = cfg.imsBridgePrefix
        val msgColor = cfg.imsBridgeMessageColor

        val header = Component.empty()
            .append(parseLegacyColoredText(prefix))
            .append(sourceLabelComponent(msg.source))
            .append(
                Component.literal(msg.username)
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColorRgb)))
            )
            .append(
                Component.literal(" [$tag]")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColorRgb)))
            )
            .append(
                Component.literal(": ")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF)))
            )

        val bodyText = ChatQoL.applyIncomingBody(stripEmbeddedImageUrls(msg.body, msg.imageUrls))
        val stack = msg.jsonStackRaw?.let { ItemStackJson.fromJsonStack(it) }
        val bodyComponent = if (stack != null) {
            buildShowItemBody(stack, msgColor)
        } else {
            buildBody(msg, bodyText, msgColor)
        }

        val out = Component.empty()
            .append(header)
            .append(bodyComponent)

        appendExtras(out, msg, stack, msg.source)
        return out
    }

    private fun appendExtras(
        out: MutableComponent,
        msg: IncomingMessage,
        stack: ItemStack?,
        source: String
    ) {
        if (msg.hasJsonStack && stack == null) {
            out.append(
                Component.literal(" §7[item]").withStyle(
                    Style.EMPTY.withHoverEvent(
                        HoverEvent.ShowText(Component.literal("§7Предмет передан (jsonStack)"))
                    )
                )
            )
        }
        for (url in msg.imageUrls) {
            out.append(ChatQoL.imageLinkComponent(url))
        }
        val sourceId = when (source.lowercase()) {
            "telegram", "tg" -> "telegram"
            "mc", "minecraft" -> "minecraft"
            else -> "discord"
        }
        out.append(QuoteClickHelper.quoteActionButton(msg.username, sourceId))
    }

    private fun configColorHex(hex: String): Int = try {
        Integer.decode(hex)
    } catch (_: Throwable) {
        0xC1C3C7
    }

    private fun normalizeSourceId(raw: String): String = when (raw.lowercase()) {
        "telegram", "tg" -> "telegram"
        "minecraft", "mc" -> "minecraft"
        "discord", "ds", "dc" -> "discord"
        else -> raw
    }

    private fun buildShowItemBody(stack: ItemStack, msgColor: String): Component {
        val rgb = legacyColorToRgb(msgColor)
        val qty = if (stack.count > 1) " x${stack.count}" else ""
        val itemName = stack.hoverName.copy()
            .withStyle(Style.EMPTY.withHoverEvent(HoverEvent.ShowItem(stack)))
        return Component.empty()
            .append(
                Component.literal("is holding §8[")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)))
            )
            .append(itemName.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))))
            .append(
                Component.literal("$qty§8]")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)))
            )
    }

    private fun buildBody(msg: IncomingMessage, bodyText: String, msgColor: String): Component {
        val rgb = legacyColorToRgb(msgColor)

        if (msg.quoted && !msg.quotedText.isNullOrBlank()) {
            val quoted = msg.quotedText!!
            return QuoteDisplay.buildInlineQuoteReply(quoted, bodyText) { reply ->
                ChatQoL.toDisplayComponent(reply).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)))
            }
        }

        val quotePair = QuoteDetector.parseIncomingQuoteBody(bodyText)
        if (quotePair != null) {
            return QuoteDisplay.buildInlineQuoteReply(quotePair.first, quotePair.second) { reply ->
                ChatQoL.toDisplayComponent(reply).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)))
            }
        }

        return ChatQoL.toDisplayComponent(bodyText)
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)))
    }

    private fun formatCommand(body: String): Component {
        return Component.empty()
            .append(
                Component.literal("[CMD] ")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA00)).withBold(true))
            )
            .append(
                Component.literal(body)
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFAA)))
            )
    }

    private fun sourceLabelComponent(source: String): Component {
        val cfg = NgbConfig.config
        val lower = source.lowercase()
        val (label, colorHex) = when {
            lower == "telegram" || lower == "tg" -> cfg.telegramLabel to cfg.telegramLabelColor
            lower == "mc" || lower == "minecraft" -> cfg.minecraftLabel to cfg.minecraftLabelColor
            lower == "command" -> "[CMD] " to "#FFAA00"
            lower == "discord" || lower == "ds" || lower == "dc" -> cfg.discordLabel to cfg.discordLabelColor
            source.isNotBlank() -> "[$source] " to cfg.minecraftLabelColor
            else -> cfg.minecraftLabel to cfg.minecraftLabelColor
        }
        return Component.literal(label)
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Integer.decode(colorHex))))
    }

    private fun parseSourceLine(line: String): Triple<String, String, String>? {
        val m = Regex("""^\[([^\]]+)]\s+([^:]{1,64}):\s*(.+)$""").find(line.trim()) ?: return null
        val sourceLabel = m.groupValues[1].trim().lowercase()
        val source = when {
            sourceLabel == "telegram" || sourceLabel == "tg" -> "telegram"
            sourceLabel == "minecraft" || sourceLabel == "mc" -> "minecraft"
            sourceLabel == "discord" || sourceLabel == "ds" || sourceLabel == "dc" -> "discord"
            else -> m.groupValues[1].trim()
        }
        return Triple(source, m.groupValues[2].trim(), m.groupValues[3].trim())
    }

    private fun parseQuoteBlock(raw: String): IncomingMessage? {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size < 2) return null
        val quoteLine = lines[1].removePrefix(">").trim()
        val replyLine = lines.drop(2).joinToString(" ").ifBlank { lines.lastOrNull().orEmpty() }
        val quoteParsed = parseSourceLine(quoteLine) ?: return null
        val replyParsed = parseSourceLine(replyLine) ?: return null
        return IncomingMessage(
            source = replyParsed.first,
            username = replyParsed.second,
            body = replyParsed.third,
            quoted = true,
            quotedText = quoteParsed.third,
            quotedFromUser = quoteParsed.second,
            imageUrls = ChatQoL.extractImageUrls(replyParsed.third)
        )
    }

    private fun extractAuthorFromQuoteLine(line: String): String? {
        val bracket = Regex("""^\[([^\]]+)]\s+([^:>]+):""").find(line.trim())
        if (bracket != null) return bracket.groupValues[2].trim().ifBlank { null }
        val plain = Regex("""^([^:>]+):""").find(line.trim())
        return plain?.groupValues?.getOrNull(1)?.trim()?.ifBlank { null }
    }

    private fun stripEmbeddedImageUrls(body: String, known: List<String>): String {
        var text = body
        for (url in known) {
            text = text.replace(url, "").trim()
        }
        return text.trim()
    }

    private fun parseLegacyColoredText(text: String): MutableComponent {
        val out = Component.empty()
        if (text.isEmpty()) return out
        var style = Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))
        val chunk = StringBuilder()
        fun flush() {
            if (chunk.isNotEmpty()) {
                out.append(Component.literal(chunk.toString()).withStyle(style))
                chunk.clear()
            }
        }
        var i = 0
        while (i < text.length) {
            if (text[i] == '§' && i + 1 < text.length) {
                flush()
                style = applyLegacyCode(style, text[i + 1])
                i += 2
            } else {
                chunk.append(text[i])
                i++
            }
        }
        flush()
        return out
    }

    private fun applyLegacyCode(style: Style, code: Char): Style {
        return when (code.lowercaseChar()) {
            '0' -> style.withColor(TextColor.fromRgb(0x000000))
            '1' -> style.withColor(TextColor.fromRgb(0x0000AA))
            '2' -> style.withColor(TextColor.fromRgb(0x00AA00))
            '3' -> style.withColor(TextColor.fromRgb(0x00AAAA))
            '4' -> style.withColor(TextColor.fromRgb(0xAA0000))
            '5' -> style.withColor(TextColor.fromRgb(0xAA00AA))
            '6' -> style.withColor(TextColor.fromRgb(0xFFAA00))
            '7' -> style.withColor(TextColor.fromRgb(0xAAAAAA))
            '8' -> style.withColor(TextColor.fromRgb(0x555555))
            '9' -> style.withColor(TextColor.fromRgb(0x5555FF))
            'a' -> style.withColor(TextColor.fromRgb(0x55FF55))
            'b' -> style.withColor(TextColor.fromRgb(0x55FFFF))
            'c' -> style.withColor(TextColor.fromRgb(0xFF5555))
            'd' -> style.withColor(TextColor.fromRgb(0xFF55FF))
            'e' -> style.withColor(TextColor.fromRgb(0xFFFF55))
            'f' -> style.withColor(TextColor.fromRgb(0xFFFFFF))
            'l' -> style.withBold(true)
            'o' -> style.withItalic(true)
            'n' -> style.withUnderlined(true)
            'm' -> style.withStrikethrough(true)
            'k' -> style.withObfuscated(true)
            'r' -> Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))
            else -> style
        }
    }

    private fun legacyColorToRgb(color: String): Int {
        if (color.startsWith("#")) {
            return try {
                Integer.decode(color)
            } catch (_: Throwable) {
                0xFFFFFF
            }
        }
        return when (color.lowercase()) {
            "§0" -> 0x000000
            "§1" -> 0x0000AA
            "§2" -> 0x00AA00
            "§3" -> 0x00AAAA
            "§4" -> 0xAA0000
            "§5" -> 0xAA00AA
            "§6" -> 0xFFAA00
            "§7" -> 0xAAAAAA
            "§8" -> 0x555555
            "§9" -> 0x5555FF
            "§a" -> 0x55FF55
            "§b" -> 0x55FFFF
            "§c" -> 0xFF5555
            "§d" -> 0xFF55FF
            "§e" -> 0xFFFF55
            else -> 0xFFFFFF
        }
    }
}
