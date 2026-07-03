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

    @Test
    fun cleansIncomingTelegramQuoteBody() {
        val quote = QuoteDetector.parseIncomingQuote(
            "> [Telegram] Y3M112: цитируемый текст\n[Telegram] fiokem: ответ"
        )
        assertEquals("цитируемый текст", quote?.quotedText)
        assertEquals("ответ", quote?.replyText)
        assertEquals("Y3M112", quote?.quotedFromUser)
        assertEquals("Telegram", quote?.quotedFromInstance)
    }

    @Test
    fun parsesMinecraftPipeQuoteWithoutPreviewText() {
        val quote = QuoteDetector.parseIncomingQuote("> [Minecraft] Traktorist: | угу")
        assertEquals("—", quote?.quotedText)
        assertEquals("угу", quote?.replyText)
        assertEquals("Traktorist", quote?.quotedFromUser)
        assertEquals("Minecraft", quote?.quotedFromInstance)
    }

    @Test
    fun parsesMinecraftPipeQuoteWithPreviewText() {
        val quote = QuoteDetector.parseIncomingQuote("> [Minecraft] timurproooo: hello | reply text")
        assertEquals("hello", quote?.quotedText)
        assertEquals("reply text", quote?.replyText)
        assertEquals("timurproooo", quote?.quotedFromUser)
        assertEquals("Minecraft", quote?.quotedFromInstance)
    }
}
