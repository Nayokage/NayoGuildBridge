package com.nayoguildbridge.util

import com.nayoguildbridge.config.NgbConfig

object BridgeOutboundFilter {
    private val STRIP_FORMATTING = Regex("§\\w")
    private val GUILD_LINE = Regex(
        """^(?:G|Guild|Officer)\s*>\s*(?:\[(?:\S+?)]\s*)?(\w{1,16})(?:\s*\[(?:\S+?)])?\s*:\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val SOURCE_TAG = Regex(
        """^\[(?:Discord|Telegram|Minecraft|TG|DS|DC|MC|discord|telegram|minecraft)]\s""",
        RegexOption.IGNORE_CASE
    )
    private val ARROW_REPLY = Regex("""^\.?[^→>]{1,64}[→>]\s*[^:]{1,64}:\s""")
    private val BRIDGE_RANK = Regex("""\[(?:Бридж|Bridge)]""", RegexOption.IGNORE_CASE)

    fun isBridgeRelayPayload(text: String): Boolean {
        val s = text.trim()
        if (s.isEmpty()) return false
        if (SOURCE_TAG.containsMatchIn(s)) return true
        if (ARROW_REPLY.containsMatchIn(s)) return true
        if (s.startsWith("[QUOTE]", ignoreCase = true)) return true
        return false
    }

    fun parseGuildLine(raw: String): Pair<String, String>? {
        val unformatted = STRIP_FORMATTING.replace(raw, "").trim()
        val m = GUILD_LINE.find(unformatted) ?: return null
        return m.groupValues[1] to m.groupValues[2].trim()
    }

    fun isBridgeBotSpeaker(speaker: String): Boolean {
        val lower = speaker.trim().lowercase()
        if (lower.isEmpty()) return false
        return NgbConfig.config.bridgeBotNames.any { it.isNotBlank() && lower == it.trim().lowercase() }
    }

    fun shouldForwardGuildLine(rawGuildLine: String): Boolean {
        val parsed = parseGuildLine(rawGuildLine) ?: return false
        val (speaker, body) = parsed
        if (isBridgeBotSpeaker(speaker)) return false
        if (BRIDGE_RANK.containsMatchIn(rawGuildLine)) return false
        if (isBridgeRelayPayload(body)) return false
        return true
    }

    fun stripSourcePrefixForDedup(text: String): String {
        val stripped = BridgeTextUtil.stripBridgeFormatting(text)
        return stripped.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}
