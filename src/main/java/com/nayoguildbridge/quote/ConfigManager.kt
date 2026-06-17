package com.nayoguildbridge.quote

import com.nayoguildbridge.config.BridgeEndpoints
import com.nayoguildbridge.config.NgbConfig

object ConfigManager {
    fun quoteSystemEnabled(): Boolean = NgbConfig.config.quoteSystemEnabled

    fun apiUrls(): List<String> = BridgeEndpoints.quoteApiUrls()
}
