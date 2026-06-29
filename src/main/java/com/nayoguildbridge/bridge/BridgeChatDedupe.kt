package com.nayoguildbridge.bridge

import com.nayoguildbridge.util.BridgeTextUtil
import java.util.concurrent.ConcurrentHashMap

object BridgeChatDedupe {
    private const val TTL_MS = 120_000L
    private const val CAP = 120
    private val stripFormatting = Regex("§\\w")

    private data class Entry(val atMs: Long)

    private val seen = ConcurrentHashMap<String, Entry>()

    fun remember(key: String) {
        if (key.isBlank()) return
        prune()
        seen[key] = Entry(System.currentTimeMillis())
    }

    fun seenRecently(key: String): Boolean {
        if (key.isBlank()) return false
        prune()
        val e = seen[key] ?: return false
        return System.currentTimeMillis() - e.atMs <= TTL_MS
    }

    /** Returns true when this display slot is claimed for the first time within TTL. */
    fun claimDisplay(user: String, body: String): Boolean {
        val key = keyFor(user, body)
        if (key.isBlank() || seenRecently(key)) return false
        remember(key)
        return true
    }

    /** Prevents MODIFY_GAME + ALLOW_CHAT from formatting the same incoming line twice. */
    fun claimRawLine(rawLine: String): Boolean {
        val key = rawKey(rawLine)
        if (key.isBlank() || seenRecently(key)) return false
        remember(key)
        return true
    }

    fun claimIncoming(rawLine: String, user: String, body: String): Boolean {
        if (!claimRawLine(rawLine)) return false
        val displayKey = keyFor(user, body)
        if (displayKey.isBlank()) return true
        if (seenRecently(displayKey)) return false
        remember(displayKey)
        return true
    }

    fun keyFor(user: String, body: String): String {
        val u = user.trim().lowercase()
        val b = BridgeTextUtil.stripBridgeFormatting(body)
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
        if (u.isBlank() || b.isBlank()) return ""
        return "$u|$b"
    }

    private fun rawKey(rawLine: String): String {
        val normalized = stripFormatting.replace(rawLine, "")
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return ""
        return "raw:$normalized"
    }

    private fun prune() {
        val now = System.currentTimeMillis()
        seen.entries.removeIf { now - it.value.atMs > TTL_MS }
        if (seen.size > CAP) {
            seen.entries.sortedBy { it.value.atMs }.take(seen.size - CAP).forEach { seen.remove(it.key) }
        }
    }
}
