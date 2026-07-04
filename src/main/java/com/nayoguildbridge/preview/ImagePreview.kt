package com.nayoguildbridge.preview

import com.nayoguildbridge.NayoGuildBridge
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO

class ImagePreview(private val urls: List<String>) {
    private val url: String = urls.firstOrNull().orEmpty()
    private val textureId: Identifier = Identifier.fromNamespaceAndPath(
        "nayoguildbridge",
        "image_preview/${sha1(url)}"
    )

    @Volatile private var loading = false
    @Volatile private var failed = false
    @Volatile private var failureReason = "Не удалось загрузить изображение"
    @Volatile private var width = 0
    @Volatile private var height = 0

    fun isReady(): Boolean = width > 0 && height > 0

    fun load(client: Minecraft) {
        if (loading || failed || width > 0) return
        loading = true
        CompletableFuture.supplyAsync { download(url) }.whenComplete { bytes, err ->
            if (err != null || bytes == null) {
                failureReason = err?.message ?: "Пустой ответ"
                failed = true
                loading = false
                return@whenComplete
            }
            client.execute {
                try {
                    val native = NativeImage.read(bytes)
                    width = native.width
                    height = native.height
                    val texture = DynamicTexture({ "ngb-preview-$url" }, native)
                    client.textureManager.register(textureId, texture)
                } catch (t: Throwable) {
                    failureReason = t.message ?: "Ошибка декодирования"
                    failed = true
                } finally {
                    loading = false
                }
            }
        }
    }

    fun render(context: GuiGraphicsExtractor, client: Minecraft, maxWidth: Int, maxHeight: Int) {
        renderAt(
            context,
            client,
            ImagePreviewHandler.PADDING + 1,
            ImagePreviewHandler.PADDING + 1,
            maxWidth,
            maxHeight,
        )
    }

    fun renderAt(
        context: GuiGraphicsExtractor,
        client: Minecraft,
        x: Int,
        y: Int,
        maxWidth: Int,
        maxHeight: Int,
    ) {
        if (failed) {
            drawMessageAt(context, client, failureReason, x, y)
            return
        }
        if (width <= 0 || height <= 0) {
            drawMessageAt(context, client, "Загрузка изображения...", x, y)
            return
        }

        val (scaledW, scaledH) = scaledSize(maxWidth, maxHeight)
        val frameLeft = x - 1
        val frameTop = y - 1
        context.fill(
            frameLeft,
            frameTop,
            frameLeft + scaledW + 2,
            frameTop + scaledH + 2,
            0xE6000000.toInt()
        )
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            textureId,
            x, y,
            0f, 0f,
            scaledW, scaledH,
            width, height,
            width, height,
        )
    }

    fun scaledSize(maxWidth: Int, maxHeight: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 1 to 1
        val safeMaxW = maxWidth.coerceAtLeast(1)
        val safeMaxH = maxHeight.coerceAtLeast(1)
        var scale = minOf(safeMaxW.toFloat() / width, safeMaxH.toFloat() / height, 1f)
        if (scale <= 0f) scale = 1f
        val scaledW = (width * scale).toInt().coerceIn(1, safeMaxW)
        val scaledH = (height * scale).toInt().coerceIn(1, safeMaxH)
        return scaledW to scaledH
    }

    companion object {
        fun computeChatPreviewBounds(client: Minecraft, fullScreen: Boolean): Pair<Int, Int> {
            val screenW = client.window.guiScaledWidth.coerceAtLeast(1)
            val screenH = client.window.guiScaledHeight.coerceAtLeast(1)
            val pad = ImagePreviewHandler.PADDING
            if (fullScreen) {
                return (screenW - pad * 2 - 2).coerceAtLeast(1) to (screenH - pad * 2 - 2).coerceAtLeast(1)
            }
            val maxW = (screenW - pad * 2 - 8).coerceIn(64, screenW - pad * 2)
            val maxH = minOf(
                (screenH * 0.55).toInt(),
                (maxW * 1.25).toInt(),
                screenH - pad * 2 - 24,
            ).coerceIn(48, screenH - pad * 2)
            return maxW to maxH
        }

        fun computeScreenPreviewBounds(screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
            val maxW = (screenWidth - 16).coerceAtLeast(64)
            val maxH = (screenHeight - 28 - 48 - 8).coerceAtLeast(64)
            return maxW to maxH
        }
    }

    private fun drawMessageAt(context: GuiGraphicsExtractor, client: Minecraft, text: String, x: Int, y: Int) {
        val w = client.font.width(text) + 12
        context.fill(x - 4, y - 4, x + w, y + client.font.lineHeight + 6, 0xE6000000.toInt())
        context.text(client.font, text, x, y, 0xFFFFFF)
    }

    private fun download(imageUrl: String): ByteArray? {
        val candidates = if (urls.isNotEmpty()) urls else listOf(imageUrl)
        for (candidate in candidates.distinct()) {
            downloadOne(candidate)?.let { return it }
        }
        return null
    }

    private fun downloadOne(imageUrl: String): ByteArray? {
        return try {
            val conn = URI(imageUrl).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", "NayoGuildBridge/1.1")
            conn.setRequestProperty("Accept", "image/*,*/*;q=0.8")
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { it.readBytes() }
        } catch (t: Throwable) {
            NayoGuildBridge.logger.debug("[NGB] image download: ${t.message}")
            try {
                val img = ImageIO.read(URI(imageUrl).toURL())
                if (img == null) return null
                val out = java.io.ByteArrayOutputStream()
                ImageIO.write(img, "png", out)
                out.toByteArray()
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
