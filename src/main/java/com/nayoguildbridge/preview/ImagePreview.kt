package com.nayoguildbridge.preview

import com.nayoguildbridge.NayoGuildBridge
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO

class ImagePreview(private val urls: List<String>) {
    private val url: String = urls.firstOrNull().orEmpty()
    private val textureId: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        "nayoguildbridge",
        "image_preview/${sha1(url)}"
    )

    @Volatile private var loading = false
    @Volatile private var failed = false
    @Volatile private var failureReason = "Не удалось загрузить изображение"
    @Volatile private var width = 0
    @Volatile private var height = 0

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

    fun render(context: GuiGraphics, client: Minecraft, maxWidth: Int, maxHeight: Int) {
        if (failed) {
            drawMessage(context, client, failureReason)
            return
        }
        if (width <= 0 || height <= 0) {
            drawMessage(context, client, "Загрузка изображения...")
            return
        }

        var scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        if (scale <= 0f) scale = 1f
        val scaledW = (width * scale).toInt().coerceAtLeast(1)
        val scaledH = (height * scale).toInt().coerceAtLeast(1)

        val x = ImagePreviewHandler.PADDING + 1
        val y = ImagePreviewHandler.PADDING + 1
        context.fill(
            ImagePreviewHandler.PADDING,
            ImagePreviewHandler.PADDING,
            ImagePreviewHandler.PADDING + scaledW + 2,
            ImagePreviewHandler.PADDING + scaledH + 2,
            0xCC000000.toInt()
        )
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            textureId,
            x, y,
            0f, 0f,
            scaledW, scaledH,
            width, height
        )
    }

    private fun drawMessage(context: GuiGraphics, client: Minecraft, text: String) {
        context.fill(4, 4, 220, 18, 0xCC000000.toInt())
        context.drawString(client.font, text, 8, 8, 0xFFFFFF)
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
