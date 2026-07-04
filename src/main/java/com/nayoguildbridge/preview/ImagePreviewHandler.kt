package com.nayoguildbridge.preview

import com.nayoguildbridge.config.NgbConfig
import com.nayoguildbridge.qol.ChatQoL
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ActiveTextCollector
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.Util
import java.net.URI
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object ImagePreviewHandler {
    const val PADDING = 5
    const val IMAGE_PREVIEW_INSERTION = "nayoguildbridge:image-preview:"
    private const val PREVIEW_CMD_PREFIX = "ngbimage open "

    private val previews = ConcurrentHashMap<String, ImagePreview>()
    private val previewableUrls: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap())
    private val previewTokens = ConcurrentHashMap<String, List<String>>()

    fun register() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is ChatScreen) {
                ScreenEvents.afterExtract(screen).register(::render)
            }
        }
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
        previews.computeIfAbsent(token) { ImagePreview(clean) }
        return token
    }

    fun openPreview(token: String) {
        val urls = previewTokens[token] ?: return
        val client = Minecraft.getInstance()
        client.execute {
            client.setScreen(ImagePreviewScreen(urls, client.screen))
        }
    }

    private fun render(screen: Screen, context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
        if (!NgbConfig.config.imagePreviewEnabled) return
        val client = Minecraft.getInstance()
        if (screen !is ChatScreen || client.level == null) return

        val style = getHoveredStyle(client, mouseX, mouseY) ?: return
        val imageUrl = resolveImageUrl(style) ?: return

        val token = ChatQoL.mediaTokenFromApiUrl(imageUrl)
        val previewKey = token ?: imageUrl
        val loadUrls = if (token != null) ChatQoL.mediaUrlsForToken(token) else listOf(imageUrl)
        val preview = previews.computeIfAbsent(previewKey) { ImagePreview(loadUrls) }
        preview.load(client)

        val fullScreen = client.hasControlDown()
        val (maxW, maxH) = ImagePreview.computeChatPreviewBounds(client, fullScreen)
        val (scaledW, scaledH) = preview.scaledSize(maxW, maxH)
        val screenW = client.window.guiScaledWidth
        val x = (screenW - scaledW - PADDING).coerceAtLeast(PADDING)
        val y = PADDING + 1
        preview.renderAt(context, client, x, y, maxW, maxH)
    }

    private fun getHoveredStyle(client: Minecraft, mouseX: Int, mouseY: Int): Style? {
        val chat = client.gui.chat
        return getDrawnTextHoveredStyle(client, chat, mouseX, mouseY)
            ?: getLegacyHoveredStyle(chat, mouseX, mouseY)
    }

    private fun getDrawnTextHoveredStyle(
        client: Minecraft,
        chat: ChatComponent,
        mouseX: Int,
        mouseY: Int,
    ): Style? {
        return try {
            var finder = ActiveTextCollector.ClickableStyleFinder(client.font, mouseX, mouseY)
            finder = finder.includeInsertions(client.hasShiftDown())
            chat.captureClickableText(
                finder,
                client.window.guiScaledHeight,
                client.gui.guiTicks,
                ChatComponent.DisplayMode.FOREGROUND,
            )
            finder.result()
        } catch (_: Throwable) {
            null
        }
    }

    private fun getLegacyHoveredStyle(chat: ChatComponent, mouseX: Int, mouseY: Int): Style? {
        for (methodName in arrayOf("getTextStyleAt", "getClickedComponentStyleAt")) {
            try {
                val method = chat.javaClass.getMethod(
                    methodName,
                    Double::class.javaPrimitiveType,
                    Double::class.javaPrimitiveType,
                )
                return method.invoke(chat, mouseX.toDouble(), mouseY.toDouble()) as? Style
            } catch (_: Throwable) {
                // try next legacy name
            }
        }
        return null
    }

    private fun resolveImageUrl(style: Style): String? {
        val insertion = style.insertion
        if (!insertion.isNullOrBlank() && insertion.startsWith(IMAGE_PREVIEW_INSERTION)) {
            return insertion.substring(IMAGE_PREVIEW_INSERTION.length)
        }
        val click = style.clickEvent
        if (click is ClickEvent.OpenUrl) {
            val url = click.uri().toString()
            if (previewableUrls.contains(url) || ChatQoL.isImageUrl(url)) {
                return url
            }
        }
        return null
    }
}

class ImagePreviewScreen(
    private val urls: List<String>,
    private val parent: Screen?,
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
                Minecraft.getInstance().setScreen(parent)
            }.bounds(left + btnW + gap, top, btnW, 20).build()
        )
    }

    override fun extractBackground(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, 0xC0101010.toInt())
    }

    override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractBackground(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.centeredText(font, title, width / 2, 12, 0xFFFFFF)

        if (!NgbConfig.config.imagePreviewEnabled) {
            guiGraphics.centeredText(
                font,
                Component.literal("Превью изображений выключено в настройках"),
                width / 2,
                height / 2,
                0xAAAAAA,
            )
            super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick)
            return
        }

        val client = Minecraft.getInstance()
        preview.load(client)
        val (maxW, maxH) = ImagePreview.computeScreenPreviewBounds(width, height)
        val (scaledW, scaledH) = preview.scaledSize(maxW, maxH)
        val x = ((width - scaledW) / 2).coerceAtLeast(8)
        val y = 28 + ((height - 28 - 48 - scaledH) / 2).coerceAtLeast(8)
        preview.renderAt(guiGraphics, client, x, y, maxW, maxH)

        if (preview.isReady()) {
            guiGraphics.centeredText(font, Component.literal("Esc — закрыть"), width / 2, height - 52, 0x888888)
        }

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick)
    }

    override fun isPauseScreen(): Boolean = false

    override fun onClose() {
        Minecraft.getInstance().setScreen(parent)
    }
}
