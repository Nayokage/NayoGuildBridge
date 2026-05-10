package com.nayoguildbridge.config

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

// Ет отвечает за предпросмотр §-кодов и цветов
class StylePreviewScreen(private val parent: Screen?) : Screen(Component.literal("§ Codes Preview")) {

    override fun init() {
        super.init()
        clearWidgets()

        addRenderableWidget(
            Button.builder(Component.literal("Back")) {
                Minecraft.getInstance().setScreen(parent)
            }.bounds(width / 2 - 60, height - 28, 120, 20).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)

        guiGraphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF)

        var y = 36
        guiGraphics.drawString(font, Component.literal("Colors:"), 16, y, 0xFFFFFF)
        y += 12

        val colorCodes = listOf(
            "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7",
            "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f"
        )
        for (i in colorCodes.indices) {
            val code = colorCodes[i]
            val text = Component.literal("$code  $code ExampleText")
            val x = 16 + (i % 2) * (width / 2 - 16)
            if (i % 2 == 0 && i > 0) y += 12
            guiGraphics.drawString(font, text, x, y, 0xFFFFFF)
        }

        y += 24
        guiGraphics.drawString(font, Component.literal("Styles:"), 16, y, 0xFFFFFF)
        y += 12
        guiGraphics.drawString(font, Component.literal("§l  §lBold"), 16, y, 0xFFFFFF); y += 12
        guiGraphics.drawString(font, Component.literal("§o  §oItalic"), 16, y, 0xFFFFFF); y += 12
        guiGraphics.drawString(font, Component.literal("§n  §nUnderline"), 16, y, 0xFFFFFF); y += 12
        guiGraphics.drawString(font, Component.literal("§m  §mStrikethrough"), 16, y, 0xFFFFFF); y += 12
        guiGraphics.drawString(font, Component.literal("§k  §kObfuscated"), 16, y, 0xFFFFFF); y += 12
        guiGraphics.drawString(font, Component.literal("§r  Reset (back to normal)"), 16, y, 0xFFFFFF); y += 12

        y += 12
        guiGraphics.drawString(
            font,
            Component.literal("Example for nick codes: \"§4§l\" -> §4§lSomeNick§r"),
            16,
            y,
            0xFFFFFF
        )

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }
}

