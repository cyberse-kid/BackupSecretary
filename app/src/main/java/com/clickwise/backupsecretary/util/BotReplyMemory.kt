package com.clickwise.backupsecretary.util

import android.os.SystemClock

/** Short-lived memory shared by both WhatsApp monitoring services in this app process. */
object BotReplyMemory {
    private const val REPLY_WINDOW_MS = 10 * 60 * 1000L
    private const val MAX_REPLIES = 50
    private data class Reply(val contact: String, val text: String, val recordedAt: Long)

    private val replies = ArrayDeque<Reply>()
    private val lock = Any()

    fun remember(contactName: String, message: String) = synchronized(lock) {
        val text = normalize(message)
        if (text.isEmpty()) return@synchronized
        purgeExpired(SystemClock.elapsedRealtime())
        replies.addLast(Reply(normalize(contactName), text, SystemClock.elapsedRealtime()))
        while (replies.size > MAX_REPLIES) replies.removeFirst()
    }

    fun wasSentByBot(contactName: String, message: String): Boolean = synchronized(lock) {
        purgeExpired(SystemClock.elapsedRealtime())
        val text = normalize(message)
        val contact = normalize(contactName)
        text.isNotEmpty() && replies.any { it.text == text && (contact.isEmpty() || it.contact == contact) }
    }

    private fun purgeExpired(now: Long) {
        while (replies.isNotEmpty() && now - replies.first().recordedAt > REPLY_WINDOW_MS) {
            replies.removeFirst()
        }
    }

    private fun normalize(value: String) = value.trim().lowercase()
}
