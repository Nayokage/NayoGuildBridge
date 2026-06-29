package com.nayoguildbridge.util

import com.nayoguildbridge.config.NgbConfig

object GuildChatClassifier {
    private val STRIP_FMT = Regex("§.")
    private val RANK = Regex("""\[([^\]]+)]""")
    private val BRIDGE_RANK_NAMES = setOf("бридж", "bridge", "мост", "relay")

    fun hasBridgeRankOnLine(rawLine: String, speaker: String): Boolean {
        if (speaker.isBlank()) return false
        val line = STRIP_FMT.replace(rawLine, "")
        val colon = line.indexOf(':')
        if (colon < 0) return false
        val header = line.substring(0, colon)
        val speakerInHeader = Regex(
            """(?i)(?<![A-Za-z0-9_])${Regex.escape(speaker)}(?![A-Za-z0-9_])"""
        ).containsMatchIn(header)
        if (!speakerInHeader) return false
        return RANK.findAll(header).any { isBridgeRankName(it.groupValues[1]) }
    }

    private fun isBridgeRankName(raw: String): Boolean {
        val normalized = raw.trim().lowercase().replace(Regex("\\s+"), " ")
        return normalized in BRIDGE_RANK_NAMES
    }

    fun looksLikeNestedRelayBody(body: String): Boolean {
        if (BridgeOutboundFilter.isBridgeRelayPayload(body)) return true
        val s = body.trim()
        val colon = s.indexOf(':')
        if (colon !in 1..24) return false
        val left = s.substring(0, colon).trim()
        return left.isNotEmpty() &&
            !left.contains(' ') &&
            left.length <= 16 &&
            left.all { it.isLetterOrDigit() || it == '_' || it == '-' }
    }

    fun looksLikeBridgedPayload(body: String, speakerIsBridgeBot: Boolean): Boolean {
        val s = body.trimStart()
        if (s.isEmpty()) return false

        if (BridgeOutboundFilter.isBridgeRelayPayload(s)) return true
        if (BridgeSourceTags.looksLikeBridgedPayloadStart(s)) return true

        val botListConfigured = NgbConfig.config.bridgeBotNames.any { it.isNotBlank() }
        if (botListConfigured && !speakerIsBridgeBot) return false

        val idx = s.indexOf(':')
        if (idx in 2..24) {
            val left = s.substring(0, idx)
            if (!left.contains(' ') && left.all { it.isLetterOrDigit() || it == '_' || it == '-' }) return true
        }
        return false
    }

    private fun isHypixelGuildGameLine(rawLine: String): Boolean {
        val line = STRIP_FMT.replace(rawLine, "")
        return line.contains("Guild >", ignoreCase = true) ||
            line.contains("Officer >", ignoreCase = true) ||
            Regex("""^G >""", RegexOption.IGNORE_CASE).containsMatchIn(line)
    }

    fun looksLikeIncomingRelayBody(body: String, speakerIsBridgeBot: Boolean): Boolean =
        looksLikeBridgedPayload(body, speakerIsBridgeBot) || looksLikeNestedRelayBody(body)

    fun shouldApplyBridgeFormat(rawLine: String, speaker: String, body: String): Boolean {
        val inBotList = BridgeOutboundFilter.isBridgeBotSpeaker(speaker)
        val bridgeRankLine = hasBridgeRankOnLine(rawLine, speaker)
        val relaySpeaker = inBotList || bridgeRankLine
        if (looksLikeBridgedPayload(body, relaySpeaker)) return true
        if (bridgeRankLine && looksLikeNestedRelayBody(body)) return true
        if (!isHypixelGuildGameLine(rawLine) && looksLikeIncomingRelayBody(body, inBotList)) return true
        return false
    }
}
