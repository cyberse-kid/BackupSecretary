package com.clickwise.backupsecretary.network

import com.clickwise.backupsecretary.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("api/auth/token/")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("api/auth/register-device/")
    suspend fun registerDevice(
        @Header("Authorization") token: String,
        @Body request: DeviceTokenRequest
    ): Response<Unit>

    @POST("api/whatsapp/message/")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Body request: WhatsAppMessageRequest
    ): Response<WhatsAppMessageResponse>

    @GET("api/leads/")
    suspend fun getLeads(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1
    ): Response<LeadListResponse>

    @PUT("api/leads/{id}/status/")
    suspend fun updateLeadStatus(
        @Header("Authorization") token: String,
        @Path("id") interactionId: String,
        @Body request: LeadStatusRequest
    ): Response<Unit>
}