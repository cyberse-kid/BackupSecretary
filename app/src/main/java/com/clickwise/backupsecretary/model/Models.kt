package com.clickwise.backupsecretary.model

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val access: String,
    val refresh: String
)

data class WhatsAppMessageRequest(
    val contact_name: String,
    val contact_phone: String,
    val message: String,
    val message_type: String = "text"
)

data class WhatsAppMessageResponse(
    val reply: String,
    val is_lead: Boolean,
    val wants_call: Boolean,
    val interview_complete: Boolean,
    val interaction_id: String
)

data class Lead(
    val _id: String,
    val tenant_id: String,
    val sender_name: String,
    val sender_phone: String,
    val text_content: String,
    val bot_response: String,
    val status: String,
    val is_lead: Boolean,
    val platform: String,
    val created_at: String,
    val updated_at: String
)

data class LeadListResponse(
    val results: List<Lead>,
    val page: Int
)

data class DeviceTokenRequest(
    val fcm_token: String
)

data class LeadStatusRequest(
    val status: String
)