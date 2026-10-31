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
import com.clickwise.backupsecretary.util.BotDeduplicator
import com.clickwise.backupsecretary.util.BotReplyMemory
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        if (packageName != WHATSAPP_PACKAGE && packageName != WHATSAPP_B_PACKAGE) return

        Log.d(TAG, "Evento WhatsApp: type=${event.eventType}, package=$packageName, text=${event.text}")

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                processWhatsAppScreen()
            }
        }
    }

    private fun processWhatsAppScreen() {
        val root = rootInActiveWindow ?: return
        val activePackage = root.packageName?.toString() ?: ""
        if (activePackage != WHATSAPP_PACKAGE && activePackage != WHATSAPP_B_PACKAGE) {
            // WhatsApp no está en primer plano en la pantalla
            return
        }

        val packageName = activePackage
        val contactName = getContactName(root, packageName)
        if (contactName == null) {
            Log.d(TAG, "No se encontro contacto. rootPackage=$packageName")
            dumpKnownWhatsAppTexts(root, packageName)
            return
        }

        val lastMessage = getLastIncomingMessage(root, packageName, contactName)
        if (lastMessage == null) {
            Log.d(TAG, "No se encontro ultimo mensaje. contacto=$contactName rootPackage=$packageName")
            dumpKnownWhatsAppTexts(root, packageName)
            return
        }

        if (BotReplyMemory.wasSentByBot(contactName, lastMessage)) {
            Log.d(TAG, "Ignorando respuesta propia: $lastMessage")
            return
        }

        val messageKey = "$contactName:$lastMessage"
        if (BotDeduplicator.isDuplicate(messageKey)) return

        Log.d(TAG, "Nuevo mensaje en pantalla de $contactName: $lastMessage")

        CoroutineScope(Dispatchers.IO).launch {
            processMessage(contactName, lastMessage)
        }
    }

    private fun getContactName(root: AccessibilityNodeInfo, packageName: String): String? {
        val ids = listOf(
            "$packageName:id/conversation_contact_name",
            "$packageName:id/contact_name",
            "$packageName:id/chat_title",
            "$packageName:id/action_bar_title"
        )
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            val name = nodes?.firstOrNull()?.text?.toString()
            if (!name.isNullOrBlank()) return name
        }
        return null
    }

    private fun getLastIncomingMessage(root: AccessibilityNodeInfo, packageName: String, contactName: String): String? {
        val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/message_text")
        if (nodes.isNullOrEmpty()) return null
        return nodes
            .mapNotNull { it.text?.toString()?.trim() }
            .lastOrNull { it.isNotEmpty() && !BotReplyMemory.wasSentByBot(contactName, it) }
    }

    private suspend fun processMessage(contactName: String, message: String) {
        try {
            var token = TokenManager.getAccessToken(applicationContext)
            if (token.isNullOrBlank()) {
                Log.e(TAG, "No hay token guardado. Inicia sesion en la app.")
                return
            }

            var response = RetrofitClient.api.sendMessage(
                token = TokenManager.bearerToken(token),
                request = WhatsAppMessageRequest(
                    contact_name  = contactName,
                    contact_phone = contactName,
                    message       = message,
                    message_type  = "text"
                )
            )

            if (response.code() == 401) {
                Log.w(TAG, "Token expirado o inválido (401), intentando renovar...")
                val newToken = TokenManager.refreshAccessToken(applicationContext)
                if (!newToken.isNullOrBlank()) {
                    token = newToken
                    response = RetrofitClient.api.sendMessage(
                        token = TokenManager.bearerToken(token),
                        request = WhatsAppMessageRequest(
                            contact_name  = contactName,
                            contact_phone = contactName,
                            message       = message,
                            message_type  = "text"
                        )
                    )
                } else {
                    Log.e(TAG, "No se pudo renovar la sesión en Azure. El usuario debe abrir la app e iniciar sesión.")
                    return
                }
            }

            if (response.isSuccessful) {
                val reply = response.body()?.reply ?: return
                Log.d(TAG, "Respuesta recibida: $reply")
                delay(1500)
                BotReplyMemory.remember(contactName, reply)
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

        val inputField = findInputField(root, packageName)
        if (inputField == null) {
            Log.e(TAG, "No se encontro campo de entrada en WhatsApp")
            return
        }

        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reply
        )

        if (!inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            Log.e(TAG, "No se pudo escribir en el campo de WhatsApp")
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            val currentRoot = rootInActiveWindow ?: return@postDelayed
            val currentPackageName = currentRoot.packageName?.toString() ?: packageName
            val sendClicked = clickSendButton(currentRoot, currentPackageName)
            Log.d(TAG, if (sendClicked) "Respuesta enviada" else "No se encontro boton enviar")
        }, 500)
    }

    private fun findInputField(root: AccessibilityNodeInfo, packageName: String): AccessibilityNodeInfo? {
        val inputNodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/entry")
        if (!inputNodes.isNullOrEmpty()) return inputNodes.first()
        return findEditText(root)
    }

    private fun findEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.toString()?.contains("EditText", ignoreCase = true) == true && node.isEditable) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditText(child)
            if (found != null) return found
        }
        return null
    }

    private fun clickSendButton(root: AccessibilityNodeInfo, packageName: String): Boolean {
        val sendNodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/send")
        if (!sendNodes.isNullOrEmpty() && sendNodes.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        val candidates = root.findAccessibilityNodeInfosByText("Enviar")
        if (!candidates.isNullOrEmpty()) {
            val clickable = candidates.firstOrNull { it.isClickable } ?: candidates.first().parent
            if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
        }
        val candidatesEn = root.findAccessibilityNodeInfosByText("Send")
        if (!candidatesEn.isNullOrEmpty()) {
            val clickable = candidatesEn.firstOrNull { it.isClickable } ?: candidatesEn.first().parent
            if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
        }
        return false
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
