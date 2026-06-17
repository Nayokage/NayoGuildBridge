package com.nayoguildbridge.config

import com.nayoguildbridge.config.NgbConfig.config
import com.nayoguildbridge.config.NgbLang
import dev.isxander.yacl3.api.ButtonOption
import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.OptionGroup
import dev.isxander.yacl3.api.YetAnotherConfigLib
import dev.isxander.yacl3.api.controller.StringControllerBuilder
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.Util
import net.minecraft.network.chat.Component

object NgbConfigManager {
    private enum class UiCategory {
        GENERAL, API, CHAT, COMBAT, RENDER, MISC
    }

    private data class FeatureSpec(
        val key: String,
        val tooltipKey: String? = null,
        val category: UiCategory? = null,
        val group: String? = null,
        val option: Option<*>
    )

    fun build(parent: Screen?): Screen {
        val specs = buildFeatureSpecs()
        val groupedByCategory = specs.groupBy { it.category ?: inferCategory(it) }
        val categories = mutableListOf<ConfigCategory>()

        listOf(UiCategory.GENERAL, UiCategory.API, UiCategory.CHAT, UiCategory.COMBAT, UiCategory.RENDER, UiCategory.MISC)
            .forEach { category ->
                val features = groupedByCategory[category].orEmpty()
                if (features.isEmpty()) return@forEach
                categories += buildFeatureCategory(category, features)
            }

        categories += buildAboutCategory()

        val builder = YetAnotherConfigLib.createBuilder()
            .title(NgbLang.component("title.ngb.config"))
            .save(NgbConfig::save)
        categories.forEach(builder::category)

        return builder.build()
            .generateScreen(parent)
    }

    private fun buildFeatureSpecs(): List<FeatureSpec> {
        return listOf(
            FeatureSpec(
                "entry.ngb.menuLanguage",
                "tooltip.ngb.menuLanguage",
                UiCategory.GENERAL,
                "core",
                strOpt("entry.ngb.menuLanguage", "tooltip.ngb.menuLanguage", { config.menuLanguage }) {
                    val v = it.trim().lowercase()
                    config.menuLanguage = when (v) {
                        "ru", "en", "auto" -> v
                        "русский", "russian" -> "ru"
                        "english", "английский" -> "en"
                        else -> "auto"
                    }
                    NgbLang.invalidate()
                }
            ),
            FeatureSpec(
                "entry.ngb.bridgeEnabled",
                "tooltip.ngb.bridgeEnabled",
                UiCategory.GENERAL,
                "core",
                boolOpt("entry.ngb.bridgeEnabled", "tooltip.ngb.bridgeEnabled", { config.bridgeEnabled }) { config.bridgeEnabled = it }
            ),
            FeatureSpec(
                "entry.ngb.environmentGuardEnabled",
                "tooltip.ngb.environmentGuardEnabled",
                UiCategory.GENERAL,
                "core",
                boolOpt(
                    "entry.ngb.environmentGuardEnabled",
                    "tooltip.ngb.environmentGuardEnabled",
                    { config.environmentGuardEnabled }
                ) { config.environmentGuardEnabled = it }
            ),
            FeatureSpec(
                "entry.ngb.requireHypixel",
                "tooltip.ngb.requireHypixel",
                UiCategory.GENERAL,
                "core",
                boolOpt("entry.ngb.requireHypixel", "tooltip.ngb.requireHypixel", { config.requireHypixel }) {
                    config.requireHypixel = it
                }
            ),
            FeatureSpec(
                "entry.ngb.hypixelGuildName",
                "tooltip.ngb.hypixelGuildName",
                UiCategory.GENERAL,
                "core",
                strOpt("entry.ngb.hypixelGuildName", "tooltip.ngb.hypixelGuildName", { config.hypixelGuildName }) {
                    config.hypixelGuildName = it.trim()
                }
            ),

            FeatureSpec(
                "entry.ngb.apiEndpointsInfo",
                "tooltip.ngb.apiEndpointsInfo",
                UiCategory.API,
                "api-info",
                infoRow("entry.ngb.apiEndpointsInfo", BridgeEndpoints.statusLine())
            ),
            FeatureSpec(
                "entry.ngb.bridgePollEnabled",
                "tooltip.ngb.bridgePollEnabled",
                UiCategory.API,
                "api-poll",
                boolOpt("entry.ngb.bridgePollEnabled", "tooltip.ngb.bridgePollEnabled", { config.bridgePollEnabled }) {
                    config.bridgePollEnabled = it
                }
            ),
            FeatureSpec(
                "entry.ngb.bridgePollOnlyWhenWsOffline",
                "tooltip.ngb.bridgePollOnlyWhenWsOffline",
                UiCategory.API,
                "api-poll",
                boolOpt(
                    "entry.ngb.bridgePollOnlyWhenWsOffline",
                    "tooltip.ngb.bridgePollOnlyWhenWsOffline",
                    { config.bridgePollOnlyWhenWsOffline }
                ) { config.bridgePollOnlyWhenWsOffline = it }
            ),
            FeatureSpec(
                "entry.ngb.bridgePollChannelId",
                "tooltip.ngb.bridgePollChannelId",
                UiCategory.API,
                "api-poll",
                strOpt("entry.ngb.bridgePollChannelId", "tooltip.ngb.bridgePollChannelId", { config.bridgePollChannelId }) {
                    config.bridgePollChannelId = it.trim().ifEmpty { "default" }
                }
            ),
            FeatureSpec(
                "entry.ngb.imsBridgeEnabled",
                "tooltip.ngb.imsBridgeEnabled",
                UiCategory.API,
                "api-ws",
                boolOpt("entry.ngb.imsBridgeEnabled", "tooltip.ngb.imsBridgeEnabled", { config.imsBridgeEnabled }) {
                    config.imsBridgeEnabled = it
                }
            ),
            FeatureSpec(
                "entry.ngb.imsAuthByNick",
                "tooltip.ngb.imsAuthByNick",
                UiCategory.API,
                "api-ws",
                boolOpt("entry.ngb.imsAuthByNick", "tooltip.ngb.imsAuthByNick", { config.imsAuthByNick }) {
                    config.imsAuthByNick = it
                }
            ),
            FeatureSpec(
                "entry.ngb.imsGuildSlug",
                "tooltip.ngb.imsGuildSlug",
                UiCategory.API,
                "api-ws",
                strOpt("entry.ngb.imsGuildSlug", "tooltip.ngb.imsGuildSlug", { config.imsGuildSlug }) {
                    config.imsGuildSlug = it.trim()
                }
            ),
            FeatureSpec(
                "entry.ngb.bridgeKey",
                "tooltip.ngb.bridgeKey",
                UiCategory.API,
                "api-ws",
                strOpt("entry.ngb.bridgeKey", "tooltip.ngb.bridgeKey", { config.bridgeKey }) {
                    config.bridgeKey = it.trim()
                }
            ),
            FeatureSpec(
                "entry.ngb.imsBridgeReceiveEnabled",
                "tooltip.ngb.imsBridgeReceiveEnabled",
                UiCategory.API,
                "api-ws",
                boolOpt(
                    "entry.ngb.imsBridgeReceiveEnabled",
                    "tooltip.ngb.imsBridgeReceiveEnabled",
                    { config.imsBridgeReceiveEnabled }
                ) { config.imsBridgeReceiveEnabled = it }
            ),
            FeatureSpec(
                "entry.ngb.imsWebOnlyMode",
                "tooltip.ngb.imsWebOnlyMode",
                UiCategory.API,
                "api-ws",
                boolOpt("entry.ngb.imsWebOnlyMode", "tooltip.ngb.imsWebOnlyMode", { config.imsWebOnlyMode }) {
                    config.imsWebOnlyMode = it
                }
            ),
            FeatureSpec(
                "entry.ngb.imsCombinedBridgeEnabled",
                "tooltip.ngb.imsCombinedBridgeEnabled",
                UiCategory.API,
                "api-ws",
                boolOpt(
                    "entry.ngb.imsCombinedBridgeEnabled",
                    "tooltip.ngb.imsCombinedBridgeEnabled",
                    { config.imsCombinedBridgeEnabled }
                ) { config.imsCombinedBridgeEnabled = it }
            ),
            FeatureSpec(
                "entry.ngb.imsCombinedBridgeChatEnabled",
                "tooltip.ngb.imsCombinedBridgeChatEnabled",
                UiCategory.API,
                "api-ws",
                boolOpt(
                    "entry.ngb.imsCombinedBridgeChatEnabled",
                    "tooltip.ngb.imsCombinedBridgeChatEnabled",
                    { config.imsCombinedBridgeChatEnabled }
                ) { config.imsCombinedBridgeChatEnabled = it }
            ),
            FeatureSpec(
                "entry.ngb.quoteSystemEnabled",
                "tooltip.ngb.quoteSystemEnabled",
                UiCategory.API,
                "api-quotes",
                boolOpt(
                    "entry.ngb.quoteSystemEnabled",
                    "tooltip.ngb.quoteSystemEnabled",
                    { config.quoteSystemEnabled }
                ) { config.quoteSystemEnabled = it }
            ),
            FeatureSpec(
                "entry.ngb.imsQuoteOverWsEnabled",
                "tooltip.ngb.imsQuoteOverWsEnabled",
                UiCategory.API,
                "api-quotes",
                boolOpt("entry.ngb.imsQuoteOverWsEnabled", "tooltip.ngb.imsQuoteOverWsEnabled", { config.imsQuoteOverWsEnabled }) {
                    config.imsQuoteOverWsEnabled = it
                }
            ),
            FeatureSpec(
                "entry.ngb.remoteBridgeEnabled",
                "tooltip.ngb.remoteBridgeEnabled",
                UiCategory.API,
                "api-sync",
                boolOpt(
                    "entry.ngb.remoteBridgeEnabled",
                    "tooltip.ngb.remoteBridgeEnabled",
                    { config.remoteBridgeEnabled }
                ) { config.remoteBridgeEnabled = it }
            ),
            FeatureSpec(
                "entry.ngb.remoteBridgeSendQuotes",
                "tooltip.ngb.remoteBridgeSendQuotes",
                UiCategory.API,
                "api-sync",
                boolOpt(
                    "entry.ngb.remoteBridgeSendQuotes",
                    "tooltip.ngb.remoteBridgeSendQuotes",
                    { config.remoteBridgeSendQuotes }
                ) { config.remoteBridgeSendQuotes = it }
            ),
            FeatureSpec(
                "entry.ngb.remoteBridgeChannelId",
                "tooltip.ngb.remoteBridgeChannelId",
                UiCategory.API,
                "api-sync",
                strOpt(
                    "entry.ngb.remoteBridgeChannelId",
                    "tooltip.ngb.remoteBridgeChannelId",
                    { config.remoteBridgeChannelId }
                ) { config.remoteBridgeChannelId = it.trim().ifEmpty { "default" } }
            ),

            FeatureSpec(
                "entry.ngb.guildBridgeFormatEnabled",
                null,
                UiCategory.CHAT,
                "formatting",
                boolOpt("entry.ngb.guildBridgeFormatEnabled", null, { config.guildBridgeFormatEnabled }) { config.guildBridgeFormatEnabled = it }
            ),
            FeatureSpec(
                "entry.ngb.bridgeCommandFormatEnabled",
                null,
                UiCategory.CHAT,
                "formatting",
                boolOpt("entry.ngb.bridgeCommandFormatEnabled", null, { config.bridgeCommandFormatEnabled }) { config.bridgeCommandFormatEnabled = it }
            ),
            FeatureSpec(
                "entry.ngb.bridgeBotFormatEnabled",
                "tooltip.ngb.bridgeBotFormatEnabled",
                UiCategory.CHAT,
                "formatting",
                boolOpt("entry.ngb.bridgeBotFormatEnabled", "tooltip.ngb.bridgeBotFormatEnabled", { config.bridgeBotFormatEnabled }) {
                    config.bridgeBotFormatEnabled = it
                }
            ),
            FeatureSpec(
                "entry.ngb.hideBotName",
                "tooltip.ngb.hideBotName",
                UiCategory.CHAT,
                "formatting",
                boolOpt("entry.ngb.hideBotName", "tooltip.ngb.hideBotName", { config.hideBotName }) { config.hideBotName = it }
            ),
            FeatureSpec(
                "entry.ngb.prefixColor",
                null,
                UiCategory.CHAT,
                "formatting",
                strOpt("entry.ngb.prefixColor", null, { config.prefixColor }) { config.prefixColor = it }
            ),
            FeatureSpec(
                "entry.ngb.nameColor",
                null,
                UiCategory.CHAT,
                "formatting",
                strOpt("entry.ngb.nameColor", null, { config.nameColor }) { config.nameColor = it }
            ),
            FeatureSpec(
                "entry.ngb.messageColor",
                null,
                UiCategory.CHAT,
                "formatting",
                strOpt("entry.ngb.messageColor", null, { config.messageColor }) { config.messageColor = it }
            ),

            FeatureSpec(
                "entry.ngb.telegramMarker",
                "tooltip.ngb.telegramMarker",
                UiCategory.CHAT,
                "sources",
                strOpt("entry.ngb.telegramMarker", "tooltip.ngb.telegramMarker", { config.telegramMarker }) {
                    config.telegramMarker = it.trim().ifEmpty { "[TG]" }
                }
            ),
            FeatureSpec(
                "entry.ngb.minecraftMarker",
                "tooltip.ngb.minecraftMarker",
                UiCategory.CHAT,
                "sources",
                strOpt("entry.ngb.minecraftMarker", "tooltip.ngb.minecraftMarker", { config.minecraftMarker }) {
                    config.minecraftMarker = it.ifEmpty { "." }
                }
            ),
            FeatureSpec(
                "entry.ngb.telegramLabel",
                null,
                UiCategory.CHAT,
                "sources",
                strOpt("entry.ngb.telegramLabel", null, { config.telegramLabel }) { config.telegramLabel = it.ifEmpty { "[Telegram] " } }
            ),
            FeatureSpec(
                "entry.ngb.discordLabel",
                null,
                UiCategory.CHAT,
                "sources",
                strOpt("entry.ngb.discordLabel", null, { config.discordLabel }) { config.discordLabel = it.ifEmpty { "[Discord] " } }
            ),
            FeatureSpec(
                "entry.ngb.minecraftLabel",
                null,
                UiCategory.CHAT,
                "sources",
                strOpt("entry.ngb.minecraftLabel", null, { config.minecraftLabel }) { config.minecraftLabel = it.ifEmpty { "[Minecraft] " } }
            ),
            FeatureSpec(
                "entry.ngb.telegramLabelColor",
                null,
                UiCategory.CHAT,
                "sources",
                strOpt("entry.ngb.telegramLabelColor", null, { config.telegramLabelColor }) { config.telegramLabelColor = it }
            ),
            FeatureSpec(
                "entry.ngb.discordLabelColor",
                null,
                UiCategory.CHAT,
                "sources",
                strOpt("entry.ngb.discordLabelColor", null, { config.discordLabelColor }) { config.discordLabelColor = it }
            ),
            FeatureSpec(
                "entry.ngb.minecraftLabelColor",
                null,
                UiCategory.CHAT,
                "sources",
                strOpt("entry.ngb.minecraftLabelColor", null, { config.minecraftLabelColor }) { config.minecraftLabelColor = it }
            ),

            FeatureSpec(
                "entry.ngb.blockList",
                "tooltip.ngb.blockList",
                UiCategory.CHAT,
                "filters",
                strOpt("entry.ngb.blockList", "tooltip.ngb.blockList", { config.blockList.joinToString(", ") }) { value ->
                    config.blockList = value
                        .split("\n", ",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                }
            ),
            FeatureSpec(
                "entry.ngb.nickHighlightEnabled",
                "tooltip.ngb.nickHighlightEnabled",
                UiCategory.CHAT,
                "nick",
                boolOpt("entry.ngb.nickHighlightEnabled", "tooltip.ngb.nickHighlightEnabled", { config.nickHighlightEnabled }) {
                    config.nickHighlightEnabled = it
                }
            ),
            FeatureSpec(
                "entry.ngb.nickHighlightColor",
                null,
                UiCategory.CHAT,
                "nick",
                strOpt("entry.ngb.nickHighlightColor", null, { config.nickHighlightColor }) { config.nickHighlightColor = it }
            ),
            FeatureSpec(
                "entry.ngb.senderNickColorEnabled",
                "tooltip.ngb.senderNickColorEnabled",
                UiCategory.CHAT,
                "nick",
                boolOpt("entry.ngb.senderNickColorEnabled", "tooltip.ngb.senderNickColorEnabled", { config.senderNickColorEnabled }) {
                    config.senderNickColorEnabled = it
                }
            ),
            FeatureSpec(
                "entry.ngb.senderNickColor",
                null,
                UiCategory.CHAT,
                "nick",
                strOpt("entry.ngb.senderNickColor", null, { config.senderNickColor }) { config.senderNickColor = it }
            ),
            FeatureSpec(
                "entry.ngb.senderNickLegacyEnabled",
                "tooltip.ngb.senderNickLegacyEnabled",
                UiCategory.CHAT,
                "nick",
                boolOpt("entry.ngb.senderNickLegacyEnabled", "tooltip.ngb.senderNickLegacyEnabled", { config.senderNickLegacyEnabled }) {
                    config.senderNickLegacyEnabled = it
                }
            ),
            FeatureSpec(
                "entry.ngb.senderNickLegacyCodes",
                "tooltip.ngb.senderNickLegacyCodes",
                UiCategory.CHAT,
                "nick",
                strOpt("entry.ngb.senderNickLegacyCodes", "tooltip.ngb.senderNickLegacyCodes", { config.senderNickLegacyCodes }) {
                    config.senderNickLegacyCodes = it.ifEmpty { "§4" }
                }
            ),
            FeatureSpec(
                "entry.ngb.senderNickStyleOnlyMine",
                "tooltip.ngb.senderNickStyleOnlyMine",
                UiCategory.CHAT,
                "nick",
                boolOpt("entry.ngb.senderNickStyleOnlyMine", "tooltip.ngb.senderNickStyleOnlyMine", { config.senderNickStyleOnlyMine }) {
                    config.senderNickStyleOnlyMine = it
                }
            ),
            FeatureSpec(
                "entry.ngb.myNickAliases",
                "tooltip.ngb.myNickAliases",
                UiCategory.CHAT,
                "nick",
                strOpt("entry.ngb.myNickAliases", "tooltip.ngb.myNickAliases", { config.myNickAliases.joinToString(", ") }) { value ->
                    config.myNickAliases = value
                        .split("\n", ",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                }
            ),
            FeatureSpec(
                "entry.ngb.wordHighlightEnabled",
                "tooltip.ngb.wordHighlightEnabled",
                UiCategory.CHAT,
                "filters",
                boolOpt("entry.ngb.wordHighlightEnabled", "tooltip.ngb.wordHighlightEnabled", { config.wordHighlightEnabled }) {
                    config.wordHighlightEnabled = it
                }
            ),
            FeatureSpec(
                "entry.ngb.wordHighlightRules",
                "tooltip.ngb.wordHighlightRules",
                UiCategory.CHAT,
                "filters",
                strOpt("entry.ngb.wordHighlightRules", "tooltip.ngb.wordHighlightRules", { config.wordHighlightRules }) { config.wordHighlightRules = it }
            ),
            FeatureSpec(
                "entry.ngb.wordHighlightOnlyMine",
                "tooltip.ngb.wordHighlightOnlyMine",
                UiCategory.CHAT,
                "filters",
                boolOpt("entry.ngb.wordHighlightOnlyMine", "tooltip.ngb.wordHighlightOnlyMine", { config.wordHighlightOnlyMine }) {
                    config.wordHighlightOnlyMine = it
                }
            ),

            FeatureSpec(
                "entry.ngb.collapseChat",
                "tooltip.ngb.collapseChat",
                UiCategory.MISC,
                "qol",
                boolOpt("entry.ngb.collapseChat", "tooltip.ngb.collapseChat", { config.collapseChat }) { config.collapseChat = it }
            ),
            FeatureSpec(
                "entry.ngb.unlimitedChat",
                "tooltip.ngb.unlimitedChat",
                UiCategory.MISC,
                "qol",
                boolOpt("entry.ngb.unlimitedChat", "tooltip.ngb.unlimitedChat", { config.unlimitedChat }) { config.unlimitedChat = it }
            ),
            FeatureSpec(
                "entry.ngb.emojiShortcodesEnabled",
                "tooltip.ngb.emojiShortcodesEnabled",
                UiCategory.MISC,
                "qol",
                boolOpt("entry.ngb.emojiShortcodesEnabled", "tooltip.ngb.emojiShortcodesEnabled", { config.emojiShortcodesEnabled }) {
                    config.emojiShortcodesEnabled = it
                }
            ),
            FeatureSpec(
                "entry.ngb.linkPreviewEnabled",
                "tooltip.ngb.linkPreviewEnabled",
                UiCategory.MISC,
                "qol",
                boolOpt("entry.ngb.linkPreviewEnabled", "tooltip.ngb.linkPreviewEnabled", { config.linkPreviewEnabled }) {
                    config.linkPreviewEnabled = it
                }
            ),
            FeatureSpec(
                "entry.ngb.imagePreviewEnabled",
                "tooltip.ngb.imagePreviewEnabled",
                UiCategory.MISC,
                "qol",
                boolOpt("entry.ngb.imagePreviewEnabled", "tooltip.ngb.imagePreviewEnabled", { config.imagePreviewEnabled }) {
                    config.imagePreviewEnabled = it
                }
            ),
            FeatureSpec(
                "entry.ngb.updateCheckEnabled",
                "tooltip.ngb.updateCheckEnabled",
                UiCategory.MISC,
                "qol",
                boolOpt("entry.ngb.updateCheckEnabled", "tooltip.ngb.updateCheckEnabled", { config.updateCheckEnabled }) {
                    config.updateCheckEnabled = it
                }
            ),
            FeatureSpec(
                "entry.ngb.copyChatEnabled",
                "tooltip.ngb.copyChatEnabled",
                UiCategory.MISC,
                "qol",
                boolOpt("entry.ngb.copyChatEnabled", "tooltip.ngb.copyChatEnabled", { config.copyChatEnabled }) {
                    config.copyChatEnabled = it
                }
            ),
            FeatureSpec(
                "entry.ngb.persistentChatEnabled",
                "tooltip.ngb.persistentChatEnabled",
                UiCategory.MISC,
                "qol",
                boolOpt("entry.ngb.persistentChatEnabled", "tooltip.ngb.persistentChatEnabled", { config.persistentChatEnabled }) {
                    config.persistentChatEnabled = it
                }
            )
        )
    }

    private fun buildFeatureCategory(category: UiCategory, features: List<FeatureSpec>): ConfigCategory {
        val groups = features.groupBy { it.group ?: inferGroupKey(it) }
            .toSortedMap()
            .map { (groupKey, grouped) ->
                OptionGroup.createBuilder()
                    .name(NgbLang.component(groupTitleKey(groupKey)))
                    .options(grouped.map { it.option })
                    .collapsed(groupKey != "core" && groupKey != "api-info")
                    .build()
            }

        return ConfigCategory.createBuilder()
            .name(NgbLang.component(categoryTitleKey(category)))
            .groups(groups)
            .build()
    }

    private fun buildAboutCategory(): ConfigCategory {
        val metadata = FabricLoader.getInstance().getModContainer("nayoguildbridge")
            .map { it.metadata }
            .orElse(null)
        val modName = metadata?.name ?: "NayoGuildBridge"
        val modVersion = metadata?.version?.friendlyString ?: "unknown"
        val modDescription = metadata?.description ?: "Bridge message formatter for client chat."
        val githubUrl = metadata?.contact?.get("sources")?.orElse(null) ?: "https://github.com/nayokage/bridgefilter"
        val discordUrl = metadata?.contact?.get("discord")?.orElse(null) ?: "https://discord.gg"

        val detailsGroup = OptionGroup.createBuilder()
            .name(NgbLang.component("group.ngb.about.details"))
            .options(
                listOf(
                    infoRow("about.ngb.openName", modName),
                    infoRow("about.ngb.openVersion", modVersion),
                    infoRow("about.ngb.openDescription", modDescription),
                    infoRow("entry.ngb.apiEndpointsInfo", BridgeEndpoints.statusLine())
                )
            )
            .build()

        val linksGroup = OptionGroup.createBuilder()
            .name(NgbLang.component("group.ngb.about.links"))
            .options(
                listOf(
                    linkButton("about.ngb.openGithub", githubUrl),
                    linkButton("about.ngb.openDiscord", discordUrl)
                )
            )
            .build()

        return ConfigCategory.createBuilder()
            .name(NgbLang.component("category.ngb.about"))
            .groups(listOf(detailsGroup, linksGroup))
            .build()
    }

    private fun linkButton(nameKey: String, url: String): ButtonOption {
        return ButtonOption.createBuilder()
            .name(NgbLang.component(nameKey))
            .text(NgbLang.component("about.ngb.open"))
            .action { Util.getPlatform().openUri(url) }
            .description(OptionDescription.of(Component.literal(url)))
            .build()
    }

    private fun infoRow(nameKey: String, value: String): ButtonOption {
        return ButtonOption.createBuilder()
            .name(NgbLang.component(nameKey))
            .text(Component.literal(value))
            .available(false)
            .action { }
            .build()
    }

    private fun inferCategory(spec: FeatureSpec): UiCategory {
        val text = "${spec.key} ${spec.tooltipKey.orEmpty()}".lowercase()
        return when {
            hasAny(text, listOf("poll", "imsbridge", "quote", "remotebridge", "bridgekey", "apiendpoint", "platform")) -> UiCategory.API
            hasAny(text, listOf("chat", "bridge", "message", "prefix", "telegram", "discord", "minecraft", "nick", "word", "filter", "bot", "guild")) -> UiCategory.CHAT
            hasAny(text, listOf("combat", "pvp", "sword", "hit", "attack", "crit", "damage")) -> UiCategory.COMBAT
            hasAny(text, listOf("render", "esp", "overlay", "hud", "visual", "particle", "color")) -> UiCategory.RENDER
            hasAny(text, listOf("global", "core", "general", "master", "hypixel", "environment")) -> UiCategory.GENERAL
            else -> UiCategory.MISC
        }
    }

    private fun inferGroupKey(spec: FeatureSpec): String {
        val text = "${spec.key} ${spec.tooltipKey.orEmpty()}".lowercase()
        return when {
            hasAny(text, listOf("block", "wordhighlight", "filter")) -> "filters"
            hasAny(text, listOf("nick", "alias", "sender")) -> "nick"
            hasAny(text, listOf("telegram", "discord", "minecraft", "marker", "source")) -> "sources"
            hasAny(text, listOf("format", "label", "messagecolor", "namecolor", "bot")) -> "formatting"
            hasAny(text, listOf("enabled", "global", "master")) -> "core"
            else -> "other"
        }
    }

    private fun categoryTitleKey(category: UiCategory): String {
        return when (category) {
            UiCategory.GENERAL -> "category.ngb.general"
            UiCategory.API -> "category.ngb.api"
            UiCategory.CHAT -> "category.ngb.chat"
            UiCategory.COMBAT -> "category.ngb.combat"
            UiCategory.RENDER -> "category.ngb.render"
            UiCategory.MISC -> "category.ngb.misc"
        }
    }

    private fun groupTitleKey(group: String): String {
        return when (group) {
            "filters" -> "group.ngb.filters"
            "nick" -> "group.ngb.nick"
            "sources" -> "group.ngb.sources"
            "formatting" -> "group.ngb.formatting"
            "core" -> "group.ngb.core"
            "api-info" -> "group.ngb.api.info"
            "api-poll" -> "group.ngb.api.poll"
            "api-ws" -> "group.ngb.api.ws"
            "api-quotes" -> "group.ngb.api.quotes"
            "api-sync" -> "group.ngb.api.sync"
            "qol" -> "group.ngb.qol"
            else -> "group.ngb.other"
        }
    }

    private fun hasAny(text: String, needles: List<String>): Boolean {
        return needles.any(text::contains)
    }

    private fun boolOpt(
        key: String,
        tooltipKey: String?,
        getter: () -> Boolean,
        save: (Boolean) -> Unit
    ): Option<Boolean> {
        return Option.createBuilder<Boolean>()
            .name(NgbLang.component(key))
            .description(
                tooltipKey?.let { OptionDescription.of(NgbLang.component(it)) } ?: OptionDescription.EMPTY
            )
            .binding(
                getter(),
                getter,
                { newVal ->
                    val old = getter()
                    save(newVal)
                    if (old != newVal) {
                        notifyToggle(key, newVal)
                    }
                }
            )
            .controller { opt -> TickBoxControllerBuilder.create(opt) }
            .build()
    }

    private fun strOpt(
        key: String,
        tooltipKey: String?,
        getter: () -> String,
        save: (String) -> Unit
    ): Option<String> {
        return Option.createBuilder<String>()
            .name(NgbLang.component(key))
            .description(
                tooltipKey?.let { OptionDescription.of(NgbLang.component(it)) } ?: OptionDescription.EMPTY
            )
            .binding(getter(), getter, save)
            .controller { opt -> StringControllerBuilder.create(opt) }
            .build()
    }

    private fun notifyToggle(key: String, enabled: Boolean) {
        val label = NgbLang.text(key)
        val lang = NgbConfig.config.menuLanguage.trim().lowercase()
        val useRu = lang == "ru" || (lang == "auto" && Minecraft.getInstance().options.languageCode.lowercase().startsWith("ru"))
        val state = if (enabled) {
            if (useRu) "§aвключено" else "§aON"
        } else {
            if (useRu) "§cвыключено" else "§cOFF"
        }
        Minecraft.getInstance().execute {
            Minecraft.getInstance().player?.displayClientMessage(
                Component.literal("§a[NGB] §f$label: $state"),
                false
            )
        }
    }
}
