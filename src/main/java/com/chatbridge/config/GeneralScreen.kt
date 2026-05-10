package com.nayoguildbridge.config

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class GeneralScreen(private val parent: Screen?) : Screen(Component.literal("NayoGuildBridge / General")) {
    private var bridgeEnabled = ChatBridgeConfig.config.bridgeEnabled
    private var guildBridgeFormatEnabled = ChatBridgeConfig.config.guildBridgeFormatEnabled
    private var bridgeCommandFormatEnabled = ChatBridgeConfig.config.bridgeCommandFormatEnabled
    private var telegramMarker = ChatBridgeConfig.config.telegramMarker
    private var minecraftMarker = ChatBridgeConfig.config.minecraftMarker

    private lateinit var tgMarkerBox: EditBox
    private lateinit var mcMarkerBox: EditBox

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
        y += gap

        val markerW = (w - 6) / 2
        tgMarkerBox = EditBox(font, centerX - w / 2, y, markerW, h, Component.literal("TG marker"))
        tgMarkerBox.value = telegramMarker
        addRenderableWidget(tgMarkerBox)
        mcMarkerBox = EditBox(font, centerX - w / 2 + markerW + 6, y, markerW, h, Component.literal("MC marker"))
        mcMarkerBox.value = minecraftMarker
        addRenderableWidget(mcMarkerBox)
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
        cfg.bridgeEnabled = bridgeEnabled
        cfg.guildBridgeFormatEnabled = guildBridgeFormatEnabled
        cfg.bridgeCommandFormatEnabled = bridgeCommandFormatEnabled
        cfg.telegramMarker = tgMarkerBox.value.trim().ifEmpty { "[TG]" }
        cfg.minecraftMarker = mcMarkerBox.value.ifEmpty { "." }
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

