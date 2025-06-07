// مسیر: app/src/main/java/com/securemessenger/app/repository/SecureMessengerRepository.kt
package com.securemessenger.app.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.securemessenger.app.crypto.CryptoManager
import com.securemessenger.app.data.database.AppDatabase
import com.securemessenger.app.data.models.*
import com.securemessenger.app.network.ApiService
import com.securemessenger.app.network.WebSocketManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository اصلی برای مدیریت داده‌ها و منطق کسب‌وکار
 */
@Singleton
class SecureMessengerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val webSocketManager: WebSocketManager,
    private val cryptoManager: CryptoManager,
    private val database: AppDatabase,
    private val sharedPreferences: SharedPreferences
) {

    companion object {
        private const val TAG = "SecureMessengerRepository"
        private const val PREF_CLIENT_ID = "client_id"
        private const val PREF_USERNAME = "username"
        private const val PREF_IS_REGISTERED = "is_registered"
    }

    // WebSocket states
    val connectionState: StateFlow<ConnectionState> = webSocketManager.connectionState
    val incomingMessages: StateFlow<WebSocketMessage?> = webSocketManager.incomingMessages
    val webSocketErrors: StateFlow<String?> = webSocketManager.errors

    /**
     * ثبت‌نام کاربر جدید
     */
    suspend fun registerUser(username: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting user registration for: $username")

                // تولید کلیدهای رمزنگاری
                val keysGenerated = cryptoManager.generateKeys()
                if (!keysGenerated) {
                    return@withContext Result.failure(Exception("خطا در تولید کلیدهای رمزنگاری"))
                }

                // دریافت کلیدهای عمومی
                val publicKeys = cryptoManager.getPublicKeysAsPem()

                // ایجاد درخواست ثبت‌نام
                val registration = ClientRegistration(
                    clientName = username,
                    rsaPublicKey = publicKeys["rsa_public_key"] ?: "",
                    eccPublicKey = publicKeys["ecc_public_key"] ?: "",
                    dhPublicKey = publicKeys["dh_public_key"] ?: ""
                )

                // ارسال درخواست به سرور
                val response = apiService.registerClient(registration)

                if (response.isSuccessful && response.body()?.success == true) {
                    val clientId = response.body()?.clientId
                    if (clientId != null) {
                        // ذخیره اطلاعات در SharedPreferences
                        saveUserInfo(clientId, username)
                        Log.d(TAG, "User registered successfully: $clientId")
                        Result.success(clientId)
                    } else {
                        Result.failure(Exception("Client ID دریافت نشد"))
                    }
                } else {
                    val errorMsg = response.body()?.message ?: "خطای ناشناخته"
                    Result.failure(Exception(errorMsg))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Registration error", e)
                Result.failure(e)
            }
        }
    }

    /**
     * اتصال به سرور
     */
    suspend fun connectToServer(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val clientId = getClientId()
                if (clientId == null) {
                    return@withContext Result.failure(Exception("کاربر ثبت‌نام نشده است"))
                }

                // اتصال HTTP برای اعلام حضور
                val connectResponse = apiService.connectClient(
                    clientId = clientId,
                    ipAddress = "0.0.0.0", // سرور IP واقعی را تشخیص می‌دهد
                    port = 0, // پورت placeholder
                    udpPort = null
                )

                if (connectResponse.isSuccessful && connectResponse.body()?.success == true) {
                    // اتصال WebSocket
                    webSocketManager.connect(clientId)
                    Result.success(true)
                } else {
                    val errorMsg = connectResponse.body()?.message ?: "خطا در اتصال"
                    Result.failure(Exception(errorMsg))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                Result.failure(e)
            }
        }
    }

    /**
     * قطع اتصال از سرور
     */
    suspend fun disconnectFromServer(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val clientId = getClientId()
                if (clientId != null) {
                    // قطع اتصال HTTP
                    apiService.disconnectClient(clientId)
                }

                // قطع اتصال WebSocket
                webSocketManager.disconnect()

                Result.success(true)
            } catch (e: Exception) {
                Log.e(TAG, "Disconnection error", e)
                Result.failure(e)
            }
        }
    }

    /**
     * جستجوی کلاینت
     */
    suspend fun findClient(targetClientId: String): Result<ClientInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.findClient(targetClientId)

                if (response.isSuccessful && response.body()?.success == true) {
                    val clientInfo = response.body()?.clientInfo
                    if (clientInfo != null) {
                        // ذخیره اطلاعات کلاینت در دیتابیس محلی
                        saveClientInfo(clientInfo)
                        Result.success(clientInfo)
                    } else {
                        Result.failure(Exception("اطلاعات کلاینت دریافت نشد"))
                    }
                } else {
                    Result.failure(Exception("کلاینت یافت نشد یا آفلاین است"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Find client error", e)
                Result.failure(e)
            }
        }
    }

    /**
     * ارسال پیام
     */
    suspend fun sendMessage(recipientId: String, message: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val currentUserId = getClientId()
                if (currentUserId == null) {
                    return@withContext Result.failure(Exception("کاربر ثبت‌نام نشده است"))
                }

                // دریافت اطلاعات گیرنده
                val clientResult = findClient(recipientId)
                if (clientResult.isFailure) {
                    return@withContext Result.failure(clientResult.exceptionOrNull() ?: Exception("خطا در یافتن کلاینت"))
                }

                val recipientInfo = clientResult.getOrNull()!!

                // رمزگذاری پیام
                val encryptedMessage = cryptoManager.tripleEncrypt(
                    message = message,
                    recipientRSAPublicKey = recipientInfo.rsaPublicKey,
                    recipientECCPublicKey = recipientInfo.eccPublicKey,
                    recipientDHPublicKey = recipientInfo.dhPublicKey,
                    recipientId = recipientId
                )

                if (encryptedMessage == null) {
                    return@withContext Result.failure(Exception("خطا در رمزگذاری پیام"))
                }

                // ایجاد پیام WebSocket
                val wsMessage = WebSocketMessage(
                    type = "message",
                    senderId = currentUserId,
                    recipientId = recipientId,
                    encryptedMessage = encryptedMessage,
                    timestamp = System.currentTimeMillis().toString(),
                    nonce = cryptoManager.generateNonce()
                )

                // ارسال پیام
                val sent = webSocketManager.sendMessage(wsMessage)

                if (sent) {
                    // ذخیره پیام در دیتابیس محلی
                    saveMessage(
                        chatId = recipientId,
                        message = message,
                        senderId = currentUserId,
                        receiverId = recipientId,
                        messageType = MessageType.SENT
                    )
                    Result.success(true)
                } else {
                    Result.failure(Exception("خطا در ارسال پیام"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Send message error", e)
                Result.failure(e)
            }
        }
    }

    /**
     * پردازش پیام دریافتی
     */
    suspend fun processIncomingMessage(wsMessage: WebSocketMessage) {
        withContext(Dispatchers.IO) {
            try {
                if (wsMessage.type == "message" &&
                    wsMessage.senderId != null &&
                    wsMessage.encryptedMessage != null) {

                    // دریافت اطلاعات فرستنده
                    val senderResult = findClient(wsMessage.senderId)
                    if (senderResult.isFailure) {
                        Log.e(TAG, "Cannot find sender info: ${wsMessage.senderId}")
                        return@withContext
                    }

                    val senderInfo = senderResult.getOrNull()!!

                    // رمزگشایی پیام
                    val decryptedMessage = cryptoManager.tripleDecrypt(
                        encryptedMessage = wsMessage.encryptedMessage,
                        senderId = wsMessage.senderId,
                        senderECCPublicKey = senderInfo.eccPublicKey
                    )

                    if (decryptedMessage != null) {
                        // ذخیره پیام در دیتابیس
                        saveMessage(
                            chatId = wsMessage.senderId,
                            message = decryptedMessage,
                            senderId = wsMessage.senderId,
                            receiverId = getClientId() ?: "",
                            messageType = MessageType.RECEIVED
                        )

                        Log.d(TAG, "Message processed successfully from: ${wsMessage.senderId}")
                    } else {
                        Log.e(TAG, "Failed to decrypt message from: ${wsMessage.senderId}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing incoming message", e)
            }
        }
    }

    /**
     * ذخیره پیام در دیتابیس
     */
    private suspend fun saveMessage(
        chatId: String,
        message: String,
        senderId: String,
        receiverId: String,
        messageType: MessageType
    ) {
        try {
            val messageEntity = MessageEntity(
                chatId = chatId,
                message = message,
                senderId = senderId,
                receiverId = receiverId,
                timestamp = System.currentTimeMillis(),
                messageType = messageType
            )

            database.messageDao().insertMessage(messageEntity)

            // به‌روزرسانی یا ایجاد چت
            updateOrCreateChat(chatId, message, messageEntity.timestamp)

        } catch (e: Exception) {
            Log.e(TAG, "Error saving message", e)
        }
    }

    /**
     * به‌روزرسانی یا ایجاد چت
     */
    private suspend fun updateOrCreateChat(chatId: String, lastMessage: String, timestamp: Long) {
        try {
            val existingChat = database.chatDao().getChatById(chatId)
            val currentUserId = getClientId() ?: ""

            if (existingChat != null) {
                // به‌روزرسانی چت موجود
                val unreadCount = database.messageDao().getUnreadMessageCount(chatId, currentUserId)
                val updatedChat = existingChat.copy(
                    lastMessage = lastMessage,
                    lastMessageTime = timestamp,
                    unreadCount = unreadCount
                )
                database.chatDao().updateChat(updatedChat)
            } else {
                // ایجاد چت جدید
                val clientInfo = database.clientDao().getClientById(chatId)
                val clientName = clientInfo?.clientName ?: "کاربر ناشناس"

                val newChat = ChatEntity(
                    chatId = chatId,
                    clientName = clientName,
                    lastMessage = lastMessage,
                    lastMessageTime = timestamp,
                    unreadCount = if (chatId != currentUserId) 1 else 0
                )
                database.chatDao().insertChat(newChat)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating/creating chat", e)
        }
    }

    /**
     * ذخیره اطلاعات کلاینت
     */
    private suspend fun saveClientInfo(clientInfo: ClientInfo) {
        try {
            val clientEntity = ClientEntity(
                clientId = clientInfo.clientId,
                clientName = clientInfo.clientName,
                rsaPublicKey = clientInfo.rsaPublicKey,
                eccPublicKey = clientInfo.eccPublicKey,
                dhPublicKey = clientInfo.dhPublicKey,
                lastSeen = System.currentTimeMillis()
            )
            database.clientDao().insertClient(clientEntity)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving client info", e)
        }
    }

    /**
     * دریافت لیست چت‌ها
     */
    fun getAllChats(): LiveData<List<ChatEntity>> {
        return database.chatDao().getAllChats()
    }

    /**
     * دریافت پیام‌های یک چت
     */
    fun getChatMessages(chatId: String): LiveData<List<MessageEntity>> {
        return database.messageDao().getMessagesByChatId(chatId)
    }

    /**
     * علامت‌گذاری پیام‌ها به عنوان خوانده‌شده
     */
    suspend fun markMessagesAsRead(chatId: String) {
        withContext(Dispatchers.IO) {
            try {
                val currentUserId = getClientId() ?: return@withContext
                database.messageDao().markMessagesAsRead(chatId, currentUserId)
                database.chatDao().updateUnreadCount(chatId, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Error marking messages as read", e)
            }
        }
    }

    /**
     * ذخیره اطلاعات کاربر
     */
    private fun saveUserInfo(clientId: String, username: String) {
        sharedPreferences.edit()
            .putString(PREF_CLIENT_ID, clientId)
            .putString(PREF_USERNAME, username)
            .putBoolean(PREF_IS_REGISTERED, true)
            .apply()
    }

    /**
     * دریافت Client ID
     */
    fun getClientId(): String? {
        return sharedPreferences.getString(PREF_CLIENT_ID, null)
    }

    /**
     * دریافت نام کاربری
     */
    fun getUsername(): String? {
        return sharedPreferences.getString(PREF_USERNAME, null)
    }

    /**
     * بررسی ثبت‌نام کاربر
     */
    fun isUserRegistered(): Boolean {
        return sharedPreferences.getBoolean(PREF_IS_REGISTERED, false)
    }

    /**
     * پاک کردن اطلاعات کاربر
     */
    fun clearUserData() {
        sharedPreferences.edit().clear().apply()
    }
}