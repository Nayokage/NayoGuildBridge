package com.nayoguildbridge

import com.nayoguildbridge.bridge.BridgeChatDedupe
import com.nayoguildbridge.config.NgbConfig
import com.nayoguildbridge.config.NgbConfig.config
import com.nayoguildbridge.quote.QuoteDetector
import com.nayoguildbridge.quote.QuoteDisplay
import com.nayoguildbridge.util.BridgeSourceTags
import com.nayoguildbridge.util.BridgeTextUtil
import com.nayoguildbridge.util.GuildChatClassifier
import com.nayoguildbridge.qol.ChatQoL
import com.nayoguildbridge.guard.EnvironmentGuard
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.ChatFormatting
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.regex.Pattern.compile


object NayoGuildBridge : ModInitializer {
    val logger: Logger = LoggerFactory.getLogger("nayoguildbridge")
    const val GUILD_PATTERN =
        ("^(?:G|Guild|Officer)\\s*>\\s*(?:\\[[^\\]]+\\]\\s*)*([A-Za-z0-9_]{1,16})(?:\\s*\\[[^\\]]+\\])*\\s*:\\s*(.+)$")
    const val BRIDGE_PATTERN =
        ("^ *((?:.+?)(?: attached an? \\w+(?::|$)| replied to .+ with an? \\w+(?::|$)| replied to .+?(?::|$)|:))(?:(?: (.*)?$)|$)")
    private val STRIP_FORMATTING = compile("§\\w")
    private val BRACKET_SOURCE = compile("""^\[([^\]]+)]\s+([^:]{1,64}):\s*(.+)$""")

    sealed class ChatTransform {
        data object Keep : ChatTransform()
        data object Hide : ChatTransform()
        data class Replace(val component: Component) : ChatTransform()
    }

    override fun onInitialize() {
        NgbConfig.load()
        ClientReceiveMessageEvents.MODIFY_GAME.register { message, actionBar ->
            when (val t = transformIncomingMessage(message, actionBar)) {
                is ChatTransform.Keep -> {
                    if (!actionBar) rememberOwnGuildLineIfVisible(message.string)
                    message
                }
                is ChatTransform.Hide -> Component.empty()
                is ChatTransform.Replace -> t.component
            }
        }
    }

    fun transformIncomingMessage(
        message: Component,
        actionBar: Boolean,
        chatSender: String? = null
    ): ChatTransform {
        if (actionBar) return ChatTransform.Keep
        if (!config.bridgeEnabled) return ChatTransform.Keep
        if (!EnvironmentGuard.isOperational()) return ChatTransform.Keep

        val unformatted = STRIP_FORMATTING.matcher(message.string).replaceAll("")

        val firstWord = unformatted.split(" ").firstOrNull() ?: ""
        val isOfficerChannel = firstWord.equals("Officer", ignoreCase = true)
        var channel = when (firstWord) {
            "From" -> ChatChannel.PRIVATE
            "Party" -> ChatChannel.PARTY
            "Guild", "G", "Officer" -> ChatChannel.GUILD
            else -> ChatChannel.UNKNOWN
        }

        if (!chatSender.isNullOrBlank()) {
            val senderIsBot = isBridgeBotNick(chatSender)
            val body = unformatted.trim()
            val relayBody = GuildChatClassifier.looksLikeIncomingRelayBody(body, senderIsBot)
            if (channel == ChatChannel.UNKNOWN && (senderIsBot || relayBody)) {
                channel = ChatChannel.GUILD
            }
            if (isMyNick(chatSender) || (!senderIsBot && !relayBody)) {
                return ChatTransform.Keep
            }
        }

        if (config.bridgeCommandFormatEnabled) {
            val maybeCmd = formatBridgeCommandMessage(message, unformatted)
            if (maybeCmd !== message) {
                return ChatTransform.Replace(maybeCmd)
            }
        }

        if (channel == ChatChannel.GUILD && config.guildBridgeFormatEnabled) {
            val guildMatch = compile(GUILD_PATTERN).matcher(unformatted)
            val relayBodyOnly = !chatSender.isNullOrBlank() &&
                GuildChatClassifier.looksLikeIncomingRelayBody(
                    unformatted.trim(),
                    isBridgeBotNick(chatSender!!)
                )
            val (username, rawBody) = if (guildMatch.matches()) {
                guildMatch.group(1) to guildMatch.group(2)
            } else if (!chatSender.isNullOrBlank() && (isBridgeBotNick(chatSender) || relayBodyOnly)) {
                chatSender to unformatted.trim()
            } else {
                return ChatTransform.Keep
            }

            var text = rawBody
            val speakerIsBot = isBridgeBotNick(username) ||
                GuildChatClassifier.hasBridgeRankOnLine(unformatted, username)

            if (!GuildChatClassifier.shouldApplyBridgeFormat(unformatted, username, rawBody)) {
                return ChatTransform.Keep
            }

            val fromKnownBridgeBot = speakerIsBot

            if (text.trimStart().startsWith("[QUOTE]", ignoreCase = true)) {
                val quoteFormatted = formatRelayedQuoteMessage(text, username)
                if (quoteFormatted != null) return ChatTransform.Replace(quoteFormatted)
            }

            if (shouldBlock(username, text)) {
                return ChatTransform.Hide
            }

            val rawBodyForSource = rawBody
            val bracketParsed = parseBracketSourceLine(text)
            val sourceIdFromBody = when {
                bracketParsed != null -> bracketParsed.sourceId
                else -> detectSourceId(rawBodyForSource)
            }

            val (sourceComponent, cleanedText) = if (bracketParsed != null) {
                buildSourceLabelForId(bracketParsed.sourceId, bracketParsed.mcInstance, bracketParsed.guildTag) to bracketParsed.body
            } else {
                buildSourcePrefixAndStrip(text)
            }
            text = cleanedText
            if (isIgnoredByOrigin(sourceIdFromBody)) return ChatTransform.Hide

            val bridgeMatcher = compile(BRIDGE_PATTERN).matcher(text)

            val (name, msg) = if (bracketParsed != null) {
                bracketParsed.nick to bracketParsed.body
            } else if (bridgeMatcher.find()) {
                    bridgeMatcher.group(1) to (bridgeMatcher.group(2) ?: "")
                } else {
                parseBridgeSenderAndMessage(text) ?: when {
                    fromKnownBridgeBot || config.hideBotName -> "" to text
                    else -> username to text
                }
                }

            val strippedMsg = msg.replaceFirst(Regex("^: "), "")
            val rawNick = if (bracketParsed != null) {
                bracketParsed.nick
            } else {
                extractSenderNick(name)
            }
            val (senderNick, arrowQuoteTarget) = splitBridgeArrowNick(rawNick)
            if (isIgnoredByPlayer(senderNick)) return ChatTransform.Hide

            val quoteTargetNick = arrowQuoteTarget ?: extractQuotePrefillTarget(name, senderNick)
            val isMine = isMyNick(senderNick)
            val isReplyLike = looksLikeReplyName(if (msg.isEmpty()) text else name)

            val prefixComponent = when {
                isOfficerChannel -> Component.literal("[Officer] ")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(config.officerPrefixColor.toColor())))
                sourceComponent.string.isNotBlank() -> sourceComponent
                else -> buildSourceLabelForId(sourceIdFromBody, bracketParsed?.mcInstance, bracketParsed?.guildTag)
            }
            val displayNameRaw = when {
                isReplyLike -> senderNick
                senderNick.isNotBlank() -> senderNick
                name.isNotBlank() -> name
                else -> ""
            }
            val senderComponentBase = buildSenderNameComponent(displayNameRaw, config.nameColor.toColor(), isMine)
            val senderComponent = if (config.nickHighlightEnabled && isMine) {
                Component.literal(senderComponentBase.string)
                    .withStyle(
                        Style.EMPTY
                            .withColor(TextColor.fromRgb(Integer.decode(config.nickHighlightColor)))
                            .withBold(true)
                    )
            } else {
                senderComponentBase
            }

            val finalBody = ChatQoL.collapseText(ChatQoL.applyIncomingBody(strippedMsg))
            val formatted = Component.empty()
                .append(prefixComponent)
                .append(if (isReplyLike) buildReplyMarkerComponent() else Component.empty())
            if (displayNameRaw.isNotBlank()) {
                formatted
                .append(senderComponent)
                .append(
                        Component.literal(": ")
                        .withColor(config.messageColor.toColor())
                    )
            }
            val bodyComponent = if (finalBody.isBlank()) {
                Component.empty()
            } else {
                var body = buildMessageBodyComponent(
                    finalBody,
                    config.messageColor.toColor(),
                    isMine,
                    sourceIdFromBody,
                    arrowQuoteTarget
                )
                if (config.nickHighlightEnabled) {
                    body = recolorNickOfPlayer(body, Integer.decode(config.nickHighlightColor), bold = true)
                }
                body
            }
            formatted.append(bodyComponent)
            if (displayNameRaw.isNotBlank()) {
                formatted.append(buildQuoteActionComponent(quoteTargetNick, sourceIdFromBody, finalBody))
            }

            val displayUser = displayNameRaw.ifBlank { senderNick }
            if (finalBody.isNotBlank() &&
                !BridgeChatDedupe.claimIncoming(unformatted, displayUser, finalBody)
            ) {
                return ChatTransform.Hide
            }

            return ChatTransform.Replace(formatted)
        }
        return ChatTransform.Keep
    }

    private data class BracketSource(
        val sourceId: String,
        val nick: String,
        val body: String,
        val mcInstance: String? = null,
        val guildTag: String? = null,
    )

    private fun parseBracketSourceLine(raw: String): BracketSource? {
        val s = raw.trimStart()
        BridgeSourceTags.parseMcInstanceLine(s)?.let { line ->
            val rest = Regex("""^([^:→>]{1,64})(?:[→>]([^:]{1,64}))?\s*:\s*(.+)$""").find(line.rest.trim()) ?: return@let null
            val sender = rest.groupValues[1].trim().removePrefix(".")
            val target = rest.groupValues[2].trim()
            val reply = rest.groupValues[3].trim()
            val body = if (target.isNotEmpty()) {
                "> [Minecraft] $target: | $reply"
            } else {
                reply
            }
            return BracketSource("minecraft", sender, body, line.instance, line.guildTag)
        }
        if (s.startsWith(BridgeSourceTags.MINECRAFT_MARKER)) {
            val rest = s.removePrefix(BridgeSourceTags.MINECRAFT_MARKER).trimStart()
            val dot = Regex("""^([^:→>]{1,64})(?:[→>]([^:]{1,64}))?\s*:\s*(.+)$""").find(rest) ?: return null
            val sender = dot.groupValues[1].trim()
            val target = dot.groupValues[2].trim()
            val reply = dot.groupValues[3].trim()
            val body = if (target.isNotEmpty()) {
                "> [Minecraft] $target: | $reply"
            } else {
                reply
            }
            return BracketSource("minecraft", sender, body)
        }
        val m = BRACKET_SOURCE.matcher(s)
        if (!m.matches()) return null
        val rawTag = m.group(1).trim()
        val mcPipe = Regex("""^Minecraft\|([^|\]]+)(?:\|(.+))?$""", RegexOption.IGNORE_CASE).find(rawTag)
        if (mcPipe != null) {
            return BracketSource(
                "minecraft",
                m.group(2).trim().removePrefix("."),
                m.group(3).trim(),
                mcPipe.groupValues[1].trim(),
                mcPipe.groupValues[2].trim().ifBlank { null },
            )
        }
        val sourceId = BridgeSourceTags.normalizeSourceId(rawTag)
        val mcInstance = when {
            sourceId == "minecraft" || sourceId == "mc" -> null
            sourceId == "telegram" || sourceId == "tg" -> null
            isDiscordSource(sourceId) -> null
            else -> rawTag
        }
        val effectiveSource = if (mcInstance != null) "minecraft" else sourceId
        val nick = m.group(2).trim().removePrefix(".")
        val body = m.group(3).trim()
        return BracketSource(effectiveSource, nick, body, mcInstance)
    }

    private fun isDiscordSource(sourceId: String): Boolean = BridgeSourceTags.isDiscordSource(sourceId)

    private fun buildSourceLabelForId(
        sourceId: String,
        mcInstance: String? = null,
        guildTag: String? = null,
    ): Component {
        val lower = sourceId.lowercase()
        val (label, colorHex) = when {
            lower == "telegram" || lower == "tg" -> BridgeSourceTags.TELEGRAM_DISPLAY to config.telegramLabelColor
            lower == "minecraft" || lower == "mc" ->
                BridgeSourceTags.mcInstanceDisplayLabel(mcInstance, guildTag) to config.minecraftLabelColor
            isDiscordSource(sourceId) -> BridgeSourceTags.DISCORD_DISPLAY to config.discordLabelColor
            sourceId.isNotBlank() ->
                BridgeSourceTags.mcInstanceDisplayLabel(mcInstance ?: sourceId, guildTag) to config.minecraftLabelColor
            else -> BridgeSourceTags.mcInstanceDisplayLabel(mcInstance, guildTag) to config.minecraftLabelColor
        }
        return Component.literal(label)
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Integer.decode(colorHex))))
    }

    fun isLocalPlayerGuildLine(rawGuildLine: String): Boolean {
        val parsed = com.nayoguildbridge.util.BridgeOutboundFilter.parseGuildLine(rawGuildLine) ?: return false
        return isMyNick(parsed.first)
    }

    fun rememberOwnGuildLineIfVisible(rawGuildLine: String) {
        if (!config.guildBridgeFormatEnabled) return
        val parsed = com.nayoguildbridge.util.BridgeOutboundFilter.parseGuildLine(rawGuildLine) ?: return
        val (speaker, body) = parsed
        if (!isMyNick(speaker)) return
        if (com.nayoguildbridge.util.BridgeOutboundFilter.isBridgeRelayPayload(body)) return
        BridgeChatDedupe.remember(BridgeChatDedupe.keyFor(speaker, body))
    }

    private fun formatRelayedQuoteMessage(raw: String, botNick: String): Component? {
        val body = raw.trimStart().removePrefix("[QUOTE]").trimStart()
        val lines = body.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        var quotedText = ""
        var quotedUser = ""
        var quotedSource = "Discord"
        var replySource = "minecraft"
        var replyUser = botNick
        var replyText = ""

        val quoteLine = lines.firstOrNull { it.startsWith(">") }
        if (quoteLine != null) {
            val q = quoteLine.removePrefix(">").trimStart()
            val m = Regex("""^\[([^\]]+)]\s+([^:]{1,64}):\s*(.+)$""").find(q)
            if (m != null) {
                quotedSource = BridgeTextUtil.normalizeSourceTag(m.groupValues[1]) ?: "Discord"
                quotedUser = m.groupValues[2].trim()
                quotedText = BridgeTextUtil.stripBridgeFormatting(m.groupValues[3])
            }
        }

        val last = lines.last()
        val arrow = Regex("""^\.?([^→>]{1,64})[→>]\s*([^:]{1,64}):\s*(.+)$""").find(last)
        if (arrow != null) {
            replyUser = arrow.groupValues[1].trim()
            quotedUser = arrow.groupValues[2].trim().ifBlank { quotedUser }
            replyText = BridgeTextUtil.stripBridgeFormatting(arrow.groupValues[3])
        } else {
            val plain = Regex("""^\[([^\]]+)]\s+([^:]{1,64}):\s*(.+)$""").find(last)
            if (plain != null) {
                replySource = BridgeTextUtil.normalizeSourceTag(plain.groupValues[1])?.lowercase() ?: "minecraft"
                replyUser = plain.groupValues[2].trim()
                replyText = BridgeTextUtil.stripBridgeFormatting(plain.groupValues[3])
            } else {
                replyText = BridgeTextUtil.stripBridgeFormatting(last)
            }
        }

        if (replyText.isBlank() && quotedText.isBlank()) return null

        val senderComponent = buildSenderNameComponent(replyUser, config.nameColor.toColor(), isMyNick(replyUser))
        val quoteBody = if (quotedText.isNotBlank()) quotedText else "—"
        val replyBody = replyText.ifBlank { " " }

        return Component.empty()
            .append(buildSourceLabelForId(replySource.lowercase()))
            .append(senderComponent)
            .append(Component.literal(": ").withColor(config.messageColor.toColor()))
            .append(
                QuoteDisplay.buildInlineQuoteReply(quoteBody, replyBody, quotedUser, quotedSource) { reply ->
                    buildMessageWithWordHighlights(reply, config.messageColor.toColor(), isMyNick(replyUser))
                }
            )
            .append(buildQuoteActionComponent(quotedUser.ifBlank { replyUser }, quotedSource.lowercase(), replyBody))
    }

    private fun guildChatBodyOrFull(unformatted: String): String {
        val match = compile(GUILD_PATTERN).matcher(unformatted)
        return if (match.matches()) match.group(2) else unformatted
    }

    fun hasYacl(): Boolean {
        try {
            Class.forName("dev.isxander.yacl3.api.YetAnotherConfigLib")
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun String.toColor(): Int {
        return Integer.decode(this)
    }

    private fun shouldBlock(username: String, text: String): Boolean {
        val list = config.blockList
        if (list.isEmpty()) return false
        val lowerText = text.lowercase()
        val lowerUser = username.lowercase()
        return list.any { term ->
            val t = term.trim().lowercase()
            t.isNotBlank() && (lowerText.contains(t) || lowerUser == t || lowerUser.contains(t))
        }
    }

    private fun recolorNickOfPlayer(component: Component, rgb: Int, bold: Boolean): Component {
        val client = Minecraft.getInstance()
        val player = client.player ?: return component

        val originalNick = client.user.name
        val currentNick = player.name.string

        val fullText = component.string
        val lower = fullText.lowercase()
        val nick = when {
            currentNick.isNotBlank() && lower.contains(currentNick.lowercase()) -> currentNick
            originalNick.isNotBlank() && lower.contains(originalNick.lowercase()) -> originalNick
            else -> return component
        }

        val idx = lower.indexOf(nick.lowercase())
        if (idx < 0) return component

        val color = TextColor.fromRgb(rgb)
        val res: MutableComponent = Component.empty()

        if (idx > 0) {
            res.append(Component.literal(fullText.substring(0, idx)).withStyle(component.style))
        }

        res.append(
            Component.literal(fullText.substring(idx, idx + nick.length))
                .withStyle(Style.EMPTY.withColor(color).withBold(bold))
        )

        val end = idx + nick.length
        if (end < fullText.length) {
            res.append(Component.literal(fullText.substring(end)).withStyle(component.style))
        }

        return res
    }

    private fun buildSourcePrefixAndStrip(raw: String): Pair<Component, String> {
        var body = raw
        val (labelText, labelColor) = when {
            body.startsWith(BridgeSourceTags.TELEGRAM_MARKER) -> {
                body = body.removePrefix(BridgeSourceTags.TELEGRAM_MARKER).trimStart()
                BridgeSourceTags.TELEGRAM_DISPLAY to config.telegramLabelColor
            }
            body.startsWith(BridgeSourceTags.MINECRAFT_MARKER) -> {
                body = body.removePrefix(BridgeSourceTags.MINECRAFT_MARKER).trimStart()
                BridgeSourceTags.MINECRAFT_DISPLAY to config.minecraftLabelColor
            }
            body.startsWith(BridgeSourceTags.TELEGRAM_DISPLAY.trimStart()) -> {
                body = body.removePrefix(BridgeSourceTags.TELEGRAM_DISPLAY.trimStart()).trimStart()
                BridgeSourceTags.TELEGRAM_DISPLAY to config.telegramLabelColor
            }
            body.startsWith(BridgeSourceTags.MINECRAFT_DISPLAY.trimStart()) -> {
                body = body.removePrefix(BridgeSourceTags.MINECRAFT_DISPLAY.trimStart()).trimStart()
                BridgeSourceTags.MINECRAFT_DISPLAY to config.minecraftLabelColor
            }
            body.startsWith(BridgeSourceTags.DISCORD_DISPLAY.trimEnd()) -> {
                body = body.removePrefix(BridgeSourceTags.DISCORD_DISPLAY.trimEnd()).trimStart()
                BridgeSourceTags.DISCORD_DISPLAY to config.discordLabelColor
            }
            body.startsWith("[DC]", ignoreCase = true) -> {
                body = body.removePrefix("[DC]").trimStart()
                BridgeSourceTags.DISCORD_DISPLAY to config.discordLabelColor
            }
            else -> {
                BridgeSourceTags.parseMcInstanceLine(body)?.let { line ->
                    body = line.rest
                    return buildSourceLabelForId("minecraft", line.instance, line.guildTag) to body
                }
                val bracket = BRACKET_SOURCE.matcher(body)
                if (bracket.matches()) {
                    val rawTag = bracket.group(1).trim()
                    val mcPipe = Regex("""^Minecraft\|([^|\]]+)(?:\|(.+))?$""", RegexOption.IGNORE_CASE).find(rawTag)
                    if (mcPipe != null) {
                        body = bracket.group(3).trim()
                        return buildSourceLabelForId(
                            "minecraft",
                            mcPipe.groupValues[1].trim(),
                            mcPipe.groupValues[2].trim().ifBlank { null },
                        ) to body
                    }
                    val sourceId = BridgeSourceTags.normalizeSourceId(rawTag)
                    val mcInstance = when {
                        sourceId == "minecraft" || sourceId == "mc" -> null
                        sourceId == "telegram" || sourceId == "tg" -> null
                        isDiscordSource(sourceId) -> null
                        else -> rawTag
                    }
                    body = bracket.group(3).trim()
                    return buildSourceLabelForId(
                        if (mcInstance != null) "minecraft" else sourceId,
                        mcInstance,
                    ) to body
                }
                return Component.empty() to body
            }
        }

        val label = Component.literal(labelText)
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Integer.decode(labelColor))))
        return label to body
    }

    private fun detectSourceId(rawBody: String): String {
        val body = rawBody.trimStart()
        return when {
            body.startsWith(BridgeSourceTags.TELEGRAM_MARKER) -> "telegram"
            BridgeSourceTags.parseMcInstanceLine(body) != null -> "minecraft"
            body.startsWith(BridgeSourceTags.MINECRAFT_MARKER) -> "minecraft"
            body.startsWith(BridgeSourceTags.TELEGRAM_DISPLAY.trimStart()) -> "telegram"
            body.startsWith(BridgeSourceTags.MINECRAFT_DISPLAY.trimStart()) -> "minecraft"
            body.startsWith(BridgeSourceTags.DISCORD_DISPLAY.trimEnd()) -> "discord"
            body.startsWith("[DC]", ignoreCase = true) -> "discord"
            else -> {
                val bracket = BRACKET_SOURCE.matcher(body)
                if (bracket.matches()) {
                    BridgeSourceTags.normalizeSourceId(bracket.group(1).trim())
                } else {
                    "minecraft"
                }
            }
        }
    }

    private fun isBridgeBotNick(nick: String): Boolean {
        val list = config.bridgeBotNames
        if (list.isEmpty()) return false
        return list.any { it.isNotBlank() && nick.equals(it, ignoreCase = true) }
    }

    private fun isIgnoredByPlayer(nick: String): Boolean {
        if (nick.isBlank()) return false
        return config.imsIgnorePlayers.any { it.equals(nick, ignoreCase = true) }
    }

    private fun isIgnoredByOrigin(origin: String): Boolean {
        if (origin.isBlank()) return false
        return config.imsIgnoreOrigins.any { it.equals(origin, ignoreCase = true) }
    }

    private fun isFromBridgeBotText(unformatted: String): Boolean {
        val lower = unformatted.lowercase()
        val list = config.bridgeBotNames
        if (list.isEmpty()) return false
        return list.any { bot ->
            val b = bot.trim().lowercase()
            b.isNotBlank() && (
                lower.contains("$b:") ||
                    lower.contains("[$b]") ||
                    lower.contains(" $b ")
                )
        }
    }

    private fun parseBridgeSenderAndMessage(raw: String): Pair<String, String>? {
        val s = raw.trim()
        if (s.isEmpty()) return null

        val gt = s.indexOf('>')
        if (gt in 1..40) {
            val left = s.substring(0, gt).trim()
            val right = s.substring(gt + 1).trimStart()
            if (right.isNotEmpty() && left.isNotEmpty()) {
                val leftToken = left.split(" ").firstOrNull().orEmpty()
                if (leftToken.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
                    return left to right
                }
            }
        }

        val colon = s.indexOf(':')
        if (colon in 1..40) {
            val left = s.substring(0, colon).trim()
            val right = s.substring(colon + 1).trimStart()
            if (right.isNotEmpty() && left.isNotEmpty()) return left to right
        }
        return null
    }

    private fun buildSenderNameComponent(namePartRaw: String, nameColor: Int, isMine: Boolean): Component {
        var s = namePartRaw.replaceFirst(Regex(":$"), "").trim()
        if (s.isEmpty()) return Component.empty()

        val allowStyle = !config.senderNickStyleOnlyMine || isMine
        val senderStyle: Style? = when {
            !allowStyle -> null
            config.senderNickLegacyEnabled -> styleFromLegacyCodes(config.senderNickLegacyCodes)
            config.senderNickColorEnabled -> Style.EMPTY.withColor(TextColor.fromRgb(Integer.decode(config.senderNickColor)))
            else -> null
        }

        val prefix = StringBuilder()
        while (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end <= 0) break
            prefix.append(s.substring(0, end + 1))
            s = s.substring(end + 1).trimStart()
            if (s.isNotEmpty() && !s.startsWith("[")) prefix.append(" ")
        }

        val firstToken = s.split(" ").firstOrNull().orEmpty()
        val arrowPos = firstToken.indexOf('→')
        val nick = if (arrowPos > 0) firstToken.substring(0, arrowPos) else firstToken
        val arrowTarget = if (arrowPos > 0 && arrowPos + 1 < firstToken.length) firstToken.substring(arrowPos + 1) else ""
        var rest = s.removePrefix(firstToken)
        if (arrowTarget.isNotBlank()) {
            rest = ""
        }
        if (rest.contains("replied to", ignoreCase = true)) {
            val idx = rest.lowercase().indexOf("replied to")
            if (idx >= 0) {
                val before = rest.substring(0, idx + "replied to".length)
                val after = rest.substring(idx + "replied to".length).trimStart()
                val target = after.split(" ").firstOrNull().orEmpty()
                val tail = after.removePrefix(target)
                rest = before + " " + target + tail
            }
        }

        val out: MutableComponent = Component.empty()
        if (prefix.isNotEmpty()) out.append(Component.literal(prefix.toString()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColor))))

        if (senderStyle != null && nick.isNotEmpty()) {
            out.append(Component.literal(nick).withStyle(senderStyle))
        } else {
            out.append(Component.literal(nick).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColor))))
        }

        if (rest.contains("replied to", ignoreCase = true)) {
            rest = ""
        }

        if (rest.isNotBlank()) {
            val lower = rest.lowercase()
            val key = "replied to "
            val pos = lower.indexOf(key)
            if (pos >= 0) {
                val start = pos + key.length
                val after = rest.substring(start)
                val target = after.trimStart().split(" ").firstOrNull().orEmpty()
                val tStart = rest.indexOf(target, startIndex = start)
                if (target.isNotEmpty() && tStart >= 0) {
                    val tEnd = tStart + target.length
                    if (tStart > 0) out.append(Component.literal(rest.substring(0, tStart)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColor))))
                    out.append(Component.literal(rest.substring(tStart, tEnd)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x55FFFF)).withBold(true)))
                    if (tEnd < rest.length) out.append(Component.literal(rest.substring(tEnd)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColor))))
                } else {
                    out.append(Component.literal(rest).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColor))))
                }
            } else {
                out.append(Component.literal(rest).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(nameColor))))
            }
        }

        return com.nayoguildbridge.bridge.ExternalGiBadgeRegistry.appendBadgePrefix(nick, out)
    }

    private fun buildMessageWithWordHighlights(text: String, baseColor: Int, isMine: Boolean): Component {
        val processed = ChatQoL.applyIncomingBody(text)
        val baseStyle = Style.EMPTY.withColor(TextColor.fromRgb(baseColor))
        if (!config.wordHighlightEnabled) {
            return ChatQoL.toDisplayComponent(processed).withStyle(baseStyle)
        }
        if (config.wordHighlightOnlyMine && !isMine) {
            return ChatQoL.toDisplayComponent(processed).withStyle(baseStyle)
        }

        val rules = parseWordHighlightRules(config.wordHighlightRules)
        if (rules.isEmpty()) {
            return ChatQoL.toDisplayComponent(processed).withStyle(baseStyle)
        }

        val lower = processed.lowercase()
        val out: MutableComponent = Component.empty()

        var i = 0
        while (i < processed.length) {
            var bestStart = -1
            var bestEnd = -1
            var bestStyle: Style? = null

            for ((wordLower, style) in rules) {
                if (wordLower.isBlank()) continue
                val idx = lower.indexOf(wordLower, startIndex = i)
                if (idx >= 0 && (bestStart < 0 || idx < bestStart)) {
                    bestStart = idx
                    bestEnd = idx + wordLower.length
                    bestStyle = style
                }
            }

            if (bestStart < 0 || bestStyle == null) {
                out.append(ChatQoL.toDisplayComponent(processed.substring(i)).withStyle(baseStyle))
                break
            }

            if (bestStart > i) {
                out.append(ChatQoL.toDisplayComponent(processed.substring(i, bestStart)).withStyle(baseStyle))
            }
            out.append(Component.literal(processed.substring(bestStart, bestEnd)).withStyle(bestStyle))
            i = bestEnd
        }

        return out
    }

    private fun buildMessageBodyComponent(
        text: String,
        baseColor: Int,
        isMine: Boolean,
        quoteSourceId: String = "discord",
        arrowQuoteTarget: String? = null
    ): Component {
        val quotePair = QuoteDetector.parseIncomingQuoteBody(text)
        if (quotePair != null) {
            val quote = QuoteDetector.parseIncomingQuote(text)
            return QuoteDisplay.buildInlineQuoteReply(
                quotePair.first,
                quotePair.second,
                quote?.quotedFromUser,
                quote?.quotedFromInstance ?: quoteSourceId
            ) { replyText ->
                buildMessageWithWordHighlights(replyText, baseColor, isMine)
            }
        }
        if (!arrowQuoteTarget.isNullOrBlank()) {
            return QuoteDisplay.buildInlineQuoteReply(
                "—",
                text,
                arrowQuoteTarget,
                quoteSourceId
            ) { replyText ->
                buildMessageWithWordHighlights(replyText, baseColor, isMine)
            }
        }
        return buildMessageWithWordHighlights(text, baseColor, isMine)
    }

    private fun splitBridgeArrowNick(raw: String): Pair<String, String?> {
        val s = raw.trim()
        if (s.isEmpty()) return "" to null
        val gt = s.indexOf("->")
        if (gt > 0) {
            val sender = s.substring(0, gt).trim()
            val target = s.substring(gt + 2).trim()
            if (sender.isNotEmpty() && target.isNotEmpty()) return sender to target
        }
        for (arrow in charArrayOf('⇾', '→')) {
            val idx = s.indexOf(arrow)
            if (idx > 0) {
                val sender = s.substring(0, idx).trim()
                val target = s.substring(idx + 1).trim()
                if (sender.isNotEmpty() && target.isNotEmpty()) return sender to target
            }
        }
        return s to null
    }

    private fun extractQuotedAuthor(quotedLine: String): String? {
        val bracket = Regex("^\\[([^\\]]+)]\\s+([^:>]{1,64})\\s*:").find(quotedLine.trim())
        if (bracket != null) return bracket.groupValues[2].trim().ifBlank { null }
        val plain = Regex("^([^:>]{1,64})\\s*:").find(quotedLine.trim())
        return plain?.groupValues?.getOrNull(1)?.trim()?.ifBlank { null }
    }

    private fun extractReplyTarget(namePartRaw: String): String? {
        val lower = namePartRaw.lowercase()
        val key = "replied to "
        val idx = lower.indexOf(key)
        if (idx >= 0) {
            val after = namePartRaw.substring(idx + key.length).trimStart()
            val target = after.split(" ").firstOrNull().orEmpty().trim()
            return target.ifEmpty { null }
        }
        val gt = namePartRaw.indexOf('>')
        if (gt in 1..40) {
            val target = namePartRaw.substring(0, gt).trim()
            if (target.isNotEmpty() && !target.contains(':')) return target
        }
        return null
    }

    private fun looksLikeReplyName(namePartRaw: String): Boolean {
        val s = namePartRaw.trim()
        if (s.isEmpty()) return false
        if (s.contains("replied to", ignoreCase = true)) return true
        if (s.contains('⇾') || s.contains('→') || s.contains("->")) return true
        return false
    }

    private fun buildReplyMarkerComponent(): Component {
        return Component.literal(".")
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x8A8D93)).withBold(true))
    }

    private fun extractQuotePrefillTarget(namePartRaw: String, fallback: String): String {
        val compact = namePartRaw.trim()
        val arrow = compact.indexOf('→')
        if (arrow in 1..80) {
            val after = compact.substring(arrow + 1).trim()
            val target = after.replace(":", "").split(" ").firstOrNull().orEmpty().trim()
            if (target.isNotEmpty()) return target
        }
        val reply = extractReplyTarget(namePartRaw)
        if (!reply.isNullOrBlank()) return reply
        return fallback
    }

    private fun sourceLabelForId(sourceId: String): String {
        return when (sourceId.lowercase()) {
            "telegram" -> "Telegram"
            "minecraft" -> "Minecraft"
            else -> "Discord"
        }
    }

    private fun buildQuoteActionComponent(senderNick: String, sourceId: String = "discord", quotedText: String? = null): Component {
        if (!config.quoteSystemEnabled) return Component.empty()
        if (senderNick.isBlank()) return Component.empty()
        val cleanNick = senderNick.replace(":", "").trim()
        if (cleanNick.isBlank()) return Component.empty()
        return Component.literal(" [q]")
            .withStyle(QuoteClickHelper.quoteButtonStyle(QuoteClickHelper.quotePrefill(cleanNick, sourceId, quotedText)))
    }

    private fun extractSenderNick(namePartRaw: String): String {
        var s = namePartRaw.replaceFirst(Regex(":$"), "").trim()
        if (s.isEmpty()) return ""
        while (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end <= 0) break
            s = s.substring(end + 1).trimStart()
        }
        val token = s.split(" ").firstOrNull().orEmpty()
        val gt = token.indexOf("->")
        if (gt > 0) return token.substring(0, gt).trim()
        for (arrow in charArrayOf('⇾', '→')) {
            val idx = token.indexOf(arrow)
            if (idx > 0) return token.substring(0, idx).trim()
        }
        return token
    }

    private fun isMyNick(nick: String): Boolean {
        if (nick.isBlank()) return false
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        val my1 = player.name.string
        val my2 = client.user.name
        val my3 = player.displayName?.string ?: ""

        if (nick.equals(my1, ignoreCase = true) || nick.equals(my2, ignoreCase = true) || (my3.isNotBlank() && nick.equals(my3, ignoreCase = true))) {
            return true
        }

        val aliases = config.myNickAliases
        if (aliases.isEmpty()) return false
        return aliases.any { it.isNotBlank() && nick.equals(it, ignoreCase = true) }
    }

    private fun parseWordHighlightRules(raw: String): List<Pair<String, Style>> {
        val parts = raw
            .split("\n", ";", ",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val res = ArrayList<Pair<String, Style>>(parts.size)
        for (p in parts) {
            val eq = p.indexOf('=')
            if (eq <= 0) continue
            val word = p.substring(0, eq).trim()
            val codes = p.substring(eq + 1).trim()
            if (word.isEmpty() || codes.isEmpty()) continue
            res.add(word.lowercase() to styleFromLegacyCodes(codes))
        }
        return res
    }

    private fun styleFromLegacyCodes(codes: String): Style {
        var style = Style.EMPTY
        var i = 0
        while (i < codes.length) {
            val ch = codes[i]
            if (ch == '§' && i + 1 < codes.length) {
                val code = codes[i + 1].lowercaseChar()
                when (code) {
                    '0' -> style = style.withColor(TextColor.fromLegacyFormat(ChatFormatting.BLACK))
                    '1' -> style = style.withColor(TextColor.fromLegacyFormat(ChatFormatting.DARK_BLUE))
                    '2' -> style = style.withColor(TextColor.fromLegacyFormat(ChatFormatting.DARK_GREEN))
                    '3' -> style = style.withColor(TextColor.fromLegacyFormat(ChatFormatting.DARK_AQUA))
                    '4' -> style = style.withColor(TextColor.fromLegacyFormat(ChatFormatting.DARK_RED))
                    '5' -> style = style.withColor(TextColor.fromLegacyFormat(ChatFormatting.DARK_PURPLE))
                    '6' -> style = style.withColor(TextColor.fromLegacyFormat(ChatFormatting.GOLD))
                    '7' -> style = style.withColor(TextColor.fromLegacyFormat(ChatFormatting.GRAY))
                    '8' -> style = style.withColor(TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY))
                    '9' -> style = style.withColor(TextColor.fromLegacyFormat(ChatFormatting.BLUE))
                    'a' -> style = style.withColor(TextColor.fromLegacyFormat(ChatFormatting.GREEN))
                    'b' -> style = style.withColor(TextColor.fromLegacyFormat(ChatFormatting.AQUA))
                    'c' -> style = style.withColor(TextColor.fromLegacyFormat(ChatFormatting.RED))
                    'd' -> style = style.withColor(TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE))
                    'e' -> style = style.withColor(TextColor.fromLegacyFormat(ChatFormatting.YELLOW))
                    'f' -> style = style.withColor(TextColor.fromLegacyFormat(ChatFormatting.WHITE))

                    'l' -> style = style.withBold(true)
                    'o' -> style = style.withItalic(true)
                    'n' -> style = style.withUnderlined(true)
                    'm' -> style = style.withStrikethrough(true)
                    'k' -> style = style.withObfuscated(true)
                    'r' -> style = Style.EMPTY
                }
                i += 2
                continue
            }
            i++
        }
        return style
    }

    private fun formatBridgeCommandMessage(message: Component, unformatted: String): Component {
        val guildCmd = compile(GUILD_PATTERN).matcher(unformatted)
        if (guildCmd.matches()) {
            val u = guildCmd.group(1)
            val b = guildCmd.group(2)
            if (!GuildChatClassifier.shouldApplyBridgeFormat(unformatted, u, b)) {
                return message
            }
        }

        val lower = unformatted.lowercase()
        val fromBridgeBot = isFromBridgeBotText(unformatted)
        val commandShapeByBot =
            fromBridgeBot && (
                lower.contains("commands") ||
                    lower.contains("online (") ||
                    lower.contains("error") ||
                    lower.contains("api") ||
                    lower.contains("executed") ||
                    lower.contains("can only") ||
                    lower.contains("must be admin")
                )

        val looksLikeCmd =
            lower.contains("'s networth:") ||
                (lower.contains("networth") && lower.contains("non-cosmetic")) ||
                lower.contains("'s level:") ||
                lower.contains("'s weight:") ||
                (lower.contains("'s") && lower.contains("is playing")) ||
                lower.contains("that command does not exist") ||
                lower.contains("commands (page") ||
                lower.contains("there are no commands available") ||
                lower.contains("there are no active parties") ||
                lower.contains("party started") ||
                lower.contains("party ended") ||
                lower.contains(", error") ||
                lower.contains(", cannot calculate") ||
                lower.contains("possible commands:") ||
                lower.contains("can only query online minecraft instances") ||
                lower.contains("there are no connected minecraft instances to query") ||
                lower.contains("command has been executed.") ||
                lower.contains("must be admin") ||
                lower.contains("can only use ingame") ||
                lower.contains("online (") ||
                lower.contains("api disabled") ||
                lower.contains("last-online")

        if (!looksLikeCmd && !commandShapeByBot) return message

        val cmdBody = guildChatBodyOrFull(unformatted)
        val (sourceComponent, cleanedText) = buildSourcePrefixAndStrip(cmdBody)
        val parsed = parseBridgeSenderAndMessage(cleanedText)
        if (parsed != null) {
            val (nick, rest) = parsed
            val isMine = isMyNick(nick)
            return Component.empty()
                .append(sourceComponent)
                .append(buildSenderNameComponent(nick, config.nameColor.toColor(), isMine))
                .append(Component.literal(": ").withColor(config.messageColor.toColor()))
                .append(buildMessageBodyComponent(rest, config.messageColor.toColor(), isMine))
                .append(buildQuoteActionComponent(extractQuotePrefillTarget(nick, nick), detectSourceId(cmdBody), rest))
        }

        return Component.empty()
            .append(
                Component.literal("[CMD] ")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA00)).withBold(true))
            )
            .append(
                Component.literal(cleanedText)
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFAA)))
            )
    }

    private enum class ChatChannel {
        PARTY, GUILD, PRIVATE, UNKNOWN
    }

}
