package com.nayoguildbridge.util

object BridgeTextUtil {
    private val sourceLine = Regex("""^\[(?:Discord|Telegram|Minecraft|TG|DS|MC|discord|telegram|minecraft)]\s+([^:]{1,64}):\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val bracketNick = Regex("""^\[([^\]]+)]\s+([^:]{1,64}):\s*(.+)$""")
    private val trailingQ = Regex("""\s*\[q]\s*$""", RegexOption.IGNORE_CASE)

    fun normalizeSourceTag(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return when (raw.trim().lowercase()) {
            "tg", "telegram" -> "Telegram"
            "ds", "discord" -> "Discord"
            "mc", "minecraft" -> "Minecraft"
            else -> raw.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    fun stripBridgeFormatting(text: String): String {
        var s = text.trim()
        s = trailingQ.replace(s, "").trim()
        if (s.startsWith(".")) s = s.removePrefix(".").trimStart()

        sourceLine.find(s)?.let { m ->
            return m.groupValues[2].trim()
        }
        bracketNick.find(s)?.let { m ->
            return m.groupValues[2].trim()
        }

        val arrow = Regex("""^([^→>]{1,64})[→>]\s*([^:]{1,64}):\s*(.+)$""").find(s)
        if (arrow != null) {
            return arrow.groupValues[3].trim()
        }

        val colon = s.indexOf(':')
        if (colon in 1..40) {
            val left = s.substring(0, colon).trim()
            val right = s.substring(colon + 1).trim()
            if (!left.contains(' ') && right.isNotEmpty()) return right
        }
        return s
    }
}
