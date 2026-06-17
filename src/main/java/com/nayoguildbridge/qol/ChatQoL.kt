package com.nayoguildbridge.qol

import com.nayoguildbridge.config.BridgeEndpoints
import com.nayoguildbridge.config.NgbConfig
import com.nayoguildbridge.preview.ImagePreviewHandler
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

object ChatQoL {
    private data class Occurrence(val count: Int, val ts: Long)
    private val collapseMap = ConcurrentHashMap<String, Occurrence>()

    @Volatile
    private var lastChatLine: String = ""

    @JvmStatic
    fun setLastChatLine(line: String) {
        lastChatLine = line
    }

    private val emojiMap = mapOf(
        ":smile:" to "\u263a",
        ":heart:" to "\u2665",
        ":fire:" to "\uD83D\uDD25",
        ":skull:" to "\u2620",
        ":star:" to "\u2b50",
        ":thumbsup:" to "\uD83D\uDC4D",
        ":ok:" to "\u2705",
        ":sad:" to "\u2639",
        ":angry:" to "\uD83D\uDE20"
    )

    private val imageUrlRx = Regex(
        """https?://\S+\.(?:png|jpg|jpeg|gif|webp)(?:\?\S*)?""",
        RegexOption.IGNORE_CASE
    )
    private val discordCdnRx = Regex(
        """https?://(?:cdn|media)\.discordapp\.(?:com|net)/attachments/\S+""",
        RegexOption.IGNORE_CASE
    )
    private val apiMediaRx = Regex(
        """https?://(?:api\.fiokem\.cc|api\.2297211\.xyz)/api/media/([a-f0-9]{16})""",
        RegexOption.IGNORE_CASE
    )
    private val imagePlaceholder = Regex("""\(image\)\s*(https?://\S+)""", RegexOption.IGNORE_CASE)
    private val bareImagePlaceholder = Regex("""\(image\)""", RegexOption.IGNORE_CASE)
    private val imageToken = Regex("""(?:\(|\[|<)img:([a-f0-9]{16})(?:\)|\]|\>)""", RegexOption.IGNORE_CASE)

    fun mediaUrlsForToken(token: String): List<String> =
        BridgeEndpoints.httpBases().map { "$it/api/media/$token" }

    fun mediaUrlForToken(token: String): String = mediaUrlsForToken(token).first()

    fun mediaTokenFromApiUrl(url: String): String? =
        apiMediaRx.find(url)?.groupValues?.getOrNull(1)

    fun applyOutgoingBody(raw: String): String {
        var text = raw
        if (NgbConfig.config.emojiShortcodesEnabled) {
            for ((k, v) in emojiMap) {
                text = text.replace(k, v, ignoreCase = true)
            }
        }
        return text
    }

    fun applyIncomingBody(raw: String): String = expandImagePlaceholders(applyOutgoingBody(raw))

    fun expandImagePlaceholders(text: String): String {
        var s = imageToken.replace(text) { mediaUrlForToken(it.groupValues[1]) }
        s = imagePlaceholder.replace(s) { it.groupValues[1] }
        return s
    }

    fun extractImageUrls(text: String): List<String> {
        val found = LinkedHashSet<String>()
        imageToken.findAll(text).forEach { m ->
            mediaUrlsForToken(m.groupValues[1]).forEach { found.add(it) }
        }
        apiMediaRx.findAll(text).forEach { m ->
            found.add(m.value.trimEnd(',', '.', ')', ']', '}'))
        }
        imageUrlRx.findAll(text).forEach { found.add(it.value.trimEnd(',', '.', ')', ']', '}')) }
        discordCdnRx.findAll(text).forEach { found.add(it.value.trimEnd(',', '.', ')', ']', '}')) }
        return found.toList()
    }

    fun isImageUrl(url: String): Boolean {
        return imageUrlRx.containsMatchIn(url) ||
            discordCdnRx.containsMatchIn(url) ||
            apiMediaRx.containsMatchIn(url)
    }

    fun imageLinkComponent(url: String, mirrorUrls: List<String> = emptyList()): MutableComponent {
        val clean = url.trim()
        val allUrls = if (mirrorUrls.isNotEmpty()) mirrorUrls else listOf(clean)
        allUrls.forEach { ImagePreviewHandler.registerImageUrl(it) }
        val label = when {
            apiMediaRx.containsMatchIn(clean) -> "фото"
            else -> clean.substringAfterLast('/').substringBefore('?').ifBlank { "image" }.take(24)
        }
        return Component.literal(" §7[🖼]")
            .append(
                Component.literal(" $label")
                    .withStyle(
                        Style.EMPTY
                            .withUnderlined(true)
                            .withColor(0xFF88FF)
                            .withClickEvent(ClickEvent.OpenUrl(URI.create(clean)))
                            .withInsertion(ImagePreviewHandler.IMAGE_PREVIEW_INSERTION + clean)
                            .withHoverEvent(
                                HoverEvent.ShowText(
                                    Component.literal(
                                        "§7Скриншот / изображение\n§7Наведите — превью в игре\n§7Клик — открыть"
                                    )
                                )
                            )
                    )
            )
    }

    fun imageTokenComponent(token: String): MutableComponent {
        val urls = mediaUrlsForToken(token)
        return imageLinkComponent(urls.first(), urls)
    }

    fun bareImageComponent(): MutableComponent =
        Component.literal(" §7[🖼]")
            .append(
                Component.literal(" фото")
                    .withStyle(
                        Style.EMPTY
                            .withColor(0xAAAAAA)
                            .withItalic(true)
                            .withHoverEvent(
                                HoverEvent.ShowText(
                                    Component.literal(
                                        "§7Картинка не зеркалирована\n§7В боте: Resolve hide links ON\n§7На API: /api/media/mirror"
                                    )
                                )
                            )
                    )
            )

    fun toDisplayComponent(text: String): MutableComponent {
        if (!NgbConfig.config.linkPreviewEnabled && !NgbConfig.config.imagePreviewEnabled) {
            return Component.literal(text)
        }
        if (imageToken.containsMatchIn(text) || bareImagePlaceholder.containsMatchIn(text)) {
            return buildRichTextWithImageTokens(text)
        }
        return buildRichText(text)
    }

    fun enrichPlainText(base: Component, plain: String): Component {
        val rich = toDisplayComponent(plain)
        if (rich.siblings.isEmpty() && rich.contents == base.contents) {
            return base
        }
        return Component.empty().append(base).append(rich)
    }

    private fun buildRichTextWithImageTokens(text: String): MutableComponent {
        val out = Component.empty()
        var last = 0
        val combined = Regex("""(?:\(|\[|<)img:([a-f0-9]{16})(?:\)|\]|\>)|\(image\)""", RegexOption.IGNORE_CASE)
        for (m in combined.findAll(text)) {
            if (m.range.first > last) {
                out.append(buildRichText(text.substring(last, m.range.first)))
            }
            val token = m.groups[1]?.value
            out.append(if (token != null) imageTokenComponent(token) else bareImageComponent())
            last = m.range.last + 1
        }
        if (last < text.length) {
            out.append(buildRichText(text.substring(last)))
        }
        return out
    }

    private fun buildRichText(text: String): MutableComponent {
        if (text.isEmpty()) return Component.literal("")
        val urlRx = Regex("""https?://\S+""")
        val match = urlRx.find(text) ?: return Component.literal(text)
        val before = text.substring(0, match.range.first)
        val url = match.value.trimEnd(',', '.', ')', ']', '}')
        val after = text.substring(match.range.last + 1)
        val isImage = NgbConfig.config.imagePreviewEnabled && isImageUrl(url)
        val linkPart = if (isImage) {
            imageLinkComponent(url)
        } else {
            Component.literal(url).withStyle(
                Style.EMPTY
                    .withUnderlined(true)
                    .withColor(0x55AAFF)
                    .withClickEvent(ClickEvent.OpenUrl(URI.create(url)))
                    .withHoverEvent(
                        HoverEvent.ShowText(Component.literal("§7Открыть ссылку"))
                    )
            )
        }
        return Component.empty()
            .append(Component.literal(before))
            .append(linkPart)
            .append(buildRichText(after))
    }

    fun collapseText(raw: String): String {
        if (!NgbConfig.config.collapseChat) return raw
        val now = System.currentTimeMillis()
        val key = raw.lowercase().trim()
        val prev = collapseMap[key]
        if (prev == null || now - prev.ts > 60_000) {
            collapseMap[key] = Occurrence(1, now)
            return raw
        }
        val next = Occurrence(prev.count + 1, now)
        collapseMap[key] = next
        return "$raw (${next.count})"
    }

    @JvmStatic
    fun collapseKey(raw: String): String = raw.lowercase().trim()

    @JvmStatic
    fun copyLastLineToClipboard() {
        val line = lastChatLine
        if (line.isBlank()) return
        net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(line)
    }
}
