package com.nayoguildbridge.util



object BridgeSourceTags {

    const val TELEGRAM_MARKER = "[TG]"

    const val MINECRAFT_MARKER = "."



    const val DISCORD_DISPLAY = "[Dis] "

    const val TELEGRAM_DISPLAY = "[Telegram] "

    const val MINECRAFT_DISPLAY = "[Minecraft] "



    const val BRACKET_TAG_PATTERN =

        "Dis|Discord|Telegram|Minecraft|TG|DS|DC|MC|dis|discord|telegram|minecraft"



    data class McInstanceLine(

        val instance: String,

        val guildTag: String? = null,

        val rest: String,

    )



    private val MC_INSTANCE_LINE =

        Regex("""^\[Minecraft\|([^|\]]+)(?:\|([^\]]+))?\]\s+(.+)$""", RegexOption.IGNORE_CASE)



    fun parseMcInstanceLine(text: String): McInstanceLine? {

        val m = MC_INSTANCE_LINE.find(text.trimStart()) ?: return null

        val guildTag = m.groupValues[2].trim().ifBlank { null }

        return McInstanceLine(

            instance = m.groupValues[1].trim(),

            guildTag = guildTag,

            rest = m.groupValues[3].trim(),

        )

    }



    fun mcInstanceDisplayLabel(instance: String?, guildTag: String? = null): String {

        val name = instance?.trim().orEmpty()

        val tag = guildTag?.trim().orEmpty()

        return when {

            name.isNotBlank() && tag.isNotBlank() -> "[Minecraft · $name · $tag] "

            name.isNotBlank() -> "[Minecraft · $name] "

            tag.isNotBlank() -> "[Minecraft · $tag] "

            else -> MINECRAFT_DISPLAY

        }

    }



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

        if (MC_INSTANCE_LINE.containsMatchIn(s)) return true

        return false

    }

}


