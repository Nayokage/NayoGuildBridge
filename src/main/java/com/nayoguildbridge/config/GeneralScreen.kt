package com.nayoguildbridge.config

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class GeneralScreen(private val parent: Screen?) : Screen(Component.literal("NayoGuildBridge / General")) {
    private var bridgeEnabled = NgbConfig.config.bridgeEnabled
    private var guildBridgeFormatEnabled = NgbConfig.config.guildBridgeFormatEnabled
    private var bridgeCommandFormatEnabled = NgbConfig.config.bridgeCommandFormatEnabled

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
            Button.builder(Component.literal("Done")) {
                applyAndSave()
                Minecraft.getInstance().setScreen(parent)
            }.bounds(centerX - 60, y, 120, h).build()
        )
    }

    private fun applyAndSave() {
        val cfg = NgbConfig.config
        cfg.bridgeEnabled = bridgeEnabled
        cfg.guildBridgeFormatEnabled = guildBridgeFormatEnabled
        cfg.bridgeCommandFormatEnabled = bridgeCommandFormatEnabled
        NgbConfig.save()
    }

    override fun onClose() {
        applyAndSave()
        Minecraft.getInstance().setScreen(parent)
    }

    override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractBackground(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.centeredText(font, title, width / 2, height / 4 - 30, 0xFFFFFF)
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun label(name: String, enabled: Boolean) = "$name: " + if (enabled) "ON" else "OFF"
}
