package com.nayoguildbridge.bridge

import com.nayoguildbridge.config.NgbConfig
import com.nayoguildbridge.qol.ChatQoL
import com.nayoguildbridge.util.BridgeOutboundFilter

object BridgeOutboundForwarder {
    @Volatile private var lastLine = ""
    @Volatile private var lastAt = 0L

    fun forwardGuildLine(rawGuildLine: String) {
        val cfg = NgbConfig.config
        if (!cfg.bridgeEnabled) return
        if (!BridgeOutboundFilter.shouldForwardGuildLine(rawGuildLine)) return

        val now = System.currentTimeMillis()
        val key = BridgeOutboundFilter.stripSourcePrefixForDedup(rawGuildLine)
        if (key == lastLine && now - lastAt < 2500) return
        lastLine = key
        lastAt = now

        when {
            cfg.imsWebOnlyMode -> BridgeRouter.sendWebOnlyRelay(rawGuildLine)
            else -> BridgeRouter.sendBridgeChat(rawGuildLine)
        }
    }

    fun forwardPlayerChat(message: String) {
        val cfg = NgbConfig.config
        if (!cfg.bridgeEnabled) return

        val body = ChatQoL.applyOutgoingBody(message).trim()
        if (body.isEmpty()) return

        val player = net.minecraft.client.Minecraft.getInstance().player ?: return
        val line = "Guild > ${player.name.string}: $body"
        forwardGuildLine(line)
    }
}
