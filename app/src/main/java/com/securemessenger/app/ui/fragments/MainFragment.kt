// مسیر: app/src/main/java/com/securemessenger/app/ui/fragments/MainFragment.kt
package com.securemessenger.app.ui.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.securemessenger.app.R
import com.securemessenger.app.data.models.ChatEntity
import com.securemessenger.app.data.models.ConnectionState
import com.securemessenger.app.databinding.DialogFindClientBinding
import com.securemessenger.app.databinding.FragmentMainBinding
import com.securemessenger.app.ui.MainActivity
import com.securemessenger.app.ui.adapters.ChatsAdapter
import com.securemessenger.app.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Fragment صفحه اصلی (لیست چت‌ها)
 */
@AndroidEntryPoint
class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var chatsAdapter: ChatsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupRecyclerView()
        setupObservers()
        updateUserInfo()
    }

    /**
     * تنظیم View ها
     */
    private fun setupViews() {
        // دکمه جستجوی کلاینت
        binding.findClientButton.setOnClickListener {
            showFindClientDialog()
        }

        // دکمه خروج
        binding.logoutButton.setOnClickListener {
            showLogoutConfirmation()
        }

        // دکمه کپی Client ID
        binding.copyIdButton.setOnClickListener {
            copyClientId()
        }

        // دکمه بازخوانی
        binding.refreshButton.setOnClickListener {
            refreshChats()
        }

        // FAB چت جدید
        binding.fabNewChat.setOnClickListener {
            showFindClientDialog()
        }

        // SwipeRefresh
        binding.swipeRefresh.setOnRefreshListener {
            refreshChats()
        }
    }

    /**
     * تنظیم RecyclerView
     */
    private fun setupRecyclerView() {
        chatsAdapter = ChatsAdapter { chatEntity ->
            // کلیک روی چت
            viewModel.openChat(chatEntity.chatId)
        }

        binding.chatsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = chatsAdapter
        }
    }

    /**
     * تنظیم مشاهده‌گرها
     */
    private fun setupObservers() {
        // مشاهده لیست چت‌ها
        viewModel.allChats.observe(viewLifecycleOwner) { chats ->
            updateChatsList(chats)
        }

        // مشاهده وضعیت اتصال
        viewModel.connectionState.observe(viewLifecycleOwner) { connectionState ->
            updateConnectionState(connectionState)
        }

        // مشاهده وضعیت بارگذاری
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }
    }

    /**
     * به‌روزرسانی اطلاعات کاربر
     */
    private fun updateUserInfo() {
        viewModel.username.value?.let { username ->
            binding.userName.text = getString(R.string.welcome_user_format, username)
        }

        viewModel.clientId.value?.let { clientId ->
            binding.clientId.text = clientId
        }
    }

    /**
     * به‌روزرسانی لیست چت‌ها
     */
    private fun updateChatsList(chats: List<ChatEntity>) {
        chatsAdapter.submitList(chats)

        // نمایش/مخفی کردن empty state
        if (chats.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.chatsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.chatsRecyclerView.visibility = View.VISIBLE
        }
    }

    /**
     * به‌روزرسانی وضعیت اتصال
     */
    private fun updateConnectionState(connectionState: ConnectionState) {
        when (connectionState) {
            ConnectionState.CONNECTED -> {
                binding.connectionStatus.text = getString(R.string.connected)
                binding.connectionIndicator.setBackgroundResource(R.drawable.circle_green)
            }
            ConnectionState.CONNECTING -> {
                binding.connectionStatus.text = getString(R.string.connecting)
                binding.connectionIndicator.setBackgroundResource(R.drawable.circle_yellow)
            }
            ConnectionState.RECONNECTING -> {
                binding.connectionStatus.text = getString(R.string.reconnecting)
                binding.connectionIndicator.setBackgroundResource(R.drawable.circle_yellow)
            }
            ConnectionState.DISCONNECTED -> {
                binding.connectionStatus.text = getString(R.string.disconnected)
                binding.connectionIndicator.setBackgroundResource(R.drawable.circle_red)
            }
            ConnectionState.ERROR -> {
                binding.connectionStatus.text = getString(R.string.connection_error)
                binding.connectionIndicator.setBackgroundResource(R.drawable.circle_red)
            }
        }
    }

    /**
     * نمایش دیالوگ جستجوی کلاینت
     */
    private fun showFindClientDialog() {
        val dialogBinding = DialogFindClientBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        // دکمه جستجو
        dialogBinding.searchButton.setOnClickListener {
            val clientId = dialogBinding.clientIdInput.text?.toString()?.trim()

            if (clientId.isNullOrBlank()) {
                dialogBinding.clientIdInputLayout.error = getString(R.string.client_id_required)
                return@setOnClickListener
            }

            // پاک کردن خطا
            dialogBinding.clientIdInputLayout.error = null

            // جستجوی کلاینت
            viewModel.findClient(clientId) { clientInfo ->
                // باز کردن چت با کلاینت پیدا شده
                viewModel.openChat(clientInfo.clientId)
                dialog.dismiss()
            }
        }

        // دکمه لغو
        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        // فوکوس خودکار
        dialogBinding.clientIdInput.requestFocus()
    }

    /**
     * نمایش تأیید خروج
     */
    private fun showLogoutConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.logout_confirmation_title))
            .setMessage(getString(R.string.logout_confirmation_message))
            .setPositiveButton(getString(R.string.logout)) { _, _ ->
                viewModel.logout()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * کپی Client ID
     */
    private fun copyClientId() {
        val clientId = viewModel.getClientIdForCopy()
        if (clientId != null) {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Client ID", clientId)
            clipboard.setPrimaryClip(clip)

            (activity as? MainActivity)?.showToast(getString(R.string.client_id_copied))
        }
    }

    /**
     * بازخوانی چت‌ها
     */
    private fun refreshChats() {
        // در حال حاضر صرفاً اتصال مجدد
        viewModel.connectToServer()
    }

    /**
     * انیمیشن ورود به Fragment
     */
    override fun onStart() {
        super.onStart()

        // انیمیشن slide down برای کارت اطلاعات کاربر
        binding.userInfoCard.translationY = -100f
        binding.userInfoCard.alpha = 0f
        binding.userInfoCard.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(500)
            .start()

        // انیمیشن fade in برای دکمه‌های عملیات
        binding.actionButtons.alpha = 0f
        binding.actionButtons.animate()
            .alpha(1f)
            .setDuration(600)
            .setStartDelay(200)
            .start()

        // انیمیشن برای FAB
        binding.fabNewChat.scaleX = 0f
        binding.fabNewChat.scaleY = 0f
        binding.fabNewChat.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setStartDelay(500)
            .start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}