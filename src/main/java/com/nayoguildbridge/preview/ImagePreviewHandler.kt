package com.nayoguildbridge.preview

import com.nayoguildbridge.config.NgbConfig
import com.nayoguildbridge.qol.ChatQoL
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Style
import java.net.URI
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object ImagePreviewHandler {
    const val PADDING = 5
    const val IMAGE_PREVIEW_INSERTION = "nayoguildbridge:image-preview:"

    private val previews = ConcurrentHashMap<String, ImagePreview>()
    private val previewableUrls: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap())

    fun register() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is ChatScreen) {
                ScreenEvents.afterRender(screen).register(::render)
            }
        }
    }

    fun registerImageUrl(imageUrl: String) {
        previewableUrls.add(URI.create(imageUrl).toString())
    }

    private fun render(screen: Screen, context: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
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

        val maxW = if (client.hasControlDown()) {
            client.window.guiScaledWidth - PADDING * 2 - 2
        } else {
            280
        }
        val maxH = if (client.hasControlDown()) {
            client.window.guiScaledHeight - PADDING * 2 - 2
        } else {
            200
        }
        preview.render(context, client, maxW.coerceAtLeast(1), maxH.coerceAtLeast(1))
    }

    private fun getHoveredStyle(client: Minecraft, mouseX: Int, mouseY: Int): Style? {
        val chat = client.gui.chat
        return try {
            chat.getClickedComponentStyleAt(mouseX.toDouble(), mouseY.toDouble())
        } catch (_: Throwable) {
            try {
                val m = chat.javaClass.getMethod(
                    "getTextStyleAt",
                    Double::class.javaPrimitiveType,
                    Double::class.javaPrimitiveType
                )
                m.invoke(chat, mouseX.toDouble(), mouseY.toDouble()) as? Style
            } catch (_: Throwable) {
                null
            }
        }
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
