// مسیر: app/src/main/java/com/securemessenger/app/network/ApiService.kt
package com.securemessenger.app.network

import com.securemessenger.app.data.models.*
import retrofit2.Response
import retrofit2.http.*

/**
 * رابط API برای ارتباط با سرور
 */
interface ApiService {

    /**
     * ثبت‌نام کلاینت جدید
     */
    @POST("register")
    suspend fun registerClient(
        @Body registration: ClientRegistration
    ): Response<RegistrationResponse>

    /**
     * اتصال کلاینت به سرور
     */
    @POST("connect")
    @FormUrlEncoded
    suspend fun connectClient(
        @Field("client_id") clientId: String,
        @Field("ip_address") ipAddress: String,
        @Field("port") port: Int,
        @Field("udp_port") udpPort: Int? = null
    ): Response<ConnectResponse>

    /**
     * قطع اتصال کلاینت
     */
    @POST("disconnect")
    @FormUrlEncoded
    suspend fun disconnectClient(
        @Field("client_id") clientId: String
    ): Response<ConnectResponse>

    /**
     * جستجوی کلاینت بر اساس ID
     */
    @GET("find_client/{client_id}")
    suspend fun findClient(
        @Path("client_id") clientId: String
    ): Response<FindClientResponse>

    /**
     * دریافت لیست کلاینت‌های فعال
     */
    @GET("active_clients")
    suspend fun getActiveClients(): Response<ActiveClientsResponse>

    /**
     * به‌روزرسانی کلیدهای DH
     */
    @POST("update_keys")
    suspend fun updateKeys(
        @Body updateRequest: UpdateKeysRequest
    ): Response<UpdateKeysResponse>

    /**
     * تست سرعت اتصال
     */
    @POST("test_speed")
    suspend fun testSpeed(
        @Body speedTest: SpeedTestRequest
    ): Response<SpeedTestResponse>

    /**
     * بررسی سلامت سرور
     */
    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>
}

/**
 * مدل پاسخ کلاینت‌های فعال
 */
data class ActiveClientsResponse(
    val success: Boolean,
    val count: Int,
    val clients: List<ActiveClient>
)

/**
 * مدل کلاینت فعال
 */
data class ActiveClient(
    @SerializedName("client_id")
    val clientId: String,
    @SerializedName("client_name")
    val clientName: String,
    @SerializedName("ip_address")
    val ipAddress: String,
    val port: Int,
    @SerializedName("last_seen")
    val lastSeen: String
)

/**
 * مدل پاسخ سلامت سرور
 */
data class HealthResponse(
    val status: String,
    @SerializedName("active_clients")
    val activeClients: Int,
    @SerializedName("websocket_connections")
    val websocketConnections: Int,
    val timestamp: String
)

/**
 * کلاس مدیریت درخواست‌های شبکه
 */
class NetworkManager {

    companion object {
        const val BASE_URL = "https://indust.aiframe.org:8443/"
        const val WSS_URL = "wss://indust.aiframe.org:8443/ws/"

        // Timeout values
        const val CONNECT_TIMEOUT = 30L
        const val READ_TIMEOUT = 30L
        const val WRITE_TIMEOUT = 30L

        // Retry settings
        const val MAX_RETRIES = 3
        const val RETRY_DELAY = 1000L
    }
}