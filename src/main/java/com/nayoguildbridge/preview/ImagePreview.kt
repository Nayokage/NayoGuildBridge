package com.nayoguildbridge.preview

import com.mojang.blaze3d.platform.NativeImage
import com.nayoguildbridge.NayoGuildBridge
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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

    fun isReady(): Boolean = width > 0 && height > 0
    fun isLoading(): Boolean = loading
    fun isFailed(): Boolean = failed
    fun failureMessage(): String = failureReason

    fun load(client: Minecraft) {
        if (loading || failed || width > 0) return
        loading = true
        CompletableFuture.supplyAsync { download(url) }.whenComplete { native, err ->
            if (err != null || native == null) {
                failureReason = err?.message ?: "Пустой ответ"
                failed = true
                loading = false
                return@whenComplete
            }
            client.execute {
                try {
                    width = native.width
                    height = native.height
                    val texture = DynamicTexture({ "ngb-preview-$url" }, native)
                    texture.upload()
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

    fun renderAt(
        context: GuiGraphics,
        client: Minecraft,
        x: Int,
        y: Int,
        maxWidth: Int,
        maxHeight: Int
    ) {
        if (failed || width <= 0 || height <= 0) return

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
            x,
            y,
            0,
            0,
            scaledW,
            scaledH,
            width,
            height
        )
    }

    fun scaledSize(maxWidth: Int, maxHeight: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return maxWidth.coerceAtMost(320) to maxHeight.coerceAtMost(240)
        var scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height, 1f)
        if (scale <= 0f) scale = 1f
        val scaledW = (width * scale).toInt().coerceAtLeast(1)
        val scaledH = (height * scale).toInt().coerceAtLeast(1)
        return scaledW to scaledH
    }

    fun statusText(): String = when {
        failed -> failureReason
        loading || width <= 0 || height <= 0 -> "Загрузка изображения..."
        else -> ""
    }

    private fun download(imageUrl: String): NativeImage? {
        val candidates = if (urls.isNotEmpty()) urls else listOf(imageUrl)
        var lastError = "Не удалось загрузить изображение"
        for (candidate in candidates.distinct()) {
            try {
                decodeImage(downloadBytes(candidate))?.let { return it }
            } catch (t: Throwable) {
                lastError = t.message ?: lastError
                NayoGuildBridge.logger.debug("[NGB] image download: ${t.message}")
            }
        }
        throw IllegalStateException(lastError)
    }

    private fun downloadBytes(imageUrl: String): ByteArray {
        val conn = openConnection(URI(imageUrl), 0)
        conn.inputStream.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                out.write(buffer, 0, read)
            }
            val bytes = out.toByteArray()
            if (bytes.isEmpty()) throw IllegalStateException("Пустой ответ")
            return bytes
        }
    }

    private fun openConnection(uri: URI, redirectCount: Int): HttpURLConnection {
        if (redirectCount > 5) throw IllegalStateException("Слишком много редиректов")
        val conn = uri.toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 12000
        conn.setRequestProperty("User-Agent", "NayoGuildBridge/1.1")
        conn.setRequestProperty("Accept", "image/png,image/jpeg,image/webp,image/*;q=0.8,*/*;q=0.5")
        conn.instanceFollowRedirects = false
        val status = conn.responseCode
        if (status in 300..399) {
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (location.isNullOrBlank()) throw IllegalStateException("Редирект без Location")
            return openConnection(uri.resolve(location.trim()), redirectCount + 1)
        }
        if (status !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("HTTP $status")
        }
        return conn
    }

    private fun decodeImage(bytes: ByteArray): NativeImage? {
        try {
            return NativeImage.read(bytes)
        } catch (_: Throwable) {
            return decodeWithImageIo(bytes)
        }
    }

    private fun decodeWithImageIo(bytes: ByteArray): NativeImage? {
        val buffered = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
        val out = ByteArrayOutputStream()
        ImageIO.write(buffered, "png", out)
        return NativeImage.read(out.toByteArray())
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
