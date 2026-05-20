package com.nayoguildbridge.config

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class WordsScreen(private val parent: Screen?) : Screen(Component.literal("NayoGuildBridge / Words")) {
    private var wordHighlightEnabled = NgbConfig.config.wordHighlightEnabled
    private var wordHighlightOnlyMine = NgbConfig.config.wordHighlightOnlyMine
    private var wordHighlightRules = NgbConfig.config.wordHighlightRules

    private lateinit var rulesBox: EditBox

    override fun init() {
        super.init()
        clearWidgets()

        val centerX = width / 2
        var y = height / 4
        val w = 260
        val h = 20
        val gap = 24

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

        rulesBox = EditBox(font, centerX - w / 2, y, w, h, Component.literal("word=§c§l; scam=§4§l"))
        rulesBox.value = wordHighlightRules
        addRenderableWidget(rulesBox)
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
        val cfg = NgbConfig.config
        cfg.wordHighlightEnabled = wordHighlightEnabled
        cfg.wordHighlightOnlyMine = wordHighlightOnlyMine
        cfg.wordHighlightRules = rulesBox.value
        NgbConfig.save()
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

