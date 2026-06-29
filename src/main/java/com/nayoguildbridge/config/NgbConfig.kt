package com.nayoguildbridge.config

import com.nayoguildbridge.NayoGuildBridge.logger
import com.nayoguildbridge.guard.EnvironmentGuard
import com.nayoguildbridge.ims.ImsBridgeClient
import java.io.File
import com.google.gson.GsonBuilder

data class Config(
    var bridgeEnabled: Boolean = true,
    var quoteSystemEnabled: Boolean = true,
    var quoteApiUrl: String = "",
    var quoteApiUrlBackup: String = "",
    var hideBotName: Boolean = true,
    var remoteBridgeEnabled: Boolean = false,
    var remoteBridgeSendQuotes: Boolean = true,
    var remoteBridgeUrl: String = "",
    var remoteBridgeUrlBackup: String = "",
    var remoteBridgeChannelId: String = "default",
    var remoteBridgePollMs: Int = 1500,

    var bridgeBackend: String = "bridge-site",

    var bridgePollEnabled: Boolean = true,
    var bridgePollDisplayInChat: Boolean = false,
    var bridgePollOnlyWhenWsOffline: Boolean = true,
    var bridgePollChannelId: String = "default",
    var bridgePollMs: Int = 1500,

    var platformBridgeEnabled: Boolean = false,
    var platformApiUrl: String = "http://127.0.0.1:4000",
    var platformWsUrl: String = "ws://127.0.0.1:4000/ws",
    var platformInstanceToken: String = "",
    var platformReconnectMs: Int = 5000,

    var environmentGuardEnabled: Boolean = false,
    var requireHypixel: Boolean = true,
    var hypixelGuildName: String = "",

    var menuLanguage: String = "auto",

    var configVersion: Int = 0,

    var imsBridgeEnabled: Boolean = false,
    var imsAuthByNick: Boolean = true,
    var bridgeKey: String = "",
    var imsGuildSlug: String = "",
    var imsWsUrl: String = "ws://127.0.0.1:4000/ws",
    var imsBridgeReceiveEnabled: Boolean = true,
    var imsCombinedBridgeEnabled: Boolean = false,
    var imsCombinedBridgeChatEnabled: Boolean = false,
    var imsPartyBridgeEnabled: Boolean = false,
    var imsWebOnlyMode: Boolean = false,
    var imsQuoteOverWsEnabled: Boolean = false,
    var imsGuildTag: String = "",
    var imsGuildColor: String = "§a",
    var imsBridgePrefix: String = "§aGui§ald > ",
    var imsBridgeMessageColor: String = "§f",
    var imsCombinedPrefix: String = "§dCB > ",
    var imsCombinedMessageColor: String = "§f",
    var imsIgnorePlayers: List<String> = emptyList(),
    var imsIgnoreOrigins: List<String> = emptyList(),
    var collapseChat: Boolean = true,
    var unlimitedChat: Boolean = true,
    var emojiShortcodesEnabled: Boolean = true,
    var linkPreviewEnabled: Boolean = true,
    var imagePreviewEnabled: Boolean = true,
    var updateCheckEnabled: Boolean = true,
    var bridgeBotFormatEnabled: Boolean = false,
    var copyChatEnabled: Boolean = true,
    var persistentChatEnabled: Boolean = false,

    var bridgeBotNames: List<String> = listOf(
        "Electoral_Goon",
        "etobridge"
    ),
    var prefixColor: String = "#55FF55",
    var nameColor: String = "#8F99FF",
    var messageColor: String = "#C1C3C7",
    var officerPrefixColor: String = "#FF5555",

    var blockList: List<String> = emptyList(),
    var nickHighlightEnabled: Boolean = true,
    var nickHighlightColor: String = "#FFFF00",
    var guildBridgeFormatEnabled: Boolean = true,
    var bridgeCommandFormatEnabled: Boolean = true,

    var senderNickColorEnabled: Boolean = false,
    var senderNickColor: String = "#55FFFF",
    var senderNickLegacyEnabled: Boolean = false,
    var senderNickLegacyCodes: String = "§4",
    var senderNickStyleOnlyMine: Boolean = false,
    var myNickAliases: List<String> = emptyList(),

    var telegramLabelColor: String = "#55FFFF",
    var discordLabelColor: String = "#5555FF",
    var minecraftLabelColor: String = "#55FF55",

    var wordHighlightEnabled: Boolean = false,
    var wordHighlightRules: String = "",
    var wordHighlightOnlyMine: Boolean = false
)

object NgbConfig {
    private const val CURRENT_CONFIG_VERSION = 7

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    private val file = File("config/nayoguildbridge.json")
    private val legacyFile = File("config/chatbridge.json")

    var config = Config()

    fun save() {
        BridgeEndpoints.applyTo(config)
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
                config = Config()
                BridgeEndpoints.applyTo(config)
                logger.info("Config file not found, new created.")
                save()
                return
            }
        }

        val json = source.readText()
        config = gson.fromJson(json, Config::class.java) ?: Config()
        BridgeEndpoints.applyTo(config)
        migrateConfig()
        syncTransportWithFeatures()
        applyBackendDefaults()
        save()
        if (source == legacyFile) {
            logger.info("Migrated config from chatbridge.json to nayoguildbridge.json")
        }
        EnvironmentGuard.resetAfterConfigLoad()
    }

    private fun migrateConfig() {
        if (config.configVersion >= CURRENT_CONFIG_VERSION) return
        logger.info("[NayoGuildBridge] Обновление конфига до v$CURRENT_CONFIG_VERSION")
        if (config.configVersion < 3) {
            config.environmentGuardEnabled = false
            config.requireHypixel = false
            config.hypixelGuildName = ""
            config.bridgeBackend = "bridge-site"
        }
        if (config.configVersion < 4) {
            config.remoteBridgeEnabled = false
            config.bridgePollEnabled = true
            config.bridgePollDisplayInChat = false
            config.bridgePollOnlyWhenWsOffline = true
        }
        if (config.configVersion < 5) {
            val knownBots = setOf("electoral_goon", "etobridge")
            config.bridgeBotNames = config.bridgeBotNames.filter { name ->
                val n = name.trim()
                n.isNotBlank() && (
                    knownBots.contains(n.lowercase()) ||
                        n.contains("bridge", ignoreCase = true) ||
                        n.contains("бридж", ignoreCase = true)
                    )
            }.ifEmpty { listOf("Electoral_Goon") }
        }
        if (config.configVersion < 6) {
            if (!config.bridgeBotNames.any { it.equals("Electoral_Goon", ignoreCase = true) }) {
                config.bridgeBotNames = config.bridgeBotNames + "Electoral_Goon"
            }
            config.quoteSystemEnabled = true
            config.bridgeEnabled = true
            config.bridgeBotFormatEnabled = false
        }
        if (config.configVersion < 7) {
            // Bridge source tags are fixed in code (BridgeSourceTags); only label colors remain in config.
        }
        config.configVersion = CURRENT_CONFIG_VERSION
    }

    private fun syncTransportWithFeatures() {
        if (config.imsWebOnlyMode) {
            config.imsBridgeEnabled = true
        }
    }

    fun applyWorkingDefaults() {
        config.environmentGuardEnabled = false
        config.requireHypixel = false
        config.hypixelGuildName = ""
        config.imsBridgeEnabled = false
        config.imsCombinedBridgeEnabled = false
        config.imsCombinedBridgeChatEnabled = false
        config.imsQuoteOverWsEnabled = false
        config.imsWebOnlyMode = false
        config.remoteBridgeEnabled = false
        config.bridgePollEnabled = true
        config.bridgePollDisplayInChat = false
        config.bridgePollOnlyWhenWsOffline = true
        config.quoteSystemEnabled = true
        config.bridgeEnabled = true
        config.bridgeBotNames = listOf("Electoral_Goon", "etobridge")
        BridgeEndpoints.applyTo(config)
        config.configVersion = CURRENT_CONFIG_VERSION
        save()
        EnvironmentGuard.resetAfterConfigLoad()
        ImsBridgeClient.disconnect()
    }

    private fun applyBackendDefaults() {
        if (config.bridgeBackend.equals("bridge-site", ignoreCase = true)) {
            if (!config.bridgePollEnabled) config.bridgePollEnabled = true
        }
    }
}
