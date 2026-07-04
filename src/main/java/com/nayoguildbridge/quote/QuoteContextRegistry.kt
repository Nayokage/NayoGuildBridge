package com.nayoguildbridge.quote

import java.util.concurrent.ConcurrentHashMap

object QuoteContextRegistry {
    data class Context(
        val originalGuildId: String? = null,
        val originalGuildName: String? = null,
        val remoteGuildTag: String? = null,
    )

    private val byKey = ConcurrentHashMap<String, Context>()

    fun key(user: String?, source: String?, text: String?): String {
        val u = user?.trim()?.lowercase().orEmpty()
        val s = source?.trim()?.lowercase().orEmpty()
        val t = text?.trim()?.replace(Regex("\\s+"), " ")?.take(160)?.lowercase().orEmpty()
        return "$s|$u|$t"
    }

    fun register(
        quotedFromUser: String?,
        quotedFromInstance: String?,
        quotedMessage: String?,
        originalGuildId: String?,
        originalGuildName: String?,
        remoteGuildTag: String? = null,
    ) {
        val id = originalGuildId?.trim()?.takeIf { it.isNotEmpty() }
        val name = originalGuildName?.trim()?.takeIf { it.isNotEmpty() }
        val tag = remoteGuildTag?.trim()?.takeIf { it.isNotEmpty() }
        if (id == null && name == null && tag == null) return
        byKey[key(quotedFromUser, quotedFromInstance, quotedMessage)] =
            Context(id, name, tag)
    }

    fun resolve(quotedFromUser: String?, quotedFromInstance: String?, quotedMessage: String?): Context? =
        byKey[key(quotedFromUser, quotedFromInstance, quotedMessage)]

    fun enrich(result: QuoteDetector.Result, localGuildId: String?): QuoteDetector.Result {
        val ctx = resolve(result.quotedFromUser, result.quotedFromInstance, result.quotedMessage)
            ?: return result
        val cross = ctx.originalGuildId != null &&
            localGuildId != null &&
            !ctx.originalGuildId.equals(localGuildId, ignoreCase = true)
        if (!cross) return result
        return result.copy(
            originalGuildId = ctx.originalGuildId,
            originalGuildName = ctx.originalGuildName ?: ctx.remoteGuildTag,
            isCrossGuildQuote = true,
        )
    }
}
