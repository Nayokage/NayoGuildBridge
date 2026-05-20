package com.nayoguildbridge.config

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.Util
import net.minecraft.network.chat.Component

class InfoScreen(private val parent: Screen?) : Screen(Component.literal("NayoGuildBridge / Info")) {
    override fun init() {
        super.init()
        clearWidgets()

        val centerX = width / 2
        var y = height / 4
        val w = 260
        val h = 20
        val gap = 24

        addRenderableWidget(
            Button.builder(Component.literal("Print info to chat")) {
                val player = Minecraft.getInstance().player
                player?.displayClientMessage(Component.literal("§b[NayoGuildBridge] §fSections:"), false)
                player?.displayClientMessage(Component.literal("§7- §fGeneral: enable bridge, markers"), false)
                player?.displayClientMessage(Component.literal("§7- §fNick: apply § codes / color to sender nick"), false)
                player?.displayClientMessage(Component.literal("§7- §fWords: highlight words using rules like scam=§4§l"), false)
                player?.displayClientMessage(Component.literal("§7- §fBlocklist: hide bridged messages that contain blocked entries"), false)
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap

        addRenderableWidget(
            Button.builder(Component.literal("Copy GitHub repo link")) {
                val url = "https://github.com/Nayokage/BridgeFilter"
                Minecraft.getInstance().keyboardHandler.setClipboard(url)
                Minecraft.getInstance().player?.displayClientMessage(
                    Component.literal("§a[NayoGuildBridge] §fGitHub link copied: §b$url"),
                    false
                )
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap

        addRenderableWidget(
            Button.builder(Component.literal("JoJoBA Discord server")) {
                Util.getPlatform().openUri("https://discord.gg/fgWeesWQd6")
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap

        addRenderableWidget(
            Button.builder(Component.literal("TheSquidsEmpire Discord server")) {
                Util.getPlatform().openUri("https://discord.gg/sbAmzT8VPM")
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap

        addRenderableWidget(
            Button.builder(Component.literal("Секретная Кнопка")) {
                Util.getPlatform().openUri("https://youtu.be/WApeMUANO3s")
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap

        addRenderableWidget(
            Button.builder(Component.literal("Ютюб Канал Ватера")) {
                Util.getPlatform().openUri("https://www.youtube.com/@WaterSpais/videos")
            }.bounds(centerX - w / 2, y, w, h).build()
        )
        y += gap * 2

        addRenderableWidget(
            Button.builder(Component.literal("Back")) {
                Minecraft.getInstance().setScreen(parent)
            }.bounds(centerX - 60, y, 120, h).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF)
        guiGraphics.drawString(font, Component.literal("This menu is split into sections."), 16, 40, 0xAAAAAA)
        guiGraphics.drawString(font, Component.literal("Use 'Print info to chat' for a quick explanation."), 16, 52, 0xAAAAAA)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }
}

