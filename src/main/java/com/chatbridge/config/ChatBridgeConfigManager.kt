package com.nayoguildbridge.config

import com.nayoguildbridge.config.ChatBridgeConfig.config
import dev.isxander.yacl3.api.ButtonOption
import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.OptionGroup
import dev.isxander.yacl3.api.YetAnotherConfigLib
import dev.isxander.yacl3.api.controller.StringControllerBuilder
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.Screen
import net.minecraft.Util
import net.minecraft.network.chat.Component

object ChatBridgeConfigManager {
    private enum class UiCategory {
        GENERAL, CHAT, COMBAT, RENDER, MISC
    }

    private data class FeatureSpec(
        val key: String,
        val tooltipKey: String? = null,
        val option: Option<*>
    )

    fun build(parent: Screen?): Screen {
        val specs = buildFeatureSpecs()
        val groupedByCategory = specs.groupBy { inferCategory(it) }
        val categories = mutableListOf<ConfigCategory>()

        UiCategory.entries.forEach { category ->
            val features = groupedByCategory[category].orEmpty()
            if (features.isEmpty()) return@forEach
            categories += buildFeatureCategory(category, features)
        }

        categories += buildAboutCategory()

        val builder = YetAnotherConfigLib.createBuilder()
            .title(Component.translatable("title.chatbridge.config"))
            .save(ChatBridgeConfig::save)
        categories.forEach(builder::category)

        return builder.build()
            .generateScreen(parent)
    }

    private fun buildFeatureSpecs(): List<FeatureSpec> {
        return listOf(
            FeatureSpec(
                "entry.chatbridge.bridgeEnabled",
                "tooltip.chatbridge.bridgeEnabled",
                boolOpt("entry.chatbridge.bridgeEnabled", "tooltip.chatbridge.bridgeEnabled", { config.bridgeEnabled }) { config.bridgeEnabled = it }
            ),
            FeatureSpec(
                "entry.chatbridge.remoteBridgeEnabled",
                "tooltip.chatbridge.remoteBridgeEnabled",
                boolOpt(
                    "entry.chatbridge.remoteBridgeEnabled",
                    "tooltip.chatbridge.remoteBridgeEnabled",
                    { config.remoteBridgeEnabled }
                ) { config.remoteBridgeEnabled = it }
            ),
            FeatureSpec(
                "entry.chatbridge.remoteBridgeChannelId",
                "tooltip.chatbridge.remoteBridgeChannelId",
                strOpt(
                    "entry.chatbridge.remoteBridgeChannelId",
                    "tooltip.chatbridge.remoteBridgeChannelId",
                    { config.remoteBridgeChannelId }
                ) { config.remoteBridgeChannelId = it.trim().ifEmpty { "default" } }
            ),
            FeatureSpec(
                "entry.chatbridge.remoteBridgePollMs",
                "tooltip.chatbridge.remoteBridgePollMs",
                strOpt(
                    "entry.chatbridge.remoteBridgePollMs",
                    "tooltip.chatbridge.remoteBridgePollMs",
                    { config.remoteBridgePollMs.toString() }
                ) { v ->
                    config.remoteBridgePollMs = v.trim().toIntOrNull()?.coerceIn(250, 10000) ?: config.remoteBridgePollMs
                }
            ),
            FeatureSpec(
                "entry.chatbridge.nameColor",
                null,
                strOpt("entry.chatbridge.nameColor", null, { config.nameColor }) { config.nameColor = it }
            ),
            FeatureSpec(
                "entry.chatbridge.messageColor",
                null,
                strOpt("entry.chatbridge.messageColor", null, { config.messageColor }) { config.messageColor = it }
            ),
            FeatureSpec(
                "entry.chatbridge.nickHighlightEnabled",
                "tooltip.chatbridge.nickHighlightEnabled",
                boolOpt("entry.chatbridge.nickHighlightEnabled", "tooltip.chatbridge.nickHighlightEnabled", { config.nickHighlightEnabled }) {
                    config.nickHighlightEnabled = it
                }
            ),
            FeatureSpec(
                "entry.chatbridge.nickHighlightColor",
                null,
                strOpt("entry.chatbridge.nickHighlightColor", null, { config.nickHighlightColor }) { config.nickHighlightColor = it }
            ),
            FeatureSpec(
                "entry.chatbridge.senderNickColorEnabled",
                "tooltip.chatbridge.senderNickColorEnabled",
                boolOpt("entry.chatbridge.senderNickColorEnabled", "tooltip.chatbridge.senderNickColorEnabled", { config.senderNickColorEnabled }) {
                    config.senderNickColorEnabled = it
                }
            ),
            FeatureSpec(
                "entry.chatbridge.senderNickColor",
                null,
                strOpt("entry.chatbridge.senderNickColor", null, { config.senderNickColor }) { config.senderNickColor = it }
            ),
            FeatureSpec(
                "entry.chatbridge.senderNickLegacyEnabled",
                "tooltip.chatbridge.senderNickLegacyEnabled",
                boolOpt("entry.chatbridge.senderNickLegacyEnabled", "tooltip.chatbridge.senderNickLegacyEnabled", { config.senderNickLegacyEnabled }) {
                    config.senderNickLegacyEnabled = it
                }
            ),
            FeatureSpec(
                "entry.chatbridge.senderNickLegacyCodes",
                "tooltip.chatbridge.senderNickLegacyCodes",
                strOpt("entry.chatbridge.senderNickLegacyCodes", "tooltip.chatbridge.senderNickLegacyCodes", { config.senderNickLegacyCodes }) {
                    config.senderNickLegacyCodes = it.ifEmpty { "§4" }
                }
            ),
            FeatureSpec(
                "entry.chatbridge.senderNickStyleOnlyMine",
                "tooltip.chatbridge.senderNickStyleOnlyMine",
                boolOpt("entry.chatbridge.senderNickStyleOnlyMine", "tooltip.chatbridge.senderNickStyleOnlyMine", { config.senderNickStyleOnlyMine }) {
                    config.senderNickStyleOnlyMine = it
                }
            ),
            FeatureSpec(
                "entry.chatbridge.myNickAliases",
                "tooltip.chatbridge.myNickAliases",
                strOpt("entry.chatbridge.myNickAliases", "tooltip.chatbridge.myNickAliases", { config.myNickAliases.joinToString(", ") }) { value ->
                    config.myNickAliases = value
                            .split("\n", ",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                }
            ),
            FeatureSpec(
                "entry.chatbridge.guildBridgeFormatEnabled",
                null,
                boolOpt("entry.chatbridge.guildBridgeFormatEnabled", null, { config.guildBridgeFormatEnabled }) { config.guildBridgeFormatEnabled = it }
            ),
            FeatureSpec(
                "entry.chatbridge.bridgeCommandFormatEnabled",
                null,
                boolOpt("entry.chatbridge.bridgeCommandFormatEnabled", null, { config.bridgeCommandFormatEnabled }) { config.bridgeCommandFormatEnabled = it }
            ),
            FeatureSpec(
                "entry.chatbridge.telegramMarker",
                "tooltip.chatbridge.telegramMarker",
                strOpt("entry.chatbridge.telegramMarker", "tooltip.chatbridge.telegramMarker", { config.telegramMarker }) {
                    config.telegramMarker = it.trim().ifEmpty { "[TG]" }
                }
            ),
            FeatureSpec(
                "entry.chatbridge.minecraftMarker",
                "tooltip.chatbridge.minecraftMarker",
                strOpt("entry.chatbridge.minecraftMarker", "tooltip.chatbridge.minecraftMarker", { config.minecraftMarker }) {
                    config.minecraftMarker = it.ifEmpty { "." }
                }
            ),
            FeatureSpec(
                "entry.chatbridge.telegramLabel",
                null,
                strOpt("entry.chatbridge.telegramLabel", null, { config.telegramLabel }) { config.telegramLabel = it.ifEmpty { "[Telegram] " } }
            ),
            FeatureSpec(
                "entry.chatbridge.discordLabel",
                null,
                strOpt("entry.chatbridge.discordLabel", null, { config.discordLabel }) { config.discordLabel = it.ifEmpty { "[Discord] " } }
            ),
            FeatureSpec(
                "entry.chatbridge.minecraftLabel",
                null,
                strOpt("entry.chatbridge.minecraftLabel", null, { config.minecraftLabel }) { config.minecraftLabel = it.ifEmpty { "[Minecraft] " } }
            ),
            FeatureSpec(
                "entry.chatbridge.telegramLabelColor",
                null,
                strOpt("entry.chatbridge.telegramLabelColor", null, { config.telegramLabelColor }) { config.telegramLabelColor = it }
            ),
            FeatureSpec(
                "entry.chatbridge.discordLabelColor",
                null,
                strOpt("entry.chatbridge.discordLabelColor", null, { config.discordLabelColor }) { config.discordLabelColor = it }
            ),
            FeatureSpec(
                "entry.chatbridge.minecraftLabelColor",
                null,
                strOpt("entry.chatbridge.minecraftLabelColor", null, { config.minecraftLabelColor }) { config.minecraftLabelColor = it }
            ),
            FeatureSpec(
                "entry.chatbridge.wordHighlightEnabled",
                "tooltip.chatbridge.wordHighlightEnabled",
                boolOpt("entry.chatbridge.wordHighlightEnabled", "tooltip.chatbridge.wordHighlightEnabled", { config.wordHighlightEnabled }) {
                    config.wordHighlightEnabled = it
                }
            ),
            FeatureSpec(
                "entry.chatbridge.wordHighlightRules",
                "tooltip.chatbridge.wordHighlightRules",
                strOpt("entry.chatbridge.wordHighlightRules", "tooltip.chatbridge.wordHighlightRules", { config.wordHighlightRules }) { config.wordHighlightRules = it }
            ),
            FeatureSpec(
                "entry.chatbridge.wordHighlightOnlyMine",
                "tooltip.chatbridge.wordHighlightOnlyMine",
                boolOpt("entry.chatbridge.wordHighlightOnlyMine", "tooltip.chatbridge.wordHighlightOnlyMine", { config.wordHighlightOnlyMine }) {
                    config.wordHighlightOnlyMine = it
                }
            ),
            FeatureSpec(
                "entry.chatbridge.hideBotName",
                "tooltip.chatbridge.hideBotName",
                boolOpt("entry.chatbridge.hideBotName", "tooltip.chatbridge.hideBotName", { config.hideBotName }) { config.hideBotName = it }
            ),
            FeatureSpec(
                "entry.chatbridge.blockList",
                "tooltip.chatbridge.blockList",
                strOpt("entry.chatbridge.blockList", "tooltip.chatbridge.blockList", { config.blockList.joinToString(", ") }) { value ->
                    config.blockList = value
                            .split("\n", ",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                }
            )
        )
    }

    private fun buildFeatureCategory(category: UiCategory, features: List<FeatureSpec>): ConfigCategory {
        val groups = features.groupBy(::inferGroupKey)
            .toSortedMap()
            .map { (groupKey, grouped) ->
                OptionGroup.createBuilder()
                    .name(Component.translatable(groupTitleKey(groupKey)))
                    .options(grouped.map { it.option })
                    .collapsed(false)
                    .build()
            }

        return ConfigCategory.createBuilder()
            .name(Component.translatable(categoryTitleKey(category)))
            .groups(groups)
            .build()
    }

    private fun buildAboutCategory(): ConfigCategory {
        val metadata = FabricLoader.getInstance().getModContainer("chatbridge")
            .map { it.metadata }
            .orElse(null)
        val modName = metadata?.name ?: "NayoGuildBridge"
        val modVersion = metadata?.version?.friendlyString ?: "unknown"
        val modDescription = metadata?.description ?: "Bridge message formatter for client chat."
        val githubUrl = metadata?.contact?.get("sources")?.orElse(null) ?: "https://github.com/nayokage/bridgefilter"
        val discordUrl = metadata?.contact?.get("discord")?.orElse(null) ?: "https://discord.gg"

        val detailsGroup = OptionGroup.createBuilder()
            .name(Component.translatable("group.chatbridge.about.details"))
            .options(
                listOf(
                    infoRow("about.chatbridge.openName", modName),
                    infoRow("about.chatbridge.openVersion", modVersion),
                    infoRow("about.chatbridge.openDescription", modDescription)
                )
            )
            .build()

        val linksGroup = OptionGroup.createBuilder()
            .name(Component.translatable("group.chatbridge.about.links"))
            .options(
                listOf(
                    linkButton("about.chatbridge.openGithub", githubUrl),
                    linkButton("about.chatbridge.openDiscord", discordUrl)
                )
            )
                .build()

        return ConfigCategory.createBuilder()
            .name(Component.translatable("category.chatbridge.about"))
            .groups(listOf(detailsGroup, linksGroup))
            .build()
    }

    private fun linkButton(nameKey: String, url: String): ButtonOption {
        return ButtonOption.createBuilder()
            .name(Component.translatable(nameKey))
            .text(Component.translatable("about.chatbridge.open"))
            .action { Util.getPlatform().openUri(url) }
            .description(OptionDescription.of(Component.literal(url)))
            .build()
    }

    private fun infoRow(nameKey: String, value: String): ButtonOption {
        return ButtonOption.createBuilder()
            .name(Component.translatable(nameKey))
            .text(Component.literal(value))
            .available(false)
            .action { }
                .build()
    }

    private fun inferCategory(spec: FeatureSpec): UiCategory {
        val text = "${spec.key} ${spec.tooltipKey.orEmpty()}".lowercase()
        return when {
            hasAny(text, listOf("chat", "bridge", "message", "prefix", "telegram", "discord", "minecraft", "nick", "word", "filter", "bot", "guild", "quote", "api")) -> UiCategory.CHAT
            hasAny(text, listOf("combat", "pvp", "sword", "hit", "attack", "crit", "damage")) -> UiCategory.COMBAT
            hasAny(text, listOf("render", "esp", "overlay", "hud", "visual", "particle", "color")) -> UiCategory.RENDER
            hasAny(text, listOf("global", "core", "general", "master")) -> UiCategory.GENERAL
            else -> UiCategory.MISC
        }
    }

    private fun inferGroupKey(spec: FeatureSpec): String {
        val text = "${spec.key} ${spec.tooltipKey.orEmpty()}".lowercase()
        return when {
            hasAny(text, listOf("block", "wordhighlight", "filter")) -> "filters"
            hasAny(text, listOf("nick", "alias", "sender")) -> "nick"
            hasAny(text, listOf("telegram", "discord", "minecraft", "marker", "source")) -> "sources"
            hasAny(text, listOf("format", "label", "messagecolor", "namecolor")) -> "formatting"
            hasAny(text, listOf("enabled", "global", "master")) -> "core"
            else -> "other"
        }
    }

    private fun categoryTitleKey(category: UiCategory): String {
        return when (category) {
            UiCategory.GENERAL -> "category.chatbridge.general"
            UiCategory.CHAT -> "category.chatbridge.chat"
            UiCategory.COMBAT -> "category.chatbridge.combat"
            UiCategory.RENDER -> "category.chatbridge.render"
            UiCategory.MISC -> "category.chatbridge.misc"
        }
    }

    private fun groupTitleKey(group: String): String {
        return when (group) {
            "filters" -> "group.chatbridge.filters"
            "nick" -> "group.chatbridge.nick"
            "sources" -> "group.chatbridge.sources"
            "formatting" -> "group.chatbridge.formatting"
            "core" -> "group.chatbridge.core"
            else -> "group.chatbridge.other"
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
            .name(Component.translatable(key))
            .description(tooltipKey?.let { OptionDescription.of(Component.translatable(it)) } ?: OptionDescription.EMPTY)
            .binding(getter(), getter, save)
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
            .name(Component.translatable(key))
            .description(tooltipKey?.let { OptionDescription.of(Component.translatable(it)) } ?: OptionDescription.EMPTY)
            .binding(getter(), getter, save)
            .controller { opt -> StringControllerBuilder.create(opt) }
            .build()
    }

}