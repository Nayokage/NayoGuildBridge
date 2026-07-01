package com.nayoguildbridge.config

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class BlocklistScreen(
    private val parent: Screen,
    initial: String
) : Screen(Component.literal("Blocklist manager")) {

    private val items: MutableList<String> = initial
        .split(" ", "\n", "\t", ",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toMutableList()

    private var page = 0
    private val perPage = 8

    private lateinit var addBox: EditBox

    override fun init() {
        super.init()
        clearWidgets()

        val centerX = width / 2
        val top = height / 4
        val w = 260
        val h = 20
        val gap = 22

        addBox = EditBox(font, centerX - w / 2, top, w - 70, h, Component.literal("Nick"))
        addRenderableWidget(addBox)

        addRenderableWidget(
            Button.builder(Component.literal("Add")) {
                val v = addBox.value.trim()
                if (v.isNotEmpty() && !items.contains(v)) {
                    items.add(v)
                    addBox.value = ""
                }
            }.bounds(centerX - w / 2 + (w - 70) + 6, top, 64, h).build()
        )

        var y = top + gap * 2

        val startIdx = page * perPage
        val slice = items.drop(startIdx).take(perPage)
        for ((i, s) in slice.withIndex()) {
            val rowY = y + i * gap
            addRenderableWidget(
                Button.builder(Component.literal("Remove: $s")) {
                    items.remove(s)
                    if (page > 0 && page * perPage >= items.size) page--
                    Minecraft.getInstance().setScreen(BlocklistScreen(parent, items.joinToString(" ")))
                }.bounds(centerX - w / 2, rowY, w, h).build()
            )
        }

        val bottomY = height - 40
        addRenderableWidget(
            Button.builder(Component.literal("< Prev")) {
                if (page > 0) page--
                Minecraft.getInstance().setScreen(BlocklistScreen(parent, items.joinToString(" ")).also { it.page = page })
            }.bounds(centerX - w / 2, bottomY, 80, h).build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Next >")) {
                val maxPage = if (items.isEmpty()) 0 else (items.size - 1) / perPage
                if (page < maxPage) page++
                Minecraft.getInstance().setScreen(BlocklistScreen(parent, items.joinToString(" ")).also { it.page = page })
            }.bounds(centerX + w / 2 - 80, bottomY, 80, h).build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Done")) {
                NgbConfig.config.blockList = items.toList()
                NgbConfig.save()
                Minecraft.getInstance().setScreen(parent)
            }.bounds(centerX - 40, bottomY, 80, h).build()
        )
    }

    override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractBackground(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.centeredText(font, title, width / 2, height / 4 - 20, 0xFFFFFF)
        guiGraphics.centeredText(
            font,
            Component.literal("Total: ${items.size} (page ${page + 1})"),
            width / 2,
            height / 4 - 8,
            0xAAAAAA
        )
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick)
    }
}

