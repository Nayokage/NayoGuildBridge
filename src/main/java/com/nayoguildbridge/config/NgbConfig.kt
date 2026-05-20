package com.nayoguildbridge.config

import com.nayoguildbridge.NayoGuildBridge.logger
import java.io.File
import com.google.gson.GsonBuilder

data class Config(
    var bridgeEnabled: Boolean = true,
    var hideBotName: Boolean = false,
    var remoteBridgeEnabled: Boolean = false,
    var remoteBridgeUrl: String = "https://bridgeapi.fiokem.cc",
    var remoteBridgeUrlBackup: String = "https://apisitetest123321.vercel.app",
    var remoteBridgeChannelId: String = "default",
    var remoteBridgePollMs: Int = 1500,
    var bridgeBotNames: List<String> = listOf(
        "etobridge",
        "koorikage",
        "mothikh",
        "tenokage",
        "uzbekf3ndi",
        "gem_zz",
        "Bakr__X"
    ),
    var nameColor: String = "#8F99FF",
    var messageColor: String = "#C1C3C7",

    // Ет отвечает за базовые фильтры и формат
    var blockList: List<String> = emptyList(),
    var nickHighlightEnabled: Boolean = true,
    var nickHighlightColor: String = "#FFFF00",
    var guildBridgeFormatEnabled: Boolean = true,
    var bridgeCommandFormatEnabled: Boolean = true,

    // Ет отвечает за цвет ника отправителя
    var senderNickColorEnabled: Boolean = false,
    var senderNickColor: String = "#55FFFF",
    // Ет отвечает за §-формат ника (вместо обычного цвета)
    var senderNickLegacyEnabled: Boolean = false,
    var senderNickLegacyCodes: String = "§4",
    // Ет отвечает за стиль только для моего ника
    var senderNickStyleOnlyMine: Boolean = false,
    // Ет отвечает за доп алиасы моего ника
    var myNickAliases: List<String> = emptyList(),

    // Ет отвечает за маркеры источника (ТГ/Дискорд/Майн)
    var telegramMarker: String = "[TG]",
    var minecraftMarker: String = ".",
    var telegramLabel: String = "[Telegram] ",
    var discordLabel: String = "[Discord] ",
    var minecraftLabel: String = "[Minecraft] ",
    var telegramLabelColor: String = "#55FFFF",
    var discordLabelColor: String = "#5555FF",
    var minecraftLabelColor: String = "#55FF55",

    // Ет отвечает за подсветку слов по правилам
    var wordHighlightEnabled: Boolean = false,
    var wordHighlightRules: String = "",
    // Ет отвечает за подсветку слов только для моего ника
    var wordHighlightOnlyMine: Boolean = false
)

object NgbConfig {
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    private val file = File("config/nayoguildbridge.json")
    private val legacyFile = File("config/chatbridge.json")

    var config = Config()

    fun save() {

        try {
            file.parentFile?.let { parent ->
                if (!parent.exists()) {
                    parent.mkdirs()
                }
            }
            val json = gson.toJson(config)
            file.writeText(json)
        } catch (e: Exception) {
            logger.error("Encountered error whilst trying to save config JSON.", e)
        }
    }

    fun load() {
        val source = when {
            file.exists() -> file
            legacyFile.exists() -> legacyFile
            else -> {
                logger.info("Config file not found, new created.")
                save()
                return
            }
        }

        val json = source.readText()
        config = gson.fromJson(json, Config::class.java) ?: Config()
        if (source == legacyFile) {
            logger.info("Migrated config from chatbridge.json to nayoguildbridge.json")
            save()
        }
    }
}