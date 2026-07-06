package com.nayoguildbridge.preview

import com.mojang.blaze3d.platform.NativeImage
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object ImageDecoder {
    fun decode(bytes: ByteArray): NativeImage {
        if (bytes.isEmpty()) throw IllegalArgumentException("Пустой ответ")

        try {
            return NativeImage.read(bytes)
        } catch (_: Throwable) {
            // NativeImage поддерживает в основном PNG; JPEG/WEBP/GIF — через ImageIO.
        }

        val buffered = readBuffered(bytes)
            ?: throw IllegalArgumentException("Неподдерживаемый формат изображения")

        try {
            val png = ByteArrayOutputStream()
            if (!ImageIO.write(buffered, "png", png)) {
                return bufferedToNative(buffered)
            }
            return NativeImage.read(png.toByteArray())
        } catch (_: Throwable) {
            return bufferedToNative(buffered)
        }
    }

    private fun readBuffered(bytes: ByteArray): BufferedImage? {
        ByteArrayInputStream(bytes).use { stream ->
            ImageIO.read(stream)?.let { return it }
        }
        for (format in IMAGEIO_FORMATS) {
            val readers = ImageIO.getImageReadersByFormatName(format)
            while (readers.hasNext()) {
                val reader = readers.next()
                try {
                    ByteArrayInputStream(bytes).use { stream ->
                        reader.input = ImageIO.createImageInputStream(stream)
                        val img = reader.read(0, null)
                        if (img != null) return img
                    }
                } catch (_: Throwable) {
                    // try next reader
                } finally {
                    reader.dispose()
                }
            }
        }
        return null
    }

    private fun bufferedToNative(img: BufferedImage): NativeImage {
        val w = img.width.coerceAtLeast(1)
        val h = img.height.coerceAtLeast(1)
        val native = NativeImage(w, h, false)
        val rgb = IntArray(w * h)
        img.getRGB(0, 0, w, h, rgb, 0, w)
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                native.setPixelRGBA(x, y, argbToAbgr(rgb[i++]))
            }
        }
        return native
    }

    private fun argbToAbgr(argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return (a shl 24) or (b shl 16) or (g shl 8) or r
    }

    private val IMAGEIO_FORMATS = listOf("webp", "jpeg", "jpg", "png", "gif", "bmp", "tiff", "tif")
}
