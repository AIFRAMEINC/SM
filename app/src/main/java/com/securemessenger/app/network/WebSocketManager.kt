// مسیر: app/src/main/java/com/securemessenger/app/network/WebSocketManager.kt
package com.securemessenger.app.network

import android.util.Log
import com.google.gson.Gson
import com.securemessenger.app.data.models.ConnectionState
import com.securemessenger.app.data.models.WebSocketMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدیریت اتصال WebSocket با سرور
 */
@Singleton
class WebSocketManager @Inject constructor(
    private val gson: Gson
) {
    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var reconnectJob: Job? = null
    private var isClosing = false
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val baseReconnectDelay = 1000L // 1 second
    private val maxReconnectDelay = 30000L // 30 seconds

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _incomingMessages = MutableStateFlow<WebSocketMessage?>(null)
    val incomingMessages: StateFlow<WebSocketMessage?> = _incomingMessages

    private val _errors = MutableStateFlow<String?>(null)
    val errors: StateFlow<String?> = _errors

    companion object {
        private const val TAG = "WebSocketManager"
        private const val NORMAL_CLOSURE_STATUS = 1000
    }

    /**
     * اتصال به WebSocket
     */
    fun connect(clientId: String) {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING) {
            return
        }

        isClosing = false
        _connectionState.value = ConnectionState.CONNECTING

        try {
            client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("${NetworkManager.WSS_URL}$clientId")
                .build()

            webSocket = client?.newWebSocket(request, createWebSocketListener())

        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to WebSocket", e)
            _connectionState.value = ConnectionState.ERROR
            _errors.value = "اتصال برقرار نشد: ${e.message}"
        }
    }

    /**
     * ارسال پیام از طریق WebSocket
     */
    fun sendMessage(message: WebSocketMessage): Boolean {
        return try {
            if (_connectionState.value != ConnectionState.CONNECTED) {
                Log.w(TAG, "WebSocket not connected, cannot send message")
                return false
            }

            val jsonMessage = gson.toJson(message)
            val result = webSocket?.send(jsonMessage) ?: false

            if (!result) {
                Log.e(TAG, "Failed to send WebSocket message")
                _errors.value = "خطا در ارسال پیام"
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error sending WebSocket message", e)
            _errors.value = "خطا در ارسال: ${e.message}"
            false
        }
    }

    /**
     * بستن اتصال WebSocket
     */
    fun disconnect() {
        isClosing = true
        reconnectJob?.cancel()

        try {
            webSocket?.close(NORMAL_CLOSURE_STATUS, "User disconnect")
            webSocket = null
            client?.dispatcher?.executorService?.shutdown()
            client = null
            _connectionState.value = ConnectionState.DISCONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting WebSocket", e)
        }
    }

    /**
     * ایجاد WebSocket Listener
     */
    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                _connectionState.value = ConnectionState.CONNECTED
                reconnectAttempts = 0

                // ارسال پیام ثبت‌نام
                val registerMessage = WebSocketMessage(
                    type = "register",
                    clientId = extractClientIdFromUrl(webSocket.request().url.toString())
                )
                sendMessage(registerMessage)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message received: $text")

                try {
                    val message = gson.fromJson(text, WebSocketMessage::class.java)
                    _incomingMessages.value = message
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing WebSocket message", e)
                    _errors.value = "خطا در پردازش پیام دریافتی"
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(NORMAL_CLOSURE_STATUS, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                _connectionState.value = ConnectionState.DISCONNECTED

                if (!isClosing) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                _connectionState.value = ConnectionState.ERROR
                _errors.value = "خطا در اتصال: ${t.message}"

                if (!isClosing) {
                    scheduleReconnect()
                }
            }
        }
    }

    /**
     * برنامه‌ریزی اتصال مجدد
     */
    private fun scheduleReconnect() {
        if (isClosing || reconnectAttempts >= maxReconnectAttempts) {
            Log.w(TAG, "Max reconnect attempts reached or closing")
            _connectionState.value = ConnectionState.ERROR
            return
        }

        reconnectAttempts++
        _connectionState.value = ConnectionState.RECONNECTING

        val delay = calculateReconnectDelay(reconnectAttempts)
        Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempts in ${delay}ms")

        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            delay(delay)

            if (!isClosing) {
                val clientId = extractClientIdFromCurrentConnection()
                if (clientId != null) {
                    connect(clientId)
                }
            }
        }
    }

    /**
     * محاسبه تأخیر اتصال مجدد (Exponential Backoff)
     */
    private fun calculateReconnectDelay(attempt: Int): Long {
        val delay = baseReconnectDelay * (1 shl (attempt - 1)) // 2^(attempt-1)
        return minOf(delay, maxReconnectDelay)
    }

    /**
     * استخراج Client ID از URL WebSocket
     */
    private fun extractClientIdFromUrl(url: String): String? {
        return try {
            url.substringAfterLast("/")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * استخراج Client ID از اتصال فعلی
     */
    private fun extractClientIdFromCurrentConnection(): String? {
        return try {
            webSocket?.request()?.url?.toString()?.let { url ->
                extractClientIdFromUrl(url)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * بررسی وضعیت اتصال
     */
    fun isConnected(): Boolean {
        return _connectionState.value == ConnectionState.CONNECTED
    }

    /**
     * پاک کردن خطاها
     */
    fun clearErrors() {
        _errors.value = null
    }

    /**
     * پاک کردن پیام‌های دریافتی
     */
    fun clearMessages() {
        _incomingMessages.value = null
    }
}