package com.clickwise.backupsecretary.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.clickwise.backupsecretary.model.WhatsAppMessageRequest
import com.clickwise.backupsecretary.network.RetrofitClient
import com.clickwise.backupsecretary.util.BotDeduplicator
import com.clickwise.backupsecretary.util.BotReplyMemory
import com.clickwise.backupsecretary.util.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WhatsAppNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "WhatsAppNotifyBot"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_B_PACKAGE = "com.whatsapp.w4b"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName != WHATSAPP_PACKAGE && sbn.packageName != WHATSAPP_B_PACKAGE) return

        val notification = sbn.notification ?: return
        val extras = notification.extras
        val contactName = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        var message = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (message.isBlank()) {
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
            if (bigText.isNotBlank()) {
                message = bigText
            }
        }
        val actionTitles = notification.actions
            ?.joinToString { it.title?.toString().orEmpty() }
            .orEmpty()

        Log.d(
            TAG,
            "Notificacion WhatsApp recibida: package=${sbn.packageName}, title=$contactName, text=$message, actions=[$actionTitles]"
        )

        if (contactName.isBlank() || message.isBlank()) return
        if (isGroupOrSummaryNotification(notification, extras, message)) return
        if (BotReplyMemory.wasSentByBot(contactName, message)) {
            Log.d(TAG, "Ignorando respuesta propia antes de llamar a la API")
            return
        }

        val replyAction = findReplyAction(notification)
        if (replyAction == null) {
            Log.d(TAG, "Notificacion sin accion de responder: $contactName -> $message")
            return
        }

        val messageKey = "$contactName:$message"
        if (BotDeduplicator.isDuplicate(messageKey)) return

        Log.d(TAG, "Mensaje por notificacion de $contactName: $message")

        serviceScope.launch {
            processNotificationMessage(contactName, message, replyAction)
        }
    }

    private suspend fun processNotificationMessage(
        contactName: String,
        message: String,
        replyAction: Notification.Action
    ) {
        try {
            var token = TokenManager.getAccessToken(applicationContext)
            if (token.isNullOrBlank()) {
                Log.e(TAG, "No hay token guardado. Inicia sesion en la app.")
                return
            }

            var response = RetrofitClient.api.sendMessage(
                token = TokenManager.bearerToken(token),
                request = WhatsAppMessageRequest(
                    contact_name = contactName,
                    contact_phone = contactName,
                    message = message,
                    message_type = "text"
                )
            )

            if (response.code() == 401) {
                Log.w(TAG, "Token expirado o inválido en notificación (401), renovando...")
                val newToken = TokenManager.refreshAccessToken(applicationContext)
                if (!newToken.isNullOrBlank()) {
                    token = newToken
                    response = RetrofitClient.api.sendMessage(
                        token = TokenManager.bearerToken(token),
                        request = WhatsAppMessageRequest(
                            contact_name = contactName,
                            contact_phone = contactName,
                            message = message,
                            message_type = "text"
                        )
                    )
                } else {
                    Log.e(TAG, "No se pudo renovar token para responder notificación.")
                    return
                }
            }

            if (!response.isSuccessful) {
                Log.e(TAG, "API error ${response.code()}: ${response.errorBody()?.string()}")
                return
            }

            val reply = response.body()?.reply?.trim()
            if (reply.isNullOrBlank()) return

            BotReplyMemory.remember(contactName, reply)
            sendInlineReply(replyAction, reply)
            Log.d(TAG, "Respuesta enviada por notificacion")
        } catch (e: Exception) {
            Log.e(TAG, "Error respondiendo notificacion", e)
        }
    }

    private fun findReplyAction(notification: Notification): Notification.Action? {
        return notification.actions
            ?.firstOrNull { action ->
                action.remoteInputs?.isNotEmpty() == true &&
                    action.title?.toString()?.contains("responder", ignoreCase = true) == true
            }
            ?: notification.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
    }

    private fun sendInlineReply(action: Notification.Action, reply: String) {
        val remoteInputs = action.remoteInputs ?: return
        val intent = Intent()
        val results = Bundle()

        remoteInputs.forEach { remoteInput ->
            results.putCharSequence(remoteInput.resultKey, reply)
        }

        RemoteInput.addResultsToIntent(remoteInputs, intent, results)
        action.actionIntent.send(this, 0, intent)
    }

    private fun isGroupOrSummaryNotification(
        notification: Notification,
        extras: Bundle,
        message: String
    ): Boolean {
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return true
        if (message.contains("mensajes nuevos", ignoreCase = true)) return true
        if (message.contains("new messages", ignoreCase = true)) return true

        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        return textLines != null && textLines.size > 1
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener conectado")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
