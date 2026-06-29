package com.nayoguildbridge.config

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class VanillaConfigScreen(private val parent: Screen?) : Screen(Component.literal("NayoGuildBridge / Bridge Filter")) {

    private var bridgeEnabled = NgbConfig.config.bridgeEnabled
    private var blockList = NgbConfig.config.blockList.joinToString(" ")
    private var nickHighlightEnabled = NgbConfig.config.nickHighlightEnabled
    private var guildBridgeFormatEnabled = NgbConfig.config.guildBridgeFormatEnabled
    private var bridgeCommandFormatEnabled = NgbConfig.config.bridgeCommandFormatEnabled
    private var senderNickColorEnabled = NgbConfig.config.senderNickColorEnabled
    private var senderNickLegacyEnabled = NgbConfig.config.senderNickLegacyEnabled
    private var senderNickLegacyCodes = NgbConfig.config.senderNickLegacyCodes
    private var senderNickStyleOnlyMine = NgbConfig.config.senderNickStyleOnlyMine
    private var myNickAliases = NgbConfig.config.myNickAliases.joinToString(", ")
    private var wordHighlightEnabled = NgbConfig.config.wordHighlightEnabled
    private var wordHighlightRules = NgbConfig.config.wordHighlightRules
    private var wordHighlightOnlyMine = NgbConfig.config.wordHighlightOnlyMine

    private lateinit var blockListBox: EditBox
    private lateinit var legacyCodesBox: EditBox
    private lateinit var wordRulesBox: EditBox
    private lateinit var myAliasesBox: EditBox

    override fun init() {
        super.init()
        clearWidgets()

        val centerX = width / 2
        var y = height / 4
        val w = 260
        val h = 20
        val gap = 24

        addRenderableWidget(
            Button.builder(Component.literal(label("Bridge enabled", bridgeEnabled))) { b ->
                bridgeEnabled = !bridgeEnabled
                b.message = Component.literal(label("Bridge enabled", bridgeEnabled))
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap

        blockListBox = EditBox(font, centerX - w / 2, y, w, h, Component.literal("Blocklist"))
        blockListBox.value = blockList
        addRenderableWidget(blockListBox)
        y += gap

        addRenderableWidget(
            Button.builder(Component.literal(label("Nick highlight (Guild)", nickHighlightEnabled))) { b ->
                nickHighlightEnabled = !nickHighlightEnabled
                b.message = Component.literal(label("Nick highlight (Guild)", nickHighlightEnabled))
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap

        addRenderableWidget(
            Button.builder(Component.literal(label("Color sender nick", senderNickColorEnabled))) { b ->
                senderNickColorEnabled = !senderNickColorEnabled
                b.message = Component.literal(label("Color sender nick", senderNickColorEnabled))
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap

        addRenderableWidget(
            Button.builder(Component.literal(label("Use § codes for sender nick", senderNickLegacyEnabled))) { b ->
                senderNickLegacyEnabled = !senderNickLegacyEnabled
                b.message = Component.literal(label("Use § codes for sender nick", senderNickLegacyEnabled))
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap

        legacyCodesBox = EditBox(font, centerX - w / 2, y, w, h, Component.literal("Sender nick § codes"))
        legacyCodesBox.value = senderNickLegacyCodes
        addRenderableWidget(legacyCodesBox)
        y += gap

        addRenderableWidget(
            Button.builder(Component.literal(label("Nick style only mine", senderNickStyleOnlyMine))) { b ->
                senderNickStyleOnlyMine = !senderNickStyleOnlyMine
                b.message = Component.literal(label("Nick style only mine", senderNickStyleOnlyMine))
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap

        myAliasesBox = EditBox(font, centerX - w / 2, y, w, h, Component.literal("My nick aliases (comma)"))
        myAliasesBox.value = myNickAliases
        addRenderableWidget(myAliasesBox)
        y += gap

        addRenderableWidget(
            Button.builder(Component.literal("Color codes / examples")) {
                Minecraft.getInstance().setScreen(StylePreviewScreen(this))
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap

        addRenderableWidget(
            Button.builder(Component.literal(label("Format Guild Bridge", guildBridgeFormatEnabled))) { b ->
                guildBridgeFormatEnabled = !guildBridgeFormatEnabled
                b.message = Component.literal(label("Format Guild Bridge", guildBridgeFormatEnabled))
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap

        addRenderableWidget(
            Button.builder(Component.literal(label("Format Bridge commands", bridgeCommandFormatEnabled))) { b ->
                bridgeCommandFormatEnabled = !bridgeCommandFormatEnabled
                b.message = Component.literal(label("Format Bridge commands", bridgeCommandFormatEnabled))
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap * 2

        addRenderableWidget(
            Button.builder(Component.literal(label("Highlight words", wordHighlightEnabled))) { b ->
                wordHighlightEnabled = !wordHighlightEnabled
                b.message = Component.literal(label("Highlight words", wordHighlightEnabled))
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap

        addRenderableWidget(
            Button.builder(Component.literal(label("Highlight words only mine", wordHighlightOnlyMine))) { b ->
                wordHighlightOnlyMine = !wordHighlightOnlyMine
                b.message = Component.literal(label("Highlight words only mine", wordHighlightOnlyMine))
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap

        wordRulesBox = EditBox(font, centerX - w / 2, y, w, h, Component.literal("word=§c§l; scam=§4§l"))
        wordRulesBox.value = wordHighlightRules
        addRenderableWidget(wordRulesBox)
        y += gap

        addRenderableWidget(
            Button.builder(Component.literal("Done")) {
                applyAndSave()
                Minecraft.getInstance().setScreen(parent)
            }.bounds(centerX - 60, y, 120, h).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, height / 4 - 30, 0xFFFFFF)
        guiGraphics.drawCenteredString(
            font,
            Component.literal("Cloth Config not installed: using vanilla menu"),
            width / 2,
            height / 4 - 16,
            0xAAAAAA
        )
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun onClose() {
        applyAndSave()
        Minecraft.getInstance().setScreen(parent)
    }

    private fun applyAndSave() {
        val cfg = NgbConfig.config
        cfg.bridgeEnabled = bridgeEnabled
        cfg.blockList = blockListBox.value
            .split("\n", ",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        cfg.nickHighlightEnabled = nickHighlightEnabled
        cfg.guildBridgeFormatEnabled = guildBridgeFormatEnabled
        cfg.bridgeCommandFormatEnabled = bridgeCommandFormatEnabled
        cfg.senderNickColorEnabled = senderNickColorEnabled
        cfg.senderNickLegacyEnabled = senderNickLegacyEnabled
        cfg.senderNickLegacyCodes = legacyCodesBox.value.ifEmpty { "§4" }
        cfg.senderNickStyleOnlyMine = senderNickStyleOnlyMine
        cfg.myNickAliases = myAliasesBox.value
            .split("\n", ",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        cfg.wordHighlightEnabled = wordHighlightEnabled
        cfg.wordHighlightRules = wordRulesBox.value
        cfg.wordHighlightOnlyMine = wordHighlightOnlyMine
        NgbConfig.save()
    }

    private fun label(name: String, enabled: Boolean) = "$name: " + if (enabled) "ON" else "OFF"

    fun setBlocklist(value: String) {
        if (this::blockListBox.isInitialized) {
            blockListBox.value = value
        }
        blockList = value
    }
}

