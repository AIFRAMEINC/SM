// مسیر: app/src/main/java/com/securemessenger/app/ui/fragments/ChatFragment.kt
package com.securemessenger.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.securemessenger.app.R
import com.securemessenger.app.data.models.MessageEntity
import com.securemessenger.app.databinding.FragmentChatBinding
import com.securemessenger.app.ui.adapters.MessagesAdapter
import com.securemessenger.app.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Fragment چت (نمایش پیام‌ها و ارسال پیام)
 */
@AndroidEntryPoint
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var messagesAdapter: MessagesAdapter

    private var chatId: String? = null
    private var clientName: String = ""

    companion object {
        private const val ARG_CHAT_ID = "chat_id"

        fun newInstance(chatId: String): ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHAT_ID, chatId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatId = arguments?.getString(ARG_CHAT_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupRecyclerView()
        setupObservers()
        loadChatInfo()
    }

    /**
     * تنظیم View ها
     */
    private fun setupViews() {
        // دکمه بازگشت
        binding.backButton.setOnClickListener {
            viewModel.closeChat()
        }

        // دکمه ارسال پیام
        binding.sendButton.setOnClickListener {
            sendMessage()
        }

        // رویداد Enter در ورودی پیام
        binding.messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        // دکمه ضمیمه فایل (فعلاً غیرفعال)
        binding.attachmentButton.setOnClickListener {
            // TODO: پیاده‌سازی ارسال فایل
        }

        // دکمه منو
        binding.menuButton.setOnClickListener {
            // TODO: نمایش منوی تنظیمات چت
        }

        // فوکوس خودکار روی ورودی پیام
        binding.messageInput.requestFocus()
    }

    /**
     * تنظیم RecyclerView پیام‌ها
     */
    private fun setupRecyclerView() {
        val currentUserId = viewModel.clientId.value ?: ""
        messagesAdapter = MessagesAdapter(currentUserId)

        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true // شروع از پایین
            }
            adapter = messagesAdapter

            // اسکرول خودکار به آخرین پیام
            messagesAdapter.registerAdapterDataObserver(object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    super.onItemRangeInserted(positionStart, itemCount)
                    scrollToPosition(messagesAdapter.itemCount - 1)
                }
            })
        }
    }

    /**
     * تنظیم مشاهده‌گرها
     */
    private fun setupObservers() {
        val currentChatId = chatId ?: return

        // مشاهده پیام‌های چت
        viewModel.getChatMessages(currentChatId).observe(viewLifecycleOwner) { messages ->
            updateMessagesList(messages)
        }

        // مشاهده وضعیت بارگذاری
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // نمایش indicator بارگذاری در صورت نیاز
        }
    }

    /**
     * بارگذاری اطلاعات چت
     */
    private fun loadChatInfo() {
        val currentChatId = chatId ?: return

        // دریافت نام کلاینت از دیتابیس محلی یا سرور
        viewModel.allChats.observe(viewLifecycleOwner) { chats ->
            val chat = chats.find { it.chatId == currentChatId }
            if (chat != null) {
                clientName = chat.clientName
                updateChatTitle(clientName)
            }
        }
    }

    /**
     * به‌روزرسانی عنوان چت
     */
    private fun updateChatTitle(name: String) {
        binding.chatTitle.text = name
        binding.chatStatus.text = getString(R.string.status_online) // فعلاً همیشه آنلاین
    }

    /**
     * به‌روزرسانی لیست پیام‌ها
     */
    private fun updateMessagesList(messages: List<MessageEntity>) {
        messagesAdapter.submitList(messages)

        // نمایش/مخفی کردن empty state
        if (messages.isEmpty()) {
            binding.emptyChatState.visibility = View.VISIBLE
            binding.messagesRecyclerView.visibility = View.GONE
        } else {
            binding.emptyChatState.visibility = View.GONE
            binding.messagesRecyclerView.visibility = View.VISIBLE

            // اسکرول به آخرین پیام
            binding.messagesRecyclerView.scrollToPosition(messages.size - 1)
        }
    }

    /**
     * ارسال پیام
     */
    private fun sendMessage() {
        val messageText = binding.messageInput.text?.toString()?.trim()

        if (messageText.isNullOrBlank()) {
            return
        }

        val currentChatId = chatId ?: return

        // ارسال پیام از طریق ViewModel
        viewModel.sendMessage(messageText)

        // پاک کردن ورودی پیام
        binding.messageInput.text?.clear()

        // فوکوس مجدد
        binding.messageInput.requestFocus()
    }

    /**
     * نمایش indicator تایپ کردن
     */
    private fun showTypingIndicator() {
        binding.typingIndicator.visibility = View.VISIBLE
        // TODO: انیمیشن نقطه‌های متحرک
    }

    /**
     * مخفی کردن indicator تایپ کردن
     */
    private fun hideTypingIndicator() {
        binding.typingIndicator.visibility = View.GONE
    }

    /**
     * انیمیشن ورود به Fragment
     */
    override fun onStart() {
        super.onStart()

        // انیمیشن slide up برای کانتینر ورودی پیام
        binding.messageInputContainer.translationY = 200f
        binding.messageInputContainer.animate()
            .translationY(0f)
            .setDuration(300)
            .start()

        // انیمیشن fade in برای RecyclerView
        binding.messagesRecyclerView.alpha = 0f
        binding.messagesRecyclerView.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(100)
            .start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}