package com.nayoguildbridge.bridge

import java.util.concurrent.ConcurrentHashMap

object BridgeChatDedupe {
    private const val TTL_MS = 12_000L
    private const val CAP = 120

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

    fun keyFor(user: String, body: String): String {
        val u = user.trim().lowercase()
        val b = body.trim().lowercase().replace(Regex("\\s+"), " ")
        return "$u|$b"
    }

    private fun prune() {
        val now = System.currentTimeMillis()
        seen.entries.removeIf { now - it.value.atMs > TTL_MS }
        if (seen.size > CAP) {
            seen.entries.sortedBy { it.value.atMs }.take(seen.size - CAP).forEach { seen.remove(it.key) }
        }
    }
}
