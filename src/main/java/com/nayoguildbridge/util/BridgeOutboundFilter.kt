package com.nayoguildbridge.util

import com.nayoguildbridge.config.NgbConfig

object BridgeOutboundFilter {
    private val STRIP_FORMATTING = Regex("§\\w")
    private val GUILD_LINE = Regex(
        """^(?:G|Guild|Officer)\s*>\s*(?:\[[^\]]+]\s*)*([A-Za-z0-9_]{1,16})(?:\s*\[[^\]]+])*\s*:\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val SOURCE_TAG_AT_START = Regex(
        """^\[(?:${BridgeSourceTags.BRACKET_TAG_PATTERN})]\s""",
        RegexOption.IGNORE_CASE
    )
    private val SOURCE_LINE_FRAGMENT = Regex(
        """\[(?:${BridgeSourceTags.BRACKET_TAG_PATTERN})]\s+[^:\n]{1,96}:\s*""",
        RegexOption.IGNORE_CASE
    )
    private val ARROW_REPLY = Regex("""^\.?[^→>]{1,64}[→>]\s*[^:]{1,64}:\s""")
    private val BRIDGE_RANK = Regex("""\[(?:Бридж|Bridge|Мост|Relay)\]""", RegexOption.IGNORE_CASE)

    fun isBridgeRelayPayload(text: String): Boolean {
        val s = text.trim()
        if (s.isEmpty()) return false
        if (hasBridgeSourceMarker(s)) return true
        if (ARROW_REPLY.containsMatchIn(s)) return true
        if (s.startsWith("[QUOTE]", ignoreCase = true)) return true
        return false
    }

    private fun hasBridgeSourceMarker(text: String): Boolean {
        val s = text.trim()
        return SOURCE_TAG_AT_START.containsMatchIn(s) ||
            SOURCE_LINE_FRAGMENT.containsMatchIn(s)
    }

    private fun stripMinecraftFormatting(raw: String): String =
        STRIP_FORMATTING.replace(raw, "").trim()

    fun parseGuildLine(raw: String): Pair<String, String>? {
        val unformatted = stripMinecraftFormatting(raw)
        val m = GUILD_LINE.find(unformatted) ?: return null
        return m.groupValues[1] to m.groupValues[2].trim()
    }

    fun isBridgeBotSpeaker(speaker: String): Boolean {
        val lower = speaker.trim().lowercase()
        if (lower.isEmpty()) return false
        return NgbConfig.config.bridgeBotNames.any { it.isNotBlank() && lower == it.trim().lowercase() }
    }

    fun shouldForwardGuildLine(rawGuildLine: String): Boolean {
        val unformatted = stripMinecraftFormatting(rawGuildLine)
        if (hasBridgeSourceMarker(unformatted)) return false

        val parsed = parseGuildLine(unformatted) ?: return false
        val (speaker, body) = parsed
        if (isBridgeBotSpeaker(speaker)) return false
        if (BRIDGE_RANK.containsMatchIn(unformatted)) return false
        if (isBridgeRelayPayload(body)) return false
        return true
    }

    fun stripSourcePrefixForDedup(text: String): String {
        val stripped = BridgeTextUtil.stripBridgeFormatting(text)
        return stripped.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}
