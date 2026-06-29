package com.nayoguildbridge.quote

import com.nayoguildbridge.config.BridgeEndpoints
import com.nayoguildbridge.config.NgbConfig

object ConfigManager {
    fun quoteSystemEnabled(): Boolean = NgbConfig.config.quoteSystemEnabled

    fun apiUrls(): List<String> {
        val cfg = NgbConfig.config
        val configured = listOf(cfg.quoteApiUrl, cfg.quoteApiUrlBackup)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return configured.ifEmpty { BridgeEndpoints.quoteApiUrls() }
    }
}
