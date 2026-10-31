package com.clickwise.backupsecretary.util

object BotDeduplicator {
    private const val DEDUP_WINDOW_MS = 2 * 60 * 1000L
    private const val MAX_KEYS = 100
    private val processedKeys = LinkedHashMap<String, Long>()
    private val lock = Any()

    fun isDuplicate(key: String): Boolean = synchronized(lock) {
        val now = android.os.SystemClock.elapsedRealtime()
        val normalizedKey = key.trim().lowercase()
        processedKeys.entries.removeAll { now - it.value > DEDUP_WINDOW_MS }
        if (processedKeys.containsKey(normalizedKey)) return@synchronized true
        processedKeys[normalizedKey] = now
        while (processedKeys.size > MAX_KEYS) processedKeys.remove(processedKeys.keys.first())
        false
    }
}
