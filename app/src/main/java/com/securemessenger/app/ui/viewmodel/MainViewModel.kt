// مسیر: app/src/main/java/com/securemessenger/app/ui/viewmodel/MainViewModel.kt
package com.securemessenger.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securemessenger.app.data.models.*
import com.securemessenger.app.repository.SecureMessengerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel اصلی برنامه
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: SecureMessengerRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    // States from repository
    val connectionState: StateFlow<ConnectionState> = repository.connectionState
    val incomingMessages: StateFlow<WebSocketMessage?> = repository.incomingMessages
    val webSocketErrors: StateFlow<String?> = repository.webSocketErrors

    // UI States
    private val _uiState = MutableLiveData<UiState>()
    val uiState: LiveData<UiState> = _uiState

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    private val _currentChat = MutableLiveData<String?>()
    val currentChat: LiveData<String?> = _currentChat

    // User info
    private val _clientId = MutableLiveData<String?>()
    val clientId: LiveData<String?> = _clientId

    private val _username = MutableLiveData<String?>()
    val username: LiveData<String?> = _username

    // Data from database
    val allChats: LiveData<List<ChatEntity>> = repository.getAllChats()

    init {
        initializeApp()
        observeIncomingMessages()
        observeWebSocketErrors()
    }

    /**
     * مقداردهی اولیه اپلیکیشن
     */
    private fun initializeApp() {
        viewModelScope.launch {
            try {
                if (repository.isUserRegistered()) {
                    _clientId.value = repository.getClientId()
                    _username.value = repository.getUsername()
                    _uiState.value = UiState.MAIN_SCREEN

                    // تلاش برای اتصال خودکار
                    connectToServer()
                } else {
                    _uiState.value = UiState.REGISTRATION_SCREEN
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing app", e)
                _uiState.value = UiState.REGISTRATION_SCREEN
            }
        }
    }

    /**
     * ثبت‌نام کاربر
     */
    fun registerUser(username: String) {
        if (username.isBlank()) {
            _errorMessage.value = "نام کاربری نمی‌تواند خالی باشد"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true

            try {
                val result = repository.registerUser(username.trim())

                if (result.isSuccess) {
                    val clientId = result.getOrNull()
                    _clientId.value = clientId
                    _username.value = username.trim()
                    _successMessage.value = "ثبت‌نام با موفقیت انجام شد!"
                    _uiState.value = UiState.MAIN_SCREEN

                    // اتصال خودکار پس از ثبت‌نام
                    connectToServer()
                } else {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "خطا در ثبت‌نام"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Registration error", e)
                _errorMessage.value = "خطا در ثبت‌نام: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * اتصال به سرور
     */
    fun connectToServer() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                val result = repository.connectToServer()

                if (result.isSuccess) {
                    _successMessage.value = "به سرور متصل شدید"
                } else {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "خطا در اتصال"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                _errorMessage.value = "خطا در اتصال: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * قطع اتصال از سرور
     */
    fun disconnectFromServer() {
        viewModelScope.launch {
            try {
                repository.disconnectFromServer()
                _successMessage.value = "از سرور قطع شدید"
            } catch (e: Exception) {
                Log.e(TAG, "Disconnect error", e)
                _errorMessage.value = "خطا در قطع اتصال: ${e.message}"
            }
        }
    }

    /**
     * جستجوی کلاینت
     */
    fun findClient(targetClientId: String, onSuccess: (ClientInfo) -> Unit) {
        if (targetClientId.isBlank()) {
            _errorMessage.value = "شناسه کلاینت نمی‌تواند خالی باشد"
            return
        }

        if (targetClientId == _clientId.value) {
            _errorMessage.value = "نمی‌توانید با خودتان چت کنید"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true

            try {
                val result = repository.findClient(targetClientId.trim())

                if (result.isSuccess) {
                    val clientInfo = result.getOrNull()
                    if (clientInfo != null) {
                        _successMessage.value = "کاربر پیدا شد: ${clientInfo.clientName}"
                        onSuccess(clientInfo)
                    }
                } else {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "کاربر یافت نشد"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Find client error", e)
                _errorMessage.value = "خطا در جستجو: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * باز کردن چت
     */
    fun openChat(chatId: String) {
        _currentChat.value = chatId
        _uiState.value = UiState.CHAT_SCREEN

        // علامت‌گذاری پیام‌ها به عنوان خوانده‌شده
        markMessagesAsRead(chatId)
    }

    /**
     * بستن چت
     */
    fun closeChat() {
        _currentChat.value = null
        _uiState.value = UiState.MAIN_SCREEN
    }

    /**
     * ارسال پیام
     */
    fun sendMessage(message: String) {
        val chatId = _currentChat.value
        if (chatId == null) {
            _errorMessage.value = "چت انتخاب نشده است"
            return
        }

        if (message.isBlank()) {
            _errorMessage.value = "پیام نمی‌تواند خالی باشد"
            return
        }

        viewModelScope.launch {
            try {
                val result = repository.sendMessage(chatId, message.trim())

                if (result.isFailure) {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "خطا در ارسال پیام"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send message error", e)
                _errorMessage.value = "خطا در ارسال: ${e.message}"
            }
        }
    }

    /**
     * دریافت پیام‌های چت
     */
    fun getChatMessages(chatId: String): LiveData<List<MessageEntity>> {
        return repository.getChatMessages(chatId)
    }

    /**
     * علامت‌گذاری پیام‌ها به عنوان خوانده‌شده
     */
    private fun markMessagesAsRead(chatId: String) {
        viewModelScope.launch {
            try {
                repository.markMessagesAsRead(chatId)
            } catch (e: Exception) {
                Log.e(TAG, "Error marking messages as read", e)
            }
        }
    }

    /**
     * مشاهده پیام‌های ورودی
     */
    private fun observeIncomingMessages() {
        viewModelScope.launch {
            incomingMessages.collect { message ->
                message?.let {
                    try {
                        repository.processIncomingMessage(it)

                        if (it.type == "message" && it.senderId != null) {
                            _successMessage.value = "پیام جدید از ${it.senderId}"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing incoming message", e)
                    }
                }
            }
        }
    }

    /**
     * مشاهده خطاهای WebSocket
     */
    private fun observeWebSocketErrors() {
        viewModelScope.launch {
            webSocketErrors.collect { error ->
                error?.let {
                    _errorMessage.value = it
                }
            }
        }
    }

    /**
     * پاک کردن پیام‌های وضعیت
     */
    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    /**
     * خروج از برنامه
     */
    fun logout() {
        viewModelScope.launch {
            try {
                repository.disconnectFromServer()
                repository.clearUserData()

                _clientId.value = null
                _username.value = null
                _currentChat.value = null
                _uiState.value = UiState.REGISTRATION_SCREEN

                _successMessage.value = "با موفقیت خارج شدید"
            } catch (e: Exception) {
                Log.e(TAG, "Logout error", e)
                _errorMessage.value = "خطا در خروج: ${e.message}"
            }
        }
    }

    /**
     * کپی کردن Client ID
     */
    fun getClientIdForCopy(): String? {
        return _clientId.value
    }
}

/**
 * حالت‌های مختلف UI
 */
enum class UiState {
    REGISTRATION_SCREEN,
    MAIN_SCREEN,
    CHAT_SCREEN
}