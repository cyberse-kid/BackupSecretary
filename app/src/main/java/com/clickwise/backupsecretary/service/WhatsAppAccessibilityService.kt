package com.clickwise.backupsecretary.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private val sentBotReplies = ArrayDeque<String>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        if (packageName != WHATSAPP_PACKAGE && packageName != WHATSAPP_B_PACKAGE) return

        Log.d(TAG, "Evento WhatsApp: type=${event.eventType}, package=$packageName, text=${event.text}")

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                processWhatsAppScreen()
            }
        }
    }

    private fun processWhatsAppScreen() {
        val root = rootInActiveWindow
        if (root == null) {
            Log.d(TAG, "No hay rootInActiveWindow")
            return
        }

        val packageName = root.packageName?.toString() ?: WHATSAPP_PACKAGE
        val contactName = getContactName(root, packageName)
        if (contactName == null) {
            Log.d(TAG, "No se encontro contacto. rootPackage=$packageName")
            dumpKnownWhatsAppTexts(root, packageName)
            return
        }

        val lastMessage = getLastIncomingMessage(root, packageName)
        if (lastMessage == null) {
            Log.d(TAG, "No se encontro ultimo mensaje. contacto=$contactName rootPackage=$packageName")
            dumpKnownWhatsAppTexts(root, packageName)
            return
        }

        if (wasSentByBot(lastMessage)) {
            Log.d(TAG, "Ignorando respuesta propia: $lastMessage")
            return
        }

        val messageKey = "$contactName:$lastMessage"
        if (messageKey == lastProcessedMessage) return
        lastProcessedMessage = messageKey

        Log.d(TAG, "Nuevo mensaje de $contactName: $lastMessage")

        CoroutineScope(Dispatchers.IO).launch {
            processMessage(contactName, lastMessage)
        }
    }

    private fun getContactName(root: AccessibilityNodeInfo, packageName: String): String? {
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/conversation_contact_name")
        if (nodes.isNullOrEmpty()) {
            val alt = root.findAccessibilityNodeInfosByViewId("$packageName:id/contact_name")
            return alt?.firstOrNull()?.text?.toString()
        }
        return nodes.firstOrNull()?.text?.toString()
    }

    private fun getLastIncomingMessage(root: AccessibilityNodeInfo, packageName: String): String? {
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/message_text")
        if (nodes.isNullOrEmpty()) return null
        return nodes
            .mapNotNull { it.text?.toString()?.trim() }
            .lastOrNull { it.isNotEmpty() && !wasSentByBot(it) }
    }

    private suspend fun processMessage(contactName: String, message: String) {
        try {
            val token = TokenManager.getAccessToken(applicationContext)
            if (token.isNullOrBlank()) {
                Log.e(TAG, "No hay token guardado. Inicia sesion en la app.")
                return
            }

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
                Log.d(TAG, "Respuesta recibida: $reply")
                delay(1500)
                writeResponseInWhatsApp(reply)
            } else {
                Log.e(TAG, "API error ${response.code()}: ${response.errorBody()?.string()}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error procesando mensaje", e)
        }
    }

    private fun writeResponseInWhatsApp(reply: String) {
        val root = rootInActiveWindow ?: return
        val packageName = root.packageName?.toString() ?: WHATSAPP_PACKAGE

        val inputNodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/entry")
        val inputField = inputNodes?.firstOrNull() ?: return

        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reply
        )

        if (!inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            Log.e(TAG, "No se pudo escribir en el campo de WhatsApp")
            return
        }

        rememberBotReply(reply)

        Handler(Looper.getMainLooper()).postDelayed({
            val currentRoot = rootInActiveWindow ?: return@postDelayed
            val currentPackageName = currentRoot.packageName?.toString() ?: packageName
            val sendNodes = currentRoot.findAccessibilityNodeInfosByViewId("$currentPackageName:id/send")
            val clicked = sendNodes?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            Log.d(TAG, if (clicked) "Respuesta enviada" else "No se encontro boton enviar")
        }, 500)
    }

    private fun rememberBotReply(reply: String) {
        sentBotReplies.addLast(reply.trim())
        while (sentBotReplies.size > 10) {
            sentBotReplies.removeFirst()
        }
    }

    private fun wasSentByBot(message: String): Boolean {
        val normalized = message.trim()
        return sentBotReplies.any { it == normalized }
    }

    private fun dumpKnownWhatsAppTexts(root: AccessibilityNodeInfo, packageName: String) {
        val ids = listOf(
            "$packageName:id/conversation_contact_name",
            "$packageName:id/contact_name",
            "$packageName:id/message_text",
            "$packageName:id/entry",
            "$packageName:id/send"
        )

        ids.forEach { id ->
            val texts = root.findAccessibilityNodeInfosByViewId(id)
                ?.mapNotNull { it.text?.toString() }
                ?.take(5)
                ?: emptyList()
            Log.d(TAG, "ID $id -> $texts")
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Servicio conectado")
    }
}
