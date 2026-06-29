package com.nayoguildbridge.util

import com.nayoguildbridge.config.Config
import com.nayoguildbridge.config.NgbConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BridgeOutboundFilterTest {
    @BeforeEach
    fun setup() {
        NgbConfig.config = Config(
            bridgeBotNames = listOf("Electoral_Goon", "etobridge")
        )
    }

    @Test
    fun detectsDiscordRelayPayload() {
        assertTrue(BridgeOutboundFilter.isBridgeRelayPayload("[Dis] Nick: hello"))
        assertTrue(BridgeOutboundFilter.isBridgeRelayPayload("[Discord] Nick: hello"))
        assertTrue(BridgeOutboundFilter.isBridgeRelayPayload("[Minecraft] Nick: hello"))
        assertTrue(BridgeOutboundFilter.isBridgeRelayPayload("old text [Telegram] Nick: hello"))
    }

    @Test
    fun rejectsBridgeBotGuildLine() {
        val line = "Guild > Electoral_Goon [Бридж]: [Dis] WaterSpais: test"
        assertFalse(BridgeOutboundFilter.shouldForwardGuildLine(line))
    }

    @Test
    fun allowsRealPlayerGuildLineForOutbound() {
        val line = "Guild > WaterSpais: обычное сообщение"
        assertTrue(BridgeOutboundFilter.shouldForwardGuildLine(line))
    }

    @Test
    fun allowsRussianRanksAroundPlayerName() {
        val line = "Guild > [Главный Модератор] WaterSpais [СТРАНЫ СНГ]: обычное сообщение"
        assertTrue(BridgeOutboundFilter.shouldForwardGuildLine(line))
    }

    @Test
    fun rejectsRelayBodyOnPlayerLine() {
        val line = "Guild > WaterSpais: [Minecraft] echo"
        assertFalse(BridgeOutboundFilter.shouldForwardGuildLine(line))
    }

    @Test
    fun rejectsRenderedBridgeLineWithSourceBeforeNick() {
        val line = "Guild > [Minecraft] UnsmaiCreature [BR]: daily bridge summary"
        assertFalse(BridgeOutboundFilter.shouldForwardGuildLine(line))
    }

    @Test
    fun rejectsJoinedBridgeHistoryBlock() {
        val line = "Guild > WaterSpais: норм но копирует че так дофига [Telegram] Y3M112: мне все уши прожужали"
        assertFalse(BridgeOutboundFilter.shouldForwardGuildLine(line))
    }

    @Test
    fun rejectsColorizedBridgeRank() {
        val line = "Guild > SomeBot §b[§3Б§bр§3и§bд§3ж§b]: Nayokage: hello"
        assertFalse(BridgeOutboundFilter.shouldForwardGuildLine(line))
    }

    @Test
    fun rejectsRussianBridgeRankAlias() {
        val line = "Guild > SomeBot [Мост]: [Telegram] User: hello"
        assertFalse(BridgeOutboundFilter.shouldForwardGuildLine(line))
    }
}
