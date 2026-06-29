package com.nayoguildbridge.util

object BridgeSourceTags {
    const val TELEGRAM_MARKER = "[TG]"
    const val MINECRAFT_MARKER = "."

    const val DISCORD_DISPLAY = "[Dis] "
    const val TELEGRAM_DISPLAY = "[Telegram] "
    const val MINECRAFT_DISPLAY = "[Minecraft] "

    const val BRACKET_TAG_PATTERN =
        "Dis|Discord|Telegram|Minecraft|TG|DS|DC|MC|dis|discord|telegram|minecraft"

    fun normalizeSourceId(raw: String): String = when (raw.trim().lowercase()) {
        "telegram", "tg" -> "telegram"
        "minecraft", "mc" -> "minecraft"
        "discord", "ds", "dc", "dis" -> "discord"
        else -> raw.trim()
    }

    fun isDiscordSource(sourceId: String): Boolean =
        normalizeSourceId(sourceId) == "discord"

    fun displayLabel(sourceId: String): String = when (normalizeSourceId(sourceId)) {
        "telegram" -> TELEGRAM_DISPLAY
        "minecraft" -> MINECRAFT_DISPLAY
        "discord" -> DISCORD_DISPLAY
        else -> if (sourceId.isNotBlank()) "[$sourceId] " else MINECRAFT_DISPLAY
    }

    fun looksLikeBridgedPayloadStart(text: String): Boolean {
        val s = text.trimStart()
        if (s.isEmpty()) return false
        if (s.startsWith(TELEGRAM_MARKER)) return true
        if (s.startsWith(MINECRAFT_MARKER)) return true
        if (s.startsWith("[DC]", ignoreCase = true)) return true
        if (s.startsWith(DISCORD_DISPLAY.trimEnd())) return true
        if (s.startsWith(TELEGRAM_DISPLAY.trimStart())) return true
        if (s.startsWith(MINECRAFT_DISPLAY.trimStart())) return true
        return false
    }
}
