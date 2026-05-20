package com.nayoguildbridge

import com.nayoguildbridge.config.NgbConfig
import com.nayoguildbridge.config.NgbConfig.config
import com.nayoguildbridge.remote.RemoteBridgeApi
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
    const val GUILD_PATTERN = ("^(?:G|Guild) > (?:\\[(?:\\S+?)\\] )?(\\w+)(?: \\[(?:\\S+?)\\])?: ?(.+)$")
    const val BRIDGE_PATTERN =
        ("^ *((?:.+?)(?: attached an? \\w+(?::|$)| replied to .+ with an? \\w+(?::|$)| replied to .+?(?::|$)|:))(?:(?: (.*)?$)|$)")
    private val STRIP_FORMATTING = compile("§\\w")

    override fun onInitialize() {
        NgbConfig.load()
        ClientReceiveMessageEvents.MODIFY_GAME.register(::onModify)
    }


    private fun onModify(message: Component, actionBar: Boolean): Component {
        if (actionBar) return message
        if (!config.bridgeEnabled) return message

        val unformatted = STRIP_FORMATTING.matcher(message.string).replaceAll("")

        val channel = when(unformatted.split(" ")[0]) {
            "From" -> ChatChannel.PRIVATE
            "Party" -> ChatChannel.PARTY
            "Guild" -> ChatChannel.GUILD
            "G" -> ChatChannel.GUILD
            else -> ChatChannel.UNKNOWN
        }

        // Ет отвечает за формат ответов команд бриджа
        if (config.bridgeCommandFormatEnabled) {
            val maybeCmd = formatBridgeCommandMessage(message, unformatted)
            if (maybeCmd !== message) {
                return maybeCmd
            }
        }

        if (channel == ChatChannel.GUILD && config.guildBridgeFormatEnabled) {
            val match = compile(GUILD_PATTERN).matcher(unformatted)
            if (!match.matches()) return message

            val username = match.group(1)
            var text = match.group(2)
            val fromKnownBridgeBot = isBridgeBotNick(username)

            // Ет отвечает за проверку что сообщение реально от бриджа
            if (!looksLikeBridgedPayload(text, fromKnownBridgeBot) && !fromKnownBridgeBot) {
                return message
            }

            // Ет отвечает за блоклист
            if (shouldBlock(text)) {
                return Component.empty()
            }

            // Ет отвечает за замену маркера на источник (Дискорд/ТГ/Майн)
            val (sourceComponent, cleanedText) = buildSourcePrefixAndStrip(text)
            text = cleanedText

            val bridgeMatcher = compile(BRIDGE_PATTERN).matcher(text)

            val (name, msg) =
                if (bridgeMatcher.find()) {
                    bridgeMatcher.group(1) to (bridgeMatcher.group(2) ?: "")
                } else {
                    parseBridgeSenderAndMessage(text) ?: if (config.hideBotName) text to "" else username to text
                }

            val strippedMsg = msg.replaceFirst(Regex("^: "), "")
            val senderNick = extractSenderNick(name)
            val isMine = isMyNick(senderNick)

            // Ет отвечает за префикс источника
            val prefixComponent = sourceComponent
            val senderComponentBase = buildSenderNameComponent(if (msg.isEmpty()) text else name, config.nameColor.toColor(), isMine)
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

            val formatted = Component.empty()
                .append(prefixComponent)
                .append(senderComponent)
                .append(
                    Component.literal(if (msg.isEmpty()) "" else ": ")
                        .withColor(config.messageColor.toColor())
                )
                .append(
                    if (msg.isEmpty()) Component.empty()
                    else buildMessageBodyComponent(strippedMsg, config.messageColor.toColor(), isMine)
                )

            RemoteBridgeApi.enqueueIngest(
                kind = "guild",
                author = senderNick,
                text = strippedMsg.ifEmpty { text },
                source = detectSourceId(match.group(2))
            )

            return formatted
        }
        return message
    }

    // Ет отвечает за проверку формы bridged-сообщения
    private fun looksLikeBridgedPayload(body: String, speakerIsBridgeBot: Boolean): Boolean {
        val s = body.trimStart()
        if (s.isEmpty()) return false

        val botListConfigured = config.bridgeBotNames.isNotEmpty()
        if (botListConfigured && !speakerIsBridgeBot) {
            return false
        }

        if (config.telegramMarker.isNotEmpty() && s.startsWith(config.telegramMarker)) return true
        if (config.minecraftMarker.isNotEmpty() && s.startsWith(config.minecraftMarker)) return true

        val tgLabel = config.telegramLabel.trimStart()
        val dcLabel = config.discordLabel.trimStart()
        val mcLabel = config.minecraftLabel.trimStart()
        if (tgLabel.isNotEmpty() && s.startsWith(tgLabel)) return true
        if (dcLabel.isNotEmpty() && s.startsWith(dcLabel)) return true
        if (mcLabel.isNotEmpty() && s.startsWith(mcLabel)) return true

        val idx = s.indexOf(':')
        if (idx in 2..24) {
            val left = s.substring(0, idx)
            if (!left.contains(' ') && left.all { it.isLetterOrDigit() || it == '_' || it == '-' }) return true
        }
        return false
    }

    // Ет отвечает за получение тела сообщения после Guild >
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

    private fun shouldBlock(text: String): Boolean {
        val list = config.blockList
        if (list.isEmpty()) return false
        val lower = text.lowercase()
        return list.any { it.isNotBlank() && lower.contains(it.lowercase()) }
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
        val tgMarker = config.telegramMarker
        val mcMarker = config.minecraftMarker

        var body = raw
        val (labelText, labelColor) = when {
            tgMarker.isNotEmpty() && body.startsWith(tgMarker) -> {
                body = body.removePrefix(tgMarker).trimStart()
                config.telegramLabel to config.telegramLabelColor
            }

            mcMarker.isNotEmpty() && body.startsWith(mcMarker) -> {
                body = body.removePrefix(mcMarker).trimStart()
                config.minecraftLabel to config.minecraftLabelColor
            }

            else -> config.discordLabel to config.discordLabelColor
        }

        val label = Component.literal(labelText)
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Integer.decode(labelColor))))

        return label to body
    }

    private fun detectSourceId(rawBody: String): String {
        val body = rawBody.trimStart()
        return when {
            config.telegramMarker.isNotEmpty() && body.startsWith(config.telegramMarker) -> "telegram"
            config.minecraftMarker.isNotEmpty() && body.startsWith(config.minecraftMarker) -> "minecraft"
            else -> "discord"
        }
    }

    private fun isBridgeBotNick(nick: String): Boolean {
        val list = config.bridgeBotNames
        if (list.isEmpty()) return false
        return list.any { it.isNotBlank() && nick.equals(it, ignoreCase = true) }
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

        // Ет отвечает за формат Nick > message
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

        // Ет отвечает за формат Nick: message
        val colon = s.indexOf(':')
        if (colon in 1..40) {
            val left = s.substring(0, colon).trim()
            val right = s.substring(colon + 1).trimStart()
            if (right.isNotEmpty() && left.isNotEmpty()) return left to right
        }
        return null
    }

    private fun buildSenderNameComponent(namePartRaw: String, nameColor: Int, isMine: Boolean): Component {
        // Ет отвечает за покраску имени отправителя
        var s = namePartRaw.replaceFirst(Regex(":$"), "").trim()
        if (s.isEmpty()) return Component.empty()

        val allowStyle = !config.senderNickStyleOnlyMine || isMine
        val senderStyle: Style? = when {
            !allowStyle -> null
            config.senderNickLegacyEnabled -> styleFromLegacyCodes(config.senderNickLegacyCodes)
            config.senderNickColorEnabled -> Style.EMPTY.withColor(TextColor.fromRgb(Integer.decode(config.senderNickColor)))
            else -> null
        }

        // Ет отвечает за сохранение тегов [LVL]/рангов
        val prefix = StringBuilder()
        while (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end <= 0) break
            prefix.append(s.substring(0, end + 1))
            s = s.substring(end + 1).trimStart()
            if (s.isNotEmpty() && !s.startsWith("[")) prefix.append(" ")
        }

        // Ет отвечает за выделение ника из начала строки
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

        return out
    }

    private fun buildMessageWithWordHighlights(text: String, baseColor: Int, isMine: Boolean): Component {
        val baseStyle = Style.EMPTY.withColor(TextColor.fromRgb(baseColor))
        if (!config.wordHighlightEnabled) {
            return Component.literal(text).withStyle(baseStyle)
        }
        if (config.wordHighlightOnlyMine && !isMine) {
            return Component.literal(text).withStyle(baseStyle)
        }

        val rules = parseWordHighlightRules(config.wordHighlightRules)
        if (rules.isEmpty()) {
            return Component.literal(text).withStyle(baseStyle)
        }

        val lower = text.lowercase()
        val out: MutableComponent = Component.empty()

        var i = 0
        while (i < text.length) {
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
                out.append(Component.literal(text.substring(i)).withStyle(baseStyle))
                break
            }

            if (bestStart > i) {
                out.append(Component.literal(text.substring(i, bestStart)).withStyle(baseStyle))
            }
            out.append(Component.literal(text.substring(bestStart, bestEnd)).withStyle(bestStyle))
            i = bestEnd
        }

        return out
    }

    private fun buildMessageBodyComponent(text: String, baseColor: Int, isMine: Boolean): Component {
        return buildMessageWithWordHighlights(text, baseColor, isMine)
    }

    private fun extractSenderNick(namePartRaw: String): String {
        var s = namePartRaw.replaceFirst(Regex(":$"), "").trim()
        if (s.isEmpty()) return ""
        // Ет отвечает за очистку тегов перед ником
        while (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end <= 0) break
            s = s.substring(end + 1).trimStart()
        }
        val token = s.split(" ").firstOrNull().orEmpty()
        val arrow = token.indexOf('→')
        return if (arrow > 0) token.substring(0, arrow) else token
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
        // Ет отвечает за разбор правил подсветки слов
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
        // Ет отвечает за определение командного ответа бриджа
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
        }

        val tag: MutableComponent = Component.empty()
            .append(Component.literal("[")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA00))))
            .append(Component.literal("BridgeCMD")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFD700)).withBold(true)))
            .append(Component.literal("] ")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA00))))
            .append(Component.literal(cleanedText).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFAA))))

        return tag
    }

    private enum class ChatChannel {
        PARTY, GUILD, PRIVATE, UNKNOWN
    }

}