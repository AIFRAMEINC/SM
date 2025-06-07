// مسیر: app/src/main/java/com/securemessenger/app/ui/fragments/RegistrationFragment.kt
package com.securemessenger.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.securemessenger.app.R
import com.securemessenger.app.databinding.FragmentRegistrationBinding
import com.securemessenger.app.ui.MainActivity
import com.securemessenger.app.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Fragment ثبت‌نام کاربر
 */
@AndroidEntryPoint
class RegistrationFragment : Fragment() {

    private var _binding: FragmentRegistrationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegistrationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupObservers()
    }

    /**
     * تنظیم View ها
     */
    private fun setupViews() {
        // فوکوس خودکار روی ورودی نام کاربری
        binding.usernameInput.requestFocus()

        // رویداد کلیک دکمه ثبت‌نام
        binding.registerButton.setOnClickListener {
            registerUser()
        }

        // رویداد Enter در ورودی
        binding.usernameInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                registerUser()
                true
            } else {
                false
            }
        }

        // رویداد تغییر متن برای پاک کردن خطاها
        binding.usernameInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.usernameInputLayout.error = null
            }
        }
    }

    /**
     * تنظیم مشاهده‌گرها
     */
    private fun setupObservers() {
        // مشاهده وضعیت بارگذاری
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            updateLoadingState(isLoading)
        }

        // مشاهده خطاها
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                showError(it)
            }
        }
    }

    /**
     * ثبت‌نام کاربر
     */
    private fun registerUser() {
        val username = binding.usernameInput.text?.toString()?.trim()

        // اعتبارسنجی ورودی
        if (username.isNullOrBlank()) {
            binding.usernameInputLayout.error = getString(R.string.username_required)
            return
        }

        if (username.length < 2) {
            binding.usernameInputLayout.error = getString(R.string.username_too_short)
            return
        }

        if (username.length > 50) {
            binding.usernameInputLayout.error = getString(R.string.username_too_long)
            return
        }

        // بررسی کاراکترهای مجاز
        if (!username.matches(Regex("^[a-zA-Z0-9_\\-\\s]+$"))) {
            binding.usernameInputLayout.error = getString(R.string.username_invalid_characters)
            return
        }

        // پاک کردن خطاها
        binding.usernameInputLayout.error = null

        // فراخوانی ViewModel برای ثبت‌نام
        viewModel.registerUser(username)
    }

    /**
     * به‌روزرسانی وضعیت بارگذاری
     */
    private fun updateLoadingState(isLoading: Boolean) {
        binding.registerButton.isEnabled = !isLoading
        binding.usernameInput.isEnabled = !isLoading

        if (isLoading) {
            binding.registerButton.text = getString(R.string.generating_keys)
        } else {
            binding.registerButton.text = getString(R.string.register_button)
        }
    }

    /**
     * نمایش خطا
     */
    private fun showError(message: String) {
        // نمایش خطا در MainActivity
        (activity as? MainActivity)?.showToast(message)

        // یا نمایش خطا در TextInputLayout اگر مربوط به نام کاربری باشد
        if (message.contains("نام کاربری") || message.contains("username")) {
            binding.usernameInputLayout.error = message
        }
    }

    /**
     * انیمیشن ورود به Fragment
     */
    override fun onStart() {
        super.onStart()
        // انیمیشن fade in برای کارت ثبت‌نام
        binding.registrationCard.alpha = 0f
        binding.registrationCard.animate()
            .alpha(1f)
            .setDuration(500)
            .start()

        // انیمیشن slide up برای ویژگی‌های امنیتی
        binding.securityFeatures.translationY = 100f
        binding.securityFeatures.alpha = 0f
        binding.securityFeatures.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(700)
            .setStartDelay(200)
            .start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}