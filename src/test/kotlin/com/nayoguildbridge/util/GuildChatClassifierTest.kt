package com.nayoguildbridge.util

import com.nayoguildbridge.config.NgbConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GuildChatClassifierTest {
    @BeforeEach
    fun setup() {
        NgbConfig.config = NgbConfig.config.copy(
            bridgeBotNames = listOf("Electoral_Goon", "etobridge")
        )
    }

    @Test
    fun nativeGuildMemberKeepsHypixel() {
        val line = "Guild > WaterSpais: обычное сообщение"
        assertFalse(GuildChatClassifier.shouldApplyBridgeFormat(line, "WaterSpais", "обычное сообщение"))
    }

    @Test
    fun russianNonBridgeRankKeepsHypixel() {
        val line = "Guild > [Главный Модератор] WaterSpais [СТРАНЫ СНГ]: обычное сообщение"
        assertFalse(GuildChatClassifier.shouldApplyBridgeFormat(line, "WaterSpais", "обычное сообщение"))
    }

    @Test
    fun bridgeRankPlainTextKeepsHypixel() {
        val line = "Guild > Skyfidon [Бридж]: и функционал чата сломался"
        assertFalse(GuildChatClassifier.shouldApplyBridgeFormat(line, "Skyfidon", "и функционал чата сломался"))
    }

    @Test
    fun bridgeRankNestedRelayFormats() {
        val line = "Guild > oSeptember11 [Бридж]: Nayokage: 💀"
        assertTrue(GuildChatClassifier.shouldApplyBridgeFormat(line, "oSeptember11", "Nayokage: 💀"))
    }

    @Test
    fun russianBridgeRankAliasFormatsNestedRelay() {
        val line = "Guild > oSeptember11 [Мост]: Nayokage: 💀"
        assertTrue(GuildChatClassifier.shouldApplyBridgeFormat(line, "oSeptember11", "Nayokage: 💀"))
    }

    @Test
    fun discordMarkerFormats() {
        val line = "Guild > oSeptember11 [Бридж]: [Dis] Nayokage: hi"
        assertTrue(GuildChatClassifier.shouldApplyBridgeFormat(line, "oSeptember11", "[Dis] Nayokage: hi"))
    }

    @Test
    fun listedBotFormatsRelay() {
        val line = "Guild > Electoral_Goon [Бридж]: [Dis] User: test"
        assertTrue(GuildChatClassifier.shouldApplyBridgeFormat(line, "Electoral_Goon", "[Dis] User: test"))
    }

    @Test
    fun playerChatRelayWithoutGuildPrefix() {
        val line = "[Dis] Nayokage: hi"
        assertTrue(GuildChatClassifier.shouldApplyBridgeFormat(line, "oSeptember11", "[Dis] Nayokage: hi"))
    }

    @Test
    fun playerChatNestedRelayWithoutBotList() {
        val line = "Nayokage: 💀"
        assertTrue(GuildChatClassifier.shouldApplyBridgeFormat(line, "oSeptember11", "Nayokage: 💀"))
    }
}
