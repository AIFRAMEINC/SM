// مسیر: app/src/main/java/com/securemessenger/app/data/models/DataModels.kt
package com.securemessenger.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * مدل درخواست ثبت‌نام کلاینت
 */
data class ClientRegistration(
    @SerializedName("client_name")
    val clientName: String,
    @SerializedName("rsa_public_key")
    val rsaPublicKey: String,
    @SerializedName("ecc_public_key")
    val eccPublicKey: String,
    @SerializedName("dh_public_key")
    val dhPublicKey: String
)

/**
 * مدل پاسخ ثبت‌نام
 */
data class RegistrationResponse(
    val success: Boolean,
    @SerializedName("client_id")
    val clientId: String?,
    val message: String
)

/**
 * مدل درخواست اتصال
 */
data class ConnectRequest(
    @SerializedName("client_id")
    val clientId: String,
    @SerializedName("ip_address")
    val ipAddress: String,
    val port: Int,
    @SerializedName("udp_port")
    val udpPort: Int? = null
)

/**
 * مدل پاسخ اتصال
 */
data class ConnectResponse(
    val success: Boolean,
    val message: String,
    @SerializedName("active_clients")
    val activeClients: Int?
)

/**
 * مدل اطلاعات کلاینت
 */
data class ClientInfo(
    @SerializedName("client_id")
    val clientId: String,
    @SerializedName("client_name")
    val clientName: String,
    @SerializedName("ip_address")
    val ipAddress: String,
    val port: Int,
    @SerializedName("udp_port")
    val udpPort: Int?,
    @SerializedName("rsa_public_key")
    val rsaPublicKey: String,
    @SerializedName("ecc_public_key")
    val eccPublicKey: String,
    @SerializedName("dh_public_key")
    val dhPublicKey: String
)

/**
 * مدل پاسخ جستجوی کلاینت
 */
data class FindClientResponse(
    val success: Boolean,
    @SerializedName("client_info")
    val clientInfo: ClientInfo?
)

/**
 * مدل پیام WebSocket
 */
data class WebSocketMessage(
    val type: String,
    @SerializedName("sender_id")
    val senderId: String? = null,
    @SerializedName("recipient_id")
    val recipientId: String? = null,
    @SerializedName("encrypted_message")
    val encryptedMessage: String? = null,
    val timestamp: String? = null,
    @SerializedName("dh_public_key")
    val dhPublicKey: String? = null,
    val nonce: String? = null,
    @SerializedName("client_id")
    val clientId: String? = null
)

/**
 * مدل پیام در دیتابیس محلی
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: String,
    val message: String,
    val senderId: String,
    val receiverId: String,
    val timestamp: Long,
    val messageType: MessageType,
    val isDelivered: Boolean = false,
    val isRead: Boolean = false
)

/**
 * نوع پیام
 */
enum class MessageType {
    SENT,
    RECEIVED
}

/**
 * مدل چت در دیتابیس محلی
 */
@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    val chatId: String,
    val clientName: String,
    val lastMessage: String?,
    val lastMessageTime: Long,
    val unreadCount: Int = 0
)

/**
 * مدل کلاینت محلی
 */
@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey
    val clientId: String,
    val clientName: String,
    val rsaPublicKey: String,
    val eccPublicKey: String,
    val dhPublicKey: String,
    val lastSeen: Long
)

/**
 * مدل درخواست به‌روزرسانی کلیدها
 */
data class UpdateKeysRequest(
    @SerializedName("client_id")
    val clientId: String,
    @SerializedName("dh_public_key")
    val dhPublicKey: String,
    @SerializedName("dh_signature")
    val dhSignature: String
)

/**
 * مدل پاسخ به‌روزرسانی کلیدها
 */
data class UpdateKeysResponse(
    val success: Boolean,
    val message: String
)

/**
 * مدل درخواست تست سرعت
 */
data class SpeedTestRequest(
    val data: String
)

/**
 * مدل پاسخ تست سرعت
 */
data class SpeedTestResponse(
    val success: Boolean,
    val message: String,
    @SerializedName("data_size_bytes")
    val dataSizeBytes: Int,
    @SerializedName("processing_time_ms")
    val processingTimeMs: Double
)

/**
 * مدل حالت اتصال
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

/**
 * مدل خطا
 */
data class ErrorResponse(
    val error: String,
    val details: String? = null
)

/**
 * مدل پیام نوتیفیکیشن
 */
data class NotificationData(
    val title: String,
    val message: String,
    val senderId: String,
    val senderName: String,
    val timestamp: Long
)