package com.nayoguildbridge.qol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatQoLImageTest {
    @Test
    fun expandsImagePlaceholderWithUrl() {
        val url = "https://cdn.discordapp.com/attachments/1/2/image.png"
        val expanded = ChatQoL.expandImagePlaceholders("(image) $url")
        assertEquals(url, expanded)
        assertTrue(ChatQoL.isImageUrl(expanded))
    }

    @Test
    fun expandsImageTokenToApiUrl() {
        val token = "a1b2c3d4e5f67890"
        val expanded = ChatQoL.expandImagePlaceholders("(img:$token)")
        assertEquals(ChatQoL.mediaUrlForToken(token), expanded)
        assertTrue(ChatQoL.isImageUrl(expanded))
    }

    @Test
    fun expandsAngleBracketImageToken() {
        val token = "a1b2c3d4e5f67890"
        val expanded = ChatQoL.expandImagePlaceholders("<img:$token>")
        assertEquals(ChatQoL.mediaUrlForToken(token), expanded)
    }
}
