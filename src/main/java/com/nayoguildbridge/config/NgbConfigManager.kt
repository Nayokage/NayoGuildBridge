package com.nayoguildbridge.config

import com.nayoguildbridge.config.NgbConfig.config
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

object NgbConfigManager {
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
            .title(Component.translatable("title.ngb.config"))
            .save(NgbConfig::save)
        categories.forEach(builder::category)

        return builder.build()
            .generateScreen(parent)
    }

    private fun buildFeatureSpecs(): List<FeatureSpec> {
        return listOf(
            FeatureSpec(
                "entry.ngb.bridgeEnabled",
                "tooltip.ngb.bridgeEnabled",
                boolOpt("entry.ngb.bridgeEnabled", "tooltip.ngb.bridgeEnabled", { config.bridgeEnabled }) { config.bridgeEnabled = it }
            ),
            FeatureSpec(
                "entry.ngb.remoteBridgeEnabled",
                "tooltip.ngb.remoteBridgeEnabled",
                boolOpt(
                    "entry.ngb.remoteBridgeEnabled",
                    "tooltip.ngb.remoteBridgeEnabled",
                    { config.remoteBridgeEnabled }
                ) { config.remoteBridgeEnabled = it }
            ),
            FeatureSpec(
                "entry.ngb.remoteBridgeChannelId",
                "tooltip.ngb.remoteBridgeChannelId",
                strOpt(
                    "entry.ngb.remoteBridgeChannelId",
                    "tooltip.ngb.remoteBridgeChannelId",
                    { config.remoteBridgeChannelId }
                ) { config.remoteBridgeChannelId = it.trim().ifEmpty { "default" } }
            ),
            FeatureSpec(
                "entry.ngb.remoteBridgePollMs",
                "tooltip.ngb.remoteBridgePollMs",
                strOpt(
                    "entry.ngb.remoteBridgePollMs",
                    "tooltip.ngb.remoteBridgePollMs",
                    { config.remoteBridgePollMs.toString() }
                ) { v ->
                    config.remoteBridgePollMs = v.trim().toIntOrNull()?.coerceIn(250, 10000) ?: config.remoteBridgePollMs
                }
            ),
            FeatureSpec(
                "entry.ngb.nameColor",
                null,
                strOpt("entry.ngb.nameColor", null, { config.nameColor }) { config.nameColor = it }
            ),
            FeatureSpec(
                "entry.ngb.messageColor",
                null,
                strOpt("entry.ngb.messageColor", null, { config.messageColor }) { config.messageColor = it }
            ),
            FeatureSpec(
                "entry.ngb.nickHighlightEnabled",
                "tooltip.ngb.nickHighlightEnabled",
                boolOpt("entry.ngb.nickHighlightEnabled", "tooltip.ngb.nickHighlightEnabled", { config.nickHighlightEnabled }) {
                    config.nickHighlightEnabled = it
                }
            ),
            FeatureSpec(
                "entry.ngb.nickHighlightColor",
                null,
                strOpt("entry.ngb.nickHighlightColor", null, { config.nickHighlightColor }) { config.nickHighlightColor = it }
            ),
            FeatureSpec(
                "entry.ngb.senderNickColorEnabled",
                "tooltip.ngb.senderNickColorEnabled",
                boolOpt("entry.ngb.senderNickColorEnabled", "tooltip.ngb.senderNickColorEnabled", { config.senderNickColorEnabled }) {
                    config.senderNickColorEnabled = it
                }
            ),
            FeatureSpec(
                "entry.ngb.senderNickColor",
                null,
                strOpt("entry.ngb.senderNickColor", null, { config.senderNickColor }) { config.senderNickColor = it }
            ),
            FeatureSpec(
                "entry.ngb.senderNickLegacyEnabled",
                "tooltip.ngb.senderNickLegacyEnabled",
                boolOpt("entry.ngb.senderNickLegacyEnabled", "tooltip.ngb.senderNickLegacyEnabled", { config.senderNickLegacyEnabled }) {
                    config.senderNickLegacyEnabled = it
                }
            ),
            FeatureSpec(
                "entry.ngb.senderNickLegacyCodes",
                "tooltip.ngb.senderNickLegacyCodes",
                strOpt("entry.ngb.senderNickLegacyCodes", "tooltip.ngb.senderNickLegacyCodes", { config.senderNickLegacyCodes }) {
                    config.senderNickLegacyCodes = it.ifEmpty { "§4" }
                }
            ),
            FeatureSpec(
                "entry.ngb.senderNickStyleOnlyMine",
                "tooltip.ngb.senderNickStyleOnlyMine",
                boolOpt("entry.ngb.senderNickStyleOnlyMine", "tooltip.ngb.senderNickStyleOnlyMine", { config.senderNickStyleOnlyMine }) {
                    config.senderNickStyleOnlyMine = it
                }
            ),
            FeatureSpec(
                "entry.ngb.myNickAliases",
                "tooltip.ngb.myNickAliases",
                strOpt("entry.ngb.myNickAliases", "tooltip.ngb.myNickAliases", { config.myNickAliases.joinToString(", ") }) { value ->
                    config.myNickAliases = value
                            .split("\n", ",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                }
            ),
            FeatureSpec(
                "entry.ngb.guildBridgeFormatEnabled",
                null,
                boolOpt("entry.ngb.guildBridgeFormatEnabled", null, { config.guildBridgeFormatEnabled }) { config.guildBridgeFormatEnabled = it }
            ),
            FeatureSpec(
                "entry.ngb.bridgeCommandFormatEnabled",
                null,
                boolOpt("entry.ngb.bridgeCommandFormatEnabled", null, { config.bridgeCommandFormatEnabled }) { config.bridgeCommandFormatEnabled = it }
            ),
            FeatureSpec(
                "entry.ngb.telegramMarker",
                "tooltip.ngb.telegramMarker",
                strOpt("entry.ngb.telegramMarker", "tooltip.ngb.telegramMarker", { config.telegramMarker }) {
                    config.telegramMarker = it.trim().ifEmpty { "[TG]" }
                }
            ),
            FeatureSpec(
                "entry.ngb.minecraftMarker",
                "tooltip.ngb.minecraftMarker",
                strOpt("entry.ngb.minecraftMarker", "tooltip.ngb.minecraftMarker", { config.minecraftMarker }) {
                    config.minecraftMarker = it.ifEmpty { "." }
                }
            ),
            FeatureSpec(
                "entry.ngb.telegramLabel",
                null,
                strOpt("entry.ngb.telegramLabel", null, { config.telegramLabel }) { config.telegramLabel = it.ifEmpty { "[Telegram] " } }
            ),
            FeatureSpec(
                "entry.ngb.discordLabel",
                null,
                strOpt("entry.ngb.discordLabel", null, { config.discordLabel }) { config.discordLabel = it.ifEmpty { "[Discord] " } }
            ),
            FeatureSpec(
                "entry.ngb.minecraftLabel",
                null,
                strOpt("entry.ngb.minecraftLabel", null, { config.minecraftLabel }) { config.minecraftLabel = it.ifEmpty { "[Minecraft] " } }
            ),
            FeatureSpec(
                "entry.ngb.telegramLabelColor",
                null,
                strOpt("entry.ngb.telegramLabelColor", null, { config.telegramLabelColor }) { config.telegramLabelColor = it }
            ),
            FeatureSpec(
                "entry.ngb.discordLabelColor",
                null,
                strOpt("entry.ngb.discordLabelColor", null, { config.discordLabelColor }) { config.discordLabelColor = it }
            ),
            FeatureSpec(
                "entry.ngb.minecraftLabelColor",
                null,
                strOpt("entry.ngb.minecraftLabelColor", null, { config.minecraftLabelColor }) { config.minecraftLabelColor = it }
            ),
            FeatureSpec(
                "entry.ngb.wordHighlightEnabled",
                "tooltip.ngb.wordHighlightEnabled",
                boolOpt("entry.ngb.wordHighlightEnabled", "tooltip.ngb.wordHighlightEnabled", { config.wordHighlightEnabled }) {
                    config.wordHighlightEnabled = it
                }
            ),
            FeatureSpec(
                "entry.ngb.wordHighlightRules",
                "tooltip.ngb.wordHighlightRules",
                strOpt("entry.ngb.wordHighlightRules", "tooltip.ngb.wordHighlightRules", { config.wordHighlightRules }) { config.wordHighlightRules = it }
            ),
            FeatureSpec(
                "entry.ngb.wordHighlightOnlyMine",
                "tooltip.ngb.wordHighlightOnlyMine",
                boolOpt("entry.ngb.wordHighlightOnlyMine", "tooltip.ngb.wordHighlightOnlyMine", { config.wordHighlightOnlyMine }) {
                    config.wordHighlightOnlyMine = it
                }
            ),
            FeatureSpec(
                "entry.ngb.hideBotName",
                "tooltip.ngb.hideBotName",
                boolOpt("entry.ngb.hideBotName", "tooltip.ngb.hideBotName", { config.hideBotName }) { config.hideBotName = it }
            ),
            FeatureSpec(
                "entry.ngb.blockList",
                "tooltip.ngb.blockList",
                strOpt("entry.ngb.blockList", "tooltip.ngb.blockList", { config.blockList.joinToString(", ") }) { value ->
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
        val metadata = FabricLoader.getInstance().getModContainer("nayoguildbridge")
            .map { it.metadata }
            .orElse(null)
        val modName = metadata?.name ?: "NayoGuildBridge"
        val modVersion = metadata?.version?.friendlyString ?: "unknown"
        val modDescription = metadata?.description ?: "Bridge message formatter for client chat."
        val githubUrl = metadata?.contact?.get("sources")?.orElse(null) ?: "https://github.com/nayokage/bridgefilter"
        val discordUrl = metadata?.contact?.get("discord")?.orElse(null) ?: "https://discord.gg"

        val detailsGroup = OptionGroup.createBuilder()
            .name(Component.translatable("group.ngb.about.details"))
            .options(
                listOf(
                    infoRow("about.ngb.openName", modName),
                    infoRow("about.ngb.openVersion", modVersion),
                    infoRow("about.ngb.openDescription", modDescription)
                )
            )
            .build()

        val linksGroup = OptionGroup.createBuilder()
            .name(Component.translatable("group.ngb.about.links"))
            .options(
                listOf(
                    linkButton("about.ngb.openGithub", githubUrl),
                    linkButton("about.ngb.openDiscord", discordUrl)
                )
            )
                .build()

        return ConfigCategory.createBuilder()
            .name(Component.translatable("category.ngb.about"))
            .groups(listOf(detailsGroup, linksGroup))
            .build()
    }

    private fun linkButton(nameKey: String, url: String): ButtonOption {
        return ButtonOption.createBuilder()
            .name(Component.translatable(nameKey))
            .text(Component.translatable("about.ngb.open"))
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
            UiCategory.GENERAL -> "category.ngb.general"
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