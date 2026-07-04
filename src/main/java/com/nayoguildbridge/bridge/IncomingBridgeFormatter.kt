package com.nayoguildbridge.bridge

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nayoguildbridge.QuoteClickHelper
import com.nayoguildbridge.config.NgbConfig
import com.nayoguildbridge.util.BridgeSourceTags
import com.nayoguildbridge.util.ItemStackJson
import com.nayoguildbridge.qol.ChatQoL
import com.nayoguildbridge.quote.QuoteDetector
import com.nayoguildbridge.quote.QuoteDisplay
import com.nayoguildbridge.quote.QuoteContextRegistry
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemStackTemplate

object IncomingBridgeFormatter {
    data class IncomingMessage(
        val source: String,
        val username: String,
        val body: String,
        val combined: Boolean = false,
        val quoted: Boolean = false,
        val quotedText: String? = null,
        val quotedFromUser: String? = null,
        val quotedSource: String? = null,
        val mcInstance: String? = null,
        val remoteGuildTag: String? = null,
        val remoteGuildColor: String? = null,
        val remoteGuildId: String? = null,
        val remoteGuildName: String? = null,
        val originalGuildId: String? = null,
        val originalGuildName: String? = null,
        val isCrossGuildQuote: Boolean = false,
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
        val source: String
        val username: String
        val body: String
        val parsedInstance: String?
        val parsedGuildTag: String?
        if (parsed != null) {
            source = parsed.source
            username = parsed.username
            body = parsed.body
            parsedInstance = parsed.mcInstance
            parsedGuildTag = parsed.remoteGuildTag
        } else {
            val parts = msg.split(": ", limit = 2)
            source = normalizeSourceId(fromField)
            username = parts.getOrNull(0)?.trim()?.ifBlank { "?" } ?: "?"
            body = parts.getOrNull(1)?.trim()?.ifBlank { msg } ?: msg
            parsedInstance = null
            parsedGuildTag = null
        }

        val quoted = root.get("quoted")?.asString == "true" || root.get("quoted")?.asBoolean == true
        val quotedText = firstString(root, "quotedText", "quotedMessage", "replyToPreview")
            ?.let { QuoteDetector.cleanIncomingQuoteText(it) }
        val quotedFromUser = firstString(root, "quotedFromUser", "replyToUser")
        val mcInstance = firstString(root, "originInstance", "mcInstance", "fromInstanceId") ?: parsedInstance
        val remoteGuildTag = firstString(root, "guildTag", "guild") ?: parsedGuildTag
        val remoteGuildColor = firstString(root, "guildColor")
        val remoteGuildId = firstString(root, "guildId")
        val remoteGuildName = firstString(root, "guildName")
        val originalGuildId = firstString(root, "originalGuildId") ?: remoteGuildId
        val originalGuildName = firstString(root, "originalGuildName", "guildName") ?: remoteGuildName
        val isCrossGuildQuote = parseBoolField(root, "isCrossGuildQuote")
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
            mcInstance = mcInstance,
            remoteGuildTag = remoteGuildTag,
            remoteGuildColor = remoteGuildColor,
            remoteGuildId = remoteGuildId,
            remoteGuildName = remoteGuildName,
            originalGuildId = originalGuildId,
            originalGuildName = originalGuildName,
            isCrossGuildQuote = isCrossGuildQuote,
            imageUrls = images,
            isCommand = isCommand,
            hasJsonStack = hasStack,
            jsonStackRaw = stackRaw
        )
    }

    fun fromPollText(text: String, mode: String, mcInstance: String? = null, meta: JsonObject? = null): IncomingMessage? {
        val raw = text.trim()
        if (raw.isBlank()) return null

        val parsed = fromPollTextInner(raw, mode, mcInstance) ?: return null
        return applyCrossGuildMeta(parsed, meta)
    }

    private fun fromPollTextInner(text: String, mode: String, mcInstance: String? = null): IncomingMessage? {
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
            return parseQuoteBlock(raw) ?: parseQuoteBlockCollapsed(raw)
        }

        val combinedMatch = Regex("""^\[B]\s+([^:]{1,64}):\s*(.+)$""").find(raw)
        if (combinedMatch != null) {
            val rawBody = combinedMatch.groupValues[2].trim()
            val quote = QuoteDetector.parseIncomingQuote(rawBody)
            return IncomingMessage(
                source = "minecraft",
                username = combinedMatch.groupValues[1].trim(),
                body = quote?.replyText ?: rawBody,
                combined = true,
                quoted = quote != null,
                quotedText = quote?.quotedText,
                quotedFromUser = quote?.quotedFromUser,
                imageUrls = ChatQoL.extractImageUrls(rawBody)
            )
        }

        val combinedShow = Regex("""^\[B]\s+(\S+)\s+(.+)$""").find(raw)
        if (combinedShow != null && raw.contains(" is holding ", ignoreCase = true)) {
            return IncomingMessage(
                source = "minecraft",
                username = combinedShow.groupValues[1].trim(),
                body = combinedShow.groupValues[2].trim(),
                combined = true
            )
        }

        val quoteInline = Regex("""^>\s*\[([^\]]+)]\s+([^:]+):\s*(.+?)\s+(\[[^\]]+]\s+[^:]+:.*)$""").find(raw)
        if (quoteInline != null) {
            val source = quoteInline.groupValues[1].trim()
            val quotedUser = quoteInline.groupValues[2].trim()
            val quotedBody = quoteInline.groupValues[3].trim()
            val replyPart = quoteInline.groupValues[4].trim()
            val replyParsed = parseSourceLine(replyPart) ?: return null
            return IncomingMessage(
                source = replyParsed.source,
                username = replyParsed.username,
                body = replyParsed.body,
                quoted = true,
                quotedText = quotedBody,
                quotedFromUser = quotedUser,
                mcInstance = replyParsed.mcInstance,
                remoteGuildTag = replyParsed.remoteGuildTag,
                imageUrls = ChatQoL.extractImageUrls(replyParsed.body)
            )
        }

        val parsed = parseSourceLine(raw) ?: return IncomingMessage(
            source = "minecraft",
            username = "?",
            body = raw,
            mcInstance = mcInstance,
            imageUrls = ChatQoL.extractImageUrls(raw)
        )

        val quote = QuoteDetector.parseIncomingQuote(parsed.body)
        return IncomingMessage(
            source = parsed.source,
            username = parsed.username,
            body = quote?.replyText ?: parsed.body,
            quoted = quote != null,
            quotedText = quote?.quotedText,
            quotedFromUser = quote?.quotedFromUser ?: parsed.username,
            mcInstance = parsed.mcInstance ?: mcInstance,
            remoteGuildTag = parsed.remoteGuildTag,
            imageUrls = ChatQoL.extractImageUrls(parsed.body)
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

    fun formatLocalQuoteOutgoing(username: String, quote: QuoteDetector.Result, combined: Boolean): Component {
        val quotedText = quote.quotedMessage?.takeIf { it.isNotBlank() } ?: "—"
        val quoteSource = BridgeSourceTags.normalizeSourceId(quote.quotedFromInstance ?: "discord")
        val msg = IncomingMessage(
            source = "minecraft",
            username = username,
            body = quote.body,
            combined = combined,
            quoted = true,
            quotedText = quotedText,
            quotedFromUser = quote.quotedFromUser,
            quotedSource = quoteSource,
            imageUrls = ChatQoL.extractImageUrls(quote.body)
        )
        if (combined) {
            return format(msg, NgbConfig.config.imsGuildTag, NgbConfig.config.imsGuildColor)
        }
        return formatGuildStyleLocalQuote(msg, quoteSource)
    }

    private fun formatGuildStyleLocalQuote(msg: IncomingMessage, quoteSource: String): Component {
        val cfg = NgbConfig.config
        val nameRgb = configColorHex(cfg.nameColor)
        val msgRgb = legacyColorToRgb(cfg.messageColor)
        val bodyText = ChatQoL.applyIncomingBody(msg.body)

        val out = Component.empty()
            .append(sourceLabelComponent(msg.source, msg.mcInstance))
            .append(
                Component.literal(msg.username)
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameRgb)))
            )
            .append(
                Component.literal(": ")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(msgRgb)))
            )
            .append(
                QuoteDisplay.buildInlineQuoteReply(
                    msg.quotedText ?: "—",
                    bodyText,
                    msg.quotedFromUser,
                    quoteSource,
                    msg.originalGuildName,
                    msg.isCrossGuildQuote,
                ) { reply ->
                    ChatQoL.toDisplayComponent(reply)
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(msgRgb)))
                }
            )

        val quoteButtonSource = when (quoteSource) {
            "telegram" -> "telegram"
            "minecraft" -> "minecraft"
            else -> "discord"
        }
        out.append(
            QuoteClickHelper.quoteActionButton(
                msg.quotedFromUser?.takeIf { it.isNotBlank() } ?: msg.username,
                quoteButtonSource,
                bodyText
            )
        )
        return out
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
            .append(sourceLabelComponent(msg.source, msg.mcInstance, msg.remoteGuildTag))
            .append(
                com.nayoguildbridge.bridge.ExternalGiBadgeRegistry.appendBadgePrefix(
                    msg.username,
                    Component.literal(msg.username)
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColor)))
                )
            )
            .append(
                Component.literal(": ")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(msgColorRgb)))
            )
            .append(bodyComponent)

        appendExtras(out, msg, stack, msg.source)
        registerQuoteContext(msg)
        return out
    }

    private fun formatCombined(msg: IncomingMessage, defaultTag: String, defaultColor: String): Component {
        val cfg = NgbConfig.config

        val tag = when {
            msg.source == "discord" -> "DISC"
            msg.source == "telegram" -> cfg.imsGuildTag.ifBlank { "TG" }
            else -> resolvePeerGuildTag(msg, defaultTag)
        }
        val nameColorRgb = when {
            msg.source == "discord" -> 0x5555FF
            msg.source == "telegram" -> 0x55FFFF
            else -> resolvePeerNameColor(msg, defaultColor)
        }

        val prefix = cfg.imsCombinedPrefix
        val msgColor = cfg.imsCombinedMessageColor

        val header = Component.empty()
            .append(parseLegacyColoredText(prefix))
            .append(sourceLabelComponent(msg.source, msg.mcInstance, msg.remoteGuildTag))
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
        registerQuoteContext(msg)
        return out
    }

    private fun formatImsPeer(msg: IncomingMessage, defaultTag: String, defaultColor: String): Component {
        val cfg = NgbConfig.config
        val tag = resolvePeerGuildTag(msg, defaultTag)
        val nameColorRgb = resolvePeerNameColor(msg, defaultColor)
        val prefix = cfg.imsBridgePrefix
        val msgColor = cfg.imsBridgeMessageColor

        val header = Component.empty()
            .append(parseLegacyColoredText(prefix))
            .append(sourceLabelComponent(msg.source, msg.mcInstance, msg.remoteGuildTag))
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
        registerQuoteContext(msg)
        return out
    }

    private fun resolvePeerGuildTag(msg: IncomingMessage, defaultTag: String): String {
        return msg.remoteGuildTag?.trim()?.takeIf { it.isNotBlank() } ?: defaultTag.ifBlank { "BR" }
    }

    private fun resolvePeerNameColor(msg: IncomingMessage, defaultColor: String): Int {
        val remote = msg.remoteGuildColor?.trim()?.takeIf { it.isNotBlank() }
        return if (remote != null) legacyColorToRgb(remote) else legacyColorToRgb(defaultColor.ifBlank { "§a" })
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
        out.append(QuoteClickHelper.quoteActionButton(
            msg.username,
            sourceId,
            msg.body,
            msg.remoteGuildId ?: msg.originalGuildId,
            msg.remoteGuildName ?: msg.originalGuildName ?: msg.remoteGuildTag,
        ))
    }

    private fun registerQuoteContext(msg: IncomingMessage) {
        val guildId = msg.remoteGuildId ?: msg.originalGuildId
        val guildName = msg.remoteGuildName ?: msg.originalGuildName ?: msg.remoteGuildTag
        QuoteContextRegistry.register(
            msg.username,
            msg.source,
            msg.body,
            guildId,
            guildName,
            msg.remoteGuildTag,
        )
    }

    private fun configColorHex(hex: String): Int = try {
        Integer.decode(hex)
    } catch (_: Throwable) {
        0xC1C3C7
    }

    private fun normalizeSourceId(raw: String): String = BridgeSourceTags.normalizeSourceId(raw)

    private fun buildShowItemBody(stack: ItemStack, msgColor: String): Component {
        val rgb = legacyColorToRgb(msgColor)
        val qty = if (stack.count > 1) " x${stack.count}" else ""
        val itemName = stack.hoverName.copy()
            .withStyle(Style.EMPTY.withHoverEvent(HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(stack))))
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
            val quoted = msg.quotedText
            val quoteSource = msg.quotedSource?.let { BridgeSourceTags.normalizeSourceId(it) } ?: "discord"
            return QuoteDisplay.buildInlineQuoteReply(
                quoted,
                bodyText,
                msg.quotedFromUser,
                quoteSource,
                msg.originalGuildName,
                msg.isCrossGuildQuote,
            ) { reply ->
                ChatQoL.toDisplayComponent(reply).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)))
            }
        }

        val quote = QuoteDetector.parseIncomingQuote(bodyText)
        if (quote != null) {
            return QuoteDisplay.buildInlineQuoteReply(
                quote.quotedText,
                quote.replyText,
                quote.quotedFromUser,
                quote.quotedFromInstance,
                msg.originalGuildName,
                msg.isCrossGuildQuote,
            ) { reply ->
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

    private data class ParsedSourceLine(
        val source: String,
        val username: String,
        val body: String,
        val mcInstance: String? = null,
        val remoteGuildTag: String? = null,
    )

    private fun sourceLabelComponent(
        source: String,
        mcInstance: String? = null,
        guildTag: String? = null,
    ): Component {
        val cfg = NgbConfig.config
        val lower = source.lowercase()
        val (label, colorHex) = when {
            lower == "telegram" || lower == "tg" -> BridgeSourceTags.TELEGRAM_DISPLAY to cfg.telegramLabelColor
            lower == "mc" || lower == "minecraft" ->
                BridgeSourceTags.mcInstanceDisplayLabel(mcInstance, guildTag) to cfg.minecraftLabelColor
            lower == "command" -> "[CMD] " to "#FFAA00"
            BridgeSourceTags.isDiscordSource(source) -> BridgeSourceTags.DISCORD_DISPLAY to cfg.discordLabelColor
            source.isNotBlank() ->
                BridgeSourceTags.mcInstanceDisplayLabel(mcInstance ?: source, guildTag) to cfg.minecraftLabelColor
            else -> BridgeSourceTags.mcInstanceDisplayLabel(mcInstance, guildTag) to cfg.minecraftLabelColor
        }
        return Component.literal(label)
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Integer.decode(colorHex))))
    }

    private fun parseSourceLine(line: String): ParsedSourceLine? {
        val trimmed = line.trim()
        BridgeSourceTags.parseMcInstanceLine(trimmed)?.let { line ->
            val inner = Regex("""^([^:]{1,64}):\s*(.+)$""").find(line.rest) ?: return@let null
            return ParsedSourceLine(
                "minecraft",
                inner.groupValues[1].trim(),
                inner.groupValues[2].trim(),
                line.instance,
                line.guildTag,
            )
        }
        val m = Regex("""^\[([^\]]+)]\s+([^:]{1,64}):\s*(.+)$""").find(trimmed) ?: return null
        val rawTag = m.groupValues[1].trim()
        val mcPipe = Regex("""^Minecraft\|([^|\]]+)(?:\|(.+))?$""", RegexOption.IGNORE_CASE).find(rawTag)
        if (mcPipe != null) {
            return ParsedSourceLine(
                "minecraft",
                m.groupValues[2].trim(),
                m.groupValues[3].trim(),
                mcPipe.groupValues[1].trim(),
                mcPipe.groupValues[2].trim().ifBlank { null },
            )
        }
        val source = BridgeSourceTags.normalizeSourceId(rawTag)
        val mcInstance = when {
            source == "minecraft" || source == "mc" -> null
            source == "telegram" || source == "tg" -> null
            BridgeSourceTags.isDiscordSource(source) -> null
            else -> rawTag
        }
        val effectiveSource = if (mcInstance != null) "minecraft" else source
        return ParsedSourceLine(
            effectiveSource,
            m.groupValues[2].trim(),
            m.groupValues[3].trim(),
            mcInstance,
            null,
        )
    }

    private fun firstString(root: JsonObject, vararg names: String): String? {
        for (name in names) {
            val el = root.get(name) ?: continue
            if (el.isJsonNull) continue
            val value = el.asString?.trim().orEmpty()
            if (value.isNotEmpty()) return value
        }
        return null
    }

    private fun parseBoolField(root: JsonObject, name: String): Boolean {
        val el = root.get(name) ?: return false
        if (el.isJsonNull) return false
        return when {
            el.isJsonPrimitive && el.asJsonPrimitive.isBoolean -> el.asBoolean
            el.isJsonPrimitive && el.asJsonPrimitive.isString -> el.asString.equals("true", ignoreCase = true)
            else -> false
        }
    }

    private fun applyCrossGuildMeta(msg: IncomingMessage, meta: JsonObject?): IncomingMessage {
        if (meta == null) return msg
        val originalGuildId = firstString(meta, "originalGuildId") ?: msg.originalGuildId
        val originalGuildName = firstString(meta, "originalGuildName") ?: msg.originalGuildName
        val isCrossGuildQuote = parseBoolField(meta, "isCrossGuildQuote") || msg.isCrossGuildQuote
        if (!isCrossGuildQuote && originalGuildId.isNullOrBlank() && originalGuildName.isNullOrBlank()) {
            return msg
        }
        return msg.copy(
            originalGuildId = originalGuildId,
            originalGuildName = originalGuildName,
            isCrossGuildQuote = isCrossGuildQuote,
        )
    }

    private fun parseQuoteBlock(raw: String): IncomingMessage? {
        val body = raw.trimStart().removePrefix("[QUOTE]").trimStart()
        val lines = body.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size < 2) return null
        val quoteLine = lines.first().removePrefix(">").trim()
        val replyLine = lines.drop(1).joinToString(" ")
        val quoteParsed = parseSourceLine(quoteLine) ?: return null
        val arrow = Regex("""^\.?([^→>]{1,64})[→>]\s*([^:]{1,64}):\s*(.+)$""").find(replyLine)
        if (arrow != null) {
            return IncomingMessage(
                source = normalizeSourceId(quoteParsed.source),
                username = arrow.groupValues[1].trim(),
                body = arrow.groupValues[3].trim(),
                quoted = true,
                quotedText = quoteParsed.body,
                quotedFromUser = quoteParsed.username,
                mcInstance = quoteParsed.mcInstance,
                imageUrls = ChatQoL.extractImageUrls(arrow.groupValues[3])
            )
        }
        val replyParsed = parseSourceLine(replyLine) ?: return null
        return IncomingMessage(
            source = replyParsed.source,
            username = replyParsed.username,
            body = replyParsed.body,
            quoted = true,
            quotedText = quoteParsed.body,
            quotedFromUser = quoteParsed.username,
            mcInstance = replyParsed.mcInstance ?: quoteParsed.mcInstance,
            imageUrls = ChatQoL.extractImageUrls(replyParsed.body)
        )
    }

    private fun parseQuoteBlockCollapsed(raw: String): IncomingMessage? {
        val body = raw.trimStart().removePrefix("[QUOTE]").trimStart()
        val quoteMatch = Regex(
            """^>\s*\[([^\]]+)]\s+([^:]{1,64}):\s*(.+?)\s+\.([^→>]{1,64})[→>]\s*([^:]{1,64}):\s*(.+)$"""
        ).find(body) ?: return null
        val source = normalizeSourceId(quoteMatch.groupValues[1])
        val quotedUser = quoteMatch.groupValues[2].trim()
        val quotedText = quoteMatch.groupValues[3].trim()
        val replyUser = quoteMatch.groupValues[4].trim()
        val replyText = quoteMatch.groupValues[6].trim()
        return IncomingMessage(
            source = source,
            username = replyUser,
            body = replyText,
            quoted = true,
            quotedText = quotedText,
            quotedFromUser = quotedUser,
            imageUrls = ChatQoL.extractImageUrls(replyText)
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
