package com.clickwise.backupsecretary.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.clickwise.backupsecretary.network.RetrofitClient
import com.clickwise.backupsecretary.model.WhatsAppMessageRequest
import com.clickwise.backupsecretary.util.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class WhatsAppAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "WhatsAppBot"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_B_PACKAGE = "com.whatsapp.w4b"
    }

    private var lastProcessedMessage = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        if (packageName != WHATSAPP_PACKAGE && packageName != WHATSAPP_B_PACKAGE) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                processWhatsAppScreen()
            }
        }
    }

    private fun processWhatsAppScreen() {
        val root = rootInActiveWindow ?: return
        val contactName = getContactName(root) ?: return
        val lastMessage = getLastIncomingMessage(root) ?: return

        val messageKey = "$contactName:$lastMessage"
        if (messageKey == lastProcessedMessage) return
        lastProcessedMessage = messageKey

        Log.d(TAG, "Nuevo mensaje de $contactName: $lastMessage")

        CoroutineScope(Dispatchers.IO).launch {
            processMessage(contactName, lastMessage)
        }
    }

    private fun getContactName(root: AccessibilityNodeInfo): String? {
        val nodes = root.findAccessibilityNodeInfosByViewId(
            "com.whatsapp:id/conversation_contact_name"
        )
        if (nodes.isNullOrEmpty()) {
            val alt = root.findAccessibilityNodeInfosByViewId(
                "com.whatsapp:id/contact_name"
            )
            return alt?.firstOrNull()?.text?.toString()
        }
        return nodes.firstOrNull()?.text?.toString()
    }

    private fun getLastIncomingMessage(root: AccessibilityNodeInfo): String? {
        val nodes = root.findAccessibilityNodeInfosByViewId(
            "com.whatsapp:id/message_text"
        )
        if (nodes.isNullOrEmpty()) return null
        return nodes.lastOrNull()?.text?.toString()
    }

    private suspend fun processMessage(contactName: String, message: String) {
        try {
            val token = TokenManager.getAccessToken(applicationContext) ?: return

            val response = RetrofitClient.api.sendMessage(
                token = TokenManager.bearerToken(token),
                request = WhatsAppMessageRequest(
                    contact_name  = contactName,
                    contact_phone = contactName,
                    message       = message,
                    message_type  = "text"
                )
            )

            if (response.isSuccessful) {
                val reply = response.body()?.reply ?: return
                delay(1500)
                writeResponseInWhatsApp(reply)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
        }
    }

    private fun writeResponseInWhatsApp(reply: String) {
        val root = rootInActiveWindow ?: return

        val inputNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry")
        val inputField = inputNodes?.firstOrNull() ?: return

        val args = android.os.Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reply
        )
        inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        val sendNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
        sendNodes?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Servicio conectado")
    }
}