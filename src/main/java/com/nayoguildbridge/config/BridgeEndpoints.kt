package com.nayoguildbridge.config

object BridgeEndpoints {
    const val PRIMARY_HTTP = "https://api.fiokem.cc"
    const val BACKUP_HTTP = "https://api.2297211.xyz"
    const val PRIMARY_WS = "wss://api.fiokem.cc/ws"
    const val BACKUP_WS = "wss://api.2297211.xyz/ws"

    fun httpBases(): List<String> = listOf(BACKUP_HTTP, PRIMARY_HTTP)

    fun wsEndpoints(): List<String> = listOf(BACKUP_WS, PRIMARY_WS)

    fun mediaHttpBase(): String = BACKUP_HTTP

    fun quoteApiUrls(): List<String> = httpBases().map { "$it/api/messages" }

    fun applyTo(cfg: Config) {
        cfg.quoteApiUrl = "$PRIMARY_HTTP/api/messages"
        cfg.quoteApiUrlBackup = "$BACKUP_HTTP/api/messages"
        cfg.remoteBridgeUrl = PRIMARY_HTTP
        cfg.platformApiUrl = PRIMARY_HTTP
        cfg.platformWsUrl = PRIMARY_WS
        cfg.imsWsUrl = PRIMARY_WS
    }

    fun statusLine(): String = "API: $PRIMARY_HTTP (запасной: $BACKUP_HTTP)"
}
