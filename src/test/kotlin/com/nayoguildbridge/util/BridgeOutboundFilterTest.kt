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
        assertTrue(BridgeOutboundFilter.isBridgeRelayPayload("[Discord] Nick: hello"))
        assertTrue(BridgeOutboundFilter.isBridgeRelayPayload("[Minecraft] Nick: hello"))
    }

    @Test
    fun rejectsBridgeBotGuildLine() {
        val line = "Guild > Electoral_Goon [Бридж]: [Discord] WaterSpais: test"
        assertFalse(BridgeOutboundFilter.shouldForwardGuildLine(line))
    }

    @Test
    fun allowsRealPlayerGuildLineForOutbound() {
        val line = "Guild > WaterSpais: обычное сообщение"
        assertTrue(BridgeOutboundFilter.shouldForwardGuildLine(line))
    }

    @Test
    fun rejectsRelayBodyOnPlayerLine() {
        val line = "Guild > WaterSpais: [Minecraft] echo"
        assertFalse(BridgeOutboundFilter.shouldForwardGuildLine(line))
    }
}
