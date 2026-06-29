package com.nayoguildbridge.preview

import com.nayoguildbridge.config.NgbConfig
import com.nayoguildbridge.qol.ChatQoL
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import java.net.URI
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object ImagePreviewHandler {
    const val IMAGE_PREVIEW_INSERTION = "nayoguildbridge:image-preview:"
    private const val PREVIEW_CMD_PREFIX = "ngbimage open "

    private val previewableUrls: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap())
    private val previewTokens = ConcurrentHashMap<String, List<String>>()

    fun register() {
        // Preview opens in ImagePreviewScreen on click.
    }

    fun registerImageUrl(imageUrl: String) {
        previewableUrls.add(URI.create(imageUrl).toString())
    }

    fun previewCommand(token: String): String = "/$PREVIEW_CMD_PREFIX$token"

    fun registerPreviewUrls(urls: List<String>): String {
        val clean = urls.filter { it.isNotBlank() }.distinct()
        if (clean.isEmpty()) return ""
        clean.forEach { registerImageUrl(it) }
        val token = clean.first().hashCode().toUInt().toString(16)
        previewTokens[token] = clean
        return token
    }

    fun openPreview(token: String) {
        val urls = previewTokens[token] ?: return
        val client = Minecraft.getInstance()
        client.execute {
            client.setScreen(ImagePreviewScreen(urls, client.screen))
        }
    }

    fun resolveImageUrlFromStyle(style: net.minecraft.network.chat.Style): String? {
        val insertion = style.insertion
        if (!insertion.isNullOrBlank() && insertion.startsWith(IMAGE_PREVIEW_INSERTION)) {
            return insertion.substring(IMAGE_PREVIEW_INSERTION.length)
        }
        val click = style.clickEvent
        if (click is net.minecraft.network.chat.ClickEvent.OpenUrl) {
            val url = click.uri().toString()
            if (previewableUrls.contains(url) || ChatQoL.isImageUrl(url)) {
                return url
            }
        }
        if (click is net.minecraft.network.chat.ClickEvent.RunCommand) {
            val cmd = click.command().trim()
            if (cmd.startsWith(PREVIEW_CMD_PREFIX)) {
                val token = cmd.removePrefix(PREVIEW_CMD_PREFIX).trim()
                return previewTokens[token]?.firstOrNull()
            }
        }
        return null
    }
}

class ImagePreviewScreen(
    private val urls: List<String>,
    private val parent: Screen?
) : Screen(Component.literal("Скриншот")) {

    private val preview = ImagePreview(urls)

    override fun init() {
        super.init()
        clearWidgets()

        val btnW = 130
        val gap = 8
        val totalW = btnW * 2 + gap
        val left = width / 2 - totalW / 2
        val top = height - 36

        addRenderableWidget(
            Button.builder(Component.literal("Открыть в браузере")) {
                urls.firstOrNull()?.let { Util.getPlatform().openUri(URI.create(it)) }
            }.bounds(left, top, btnW, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Закрыть")) {
                minecraft.setScreen(parent)
            }.bounds(left + btnW + gap, top, btnW, 20).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF)

        if (!NgbConfig.config.imagePreviewEnabled) {
            guiGraphics.drawCenteredString(
                font,
                "Превью изображений выключено в настройках",
                width / 2,
                height / 2,
                0xAAAAAA
            )
            super.render(guiGraphics, mouseX, mouseY, partialTick)
            return
        }

        preview.load(minecraft)
        val maxW = (width * 0.86).toInt().coerceAtLeast(64)
        val maxH = (height * 0.68).toInt().coerceAtLeast(64)
        val (scaledW, scaledH) = preview.scaledSize(maxW, maxH)
        val x = (width - scaledW) / 2
        val y = 28 + ((height - 28 - 48 - scaledH) / 2).coerceAtLeast(8)
        preview.renderAt(guiGraphics, minecraft, x, y, maxW, maxH)

        if (preview.isReady()) {
            guiGraphics.drawCenteredString(font, "Esc — закрыть", width / 2, height - 52, 0x888888)
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun isPauseScreen(): Boolean = false

    override fun onClose() {
        minecraft.setScreen(parent)
    }
}
