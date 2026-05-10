package com.nayoguildbridge.config

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class NickStyleScreen(private val parent: Screen?) : Screen(Component.literal("NayoGuildBridge / Nick")) {
    private var senderNickColorEnabled = ChatBridgeConfig.config.senderNickColorEnabled
    private var senderNickLegacyEnabled = ChatBridgeConfig.config.senderNickLegacyEnabled
    private var senderNickStyleOnlyMine = ChatBridgeConfig.config.senderNickStyleOnlyMine
    private var senderNickLegacyCodes = ChatBridgeConfig.config.senderNickLegacyCodes
    private var myNickAliases = ChatBridgeConfig.config.myNickAliases.joinToString(", ")

    private lateinit var legacyCodesBox: EditBox
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
        y += gap * 2

        addRenderableWidget(
            Button.builder(Component.literal("Done")) {
                applyAndSave()
                Minecraft.getInstance().setScreen(parent)
            }.bounds(centerX - 60, y, 120, h).build()
        )
    }

    private fun applyAndSave() {
        val cfg = ChatBridgeConfig.config
        cfg.senderNickColorEnabled = senderNickColorEnabled
        cfg.senderNickLegacyEnabled = senderNickLegacyEnabled
        cfg.senderNickLegacyCodes = legacyCodesBox.value.ifEmpty { "§4" }
        cfg.senderNickStyleOnlyMine = senderNickStyleOnlyMine
        cfg.myNickAliases = myAliasesBox.value
            .split("\n", ",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        ChatBridgeConfig.save()
    }

    override fun onClose() {
        applyAndSave()
        Minecraft.getInstance().setScreen(parent)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, height / 4 - 30, 0xFFFFFF)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun label(name: String, enabled: Boolean) = "$name: " + if (enabled) "ON" else "OFF"
}

