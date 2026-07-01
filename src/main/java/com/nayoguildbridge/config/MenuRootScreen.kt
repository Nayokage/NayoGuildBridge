package com.nayoguildbridge.config

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class MenuRootScreen(private val parent: Screen?) : Screen(Component.literal("NayoGuildBridge / Bridge Filter")) {

    override fun init() {
        super.init()
        clearWidgets()

        val centerX = width / 2
        var y = height / 4
        val w = 220
        val h = 20
        val gap = 24

        addRenderableWidget(
            Button.builder(Component.literal("General")) {
                Minecraft.getInstance().setScreen(GeneralScreen(this))
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap

        addRenderableWidget(
            Button.builder(Component.literal("Nick")) {
                Minecraft.getInstance().setScreen(NickStyleScreen(this))
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap

        addRenderableWidget(
            Button.builder(Component.literal("Words")) {
                Minecraft.getInstance().setScreen(WordsScreen(this))
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap

        addRenderableWidget(
            Button.builder(Component.literal("Blocklist")) {
                Minecraft.getInstance().setScreen(BlocklistScreen(this, NgbConfig.config.blockList.joinToString(", ")))
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap

        addRenderableWidget(
            Button.builder(Component.literal("Info / GitHub")) {
                Minecraft.getInstance().setScreen(InfoScreen(this))
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap * 2

        addRenderableWidget(
            Button.builder(Component.literal("Done")) {
                Minecraft.getInstance().setScreen(parent)
            }.bounds(centerX - 60, y, 120, h).build()
        )
    }

    override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractBackground(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.centeredText(font, title, width / 2, height / 4 - 30, 0xFFFFFF)
        guiGraphics.centeredText(
            font,
            Component.literal("Vanilla menu mode (works without Cloth Config)"),
            width / 2,
            height / 4 - 16,
            0xAAAAAA
        )
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick)
    }
}

