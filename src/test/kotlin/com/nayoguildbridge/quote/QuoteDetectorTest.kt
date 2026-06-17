package com.nayoguildbridge.quote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuoteDetectorTest {
    @Test
    fun detectsPipeQuoteWithReplyOnly() {
        val quote = QuoteDetector.detect("> [Discord] Nick: | мой ответ")
        assertTrue(quote.quoted)
        assertEquals("мой ответ", quote.body)
        assertEquals("Nick", quote.quotedFromUser)
    }

    @Test
    fun detectsPipeQuoteWithQuotedText() {
        val quote = QuoteDetector.detect("> [Telegram] NDFM7: привет | да")
        assertTrue(quote.quoted)
        assertEquals("да", quote.body)
        assertEquals("привет", quote.quotedMessage)
        assertEquals("NDFM7", quote.quotedFromUser)
    }
}
