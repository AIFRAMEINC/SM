// مسیر: app/src/main/java/com/securemessenger/app/ui/MainActivity.kt
package com.securemessenger.app.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.securemessenger.app.R
import com.securemessenger.app.databinding.ActivityMainBinding
import com.securemessenger.app.ui.fragments.ChatFragment
import com.securemessenger.app.ui.fragments.MainFragment
import com.securemessenger.app.ui.fragments.RegistrationFragment
import com.securemessenger.app.ui.viewmodel.MainViewModel
import com.securemessenger.app.ui.viewmodel.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * اکتیویتی اصلی برنامه
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    companion object {
        private const val TAG = "MainActivity"
        private const val STAR_COUNT = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        createStars()
    }

    /**
     * تنظیم مشاهده‌گرهای ViewModel
     */
    private fun setupObservers() {
        // مشاهده تغییرات وضعیت UI
        viewModel.uiState.observe(this) { uiState ->
            when (uiState) {
                UiState.REGISTRATION_SCREEN -> {
                    showFragment(RegistrationFragment())
                }
                UiState.MAIN_SCREEN -> {
                    showFragment(MainFragment())
                }
                UiState.CHAT_SCREEN -> {
                    val chatId = viewModel.currentChat.value
                    if (chatId != null) {
                        showFragment(ChatFragment.newInstance(chatId))
                    }
                }
                null -> {
                    // حالت اولیه - منتظر تعیین وضعیت
                }
            }
        }

        // مشاهده وضعیت بارگذاری
        viewModel.isLoading.observe(this) { isLoading ->
            binding.loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // مشاهده پیام‌های خطا
        viewModel.errorMessage.observe(this) { errorMessage ->
            errorMessage?.let {
                showErrorMessage(it)
                viewModel.clearMessages()
            }
        }

        // مشاهده پیام‌های موفقیت
        viewModel.successMessage.observe(this) { successMessage ->
            successMessage?.let {
                showSuccessMessage(it)
                viewModel.clearMessages()
            }
        }
    }

    /**
     * نمایش Fragment
     */
    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commitNow()
    }

    /**
     * ایجاد انیمیشن ستاره‌ها
     */
    private fun createStars() {
        // پاک کردن ستاره‌های قبلی
        binding.starsContainer.removeAllViews()

        lifecycleScope.launch {
            repeat(STAR_COUNT) { index ->
                val star = createStar()
                binding.starsContainer.addView(star)

                // تأخیر کوچک برای انیمیشن تدریجی
                delay(20)
            }
        }
    }

    /**
     * ایجاد یک ستاره
     */
    private fun createStar(): ImageView {
        val star = ImageView(this).apply {
            setImageResource(R.drawable.ic_star)

            // تنظیم موقعیت تصادفی
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            x = Random.nextFloat() * screenWidth
            y = Random.nextFloat() * screenHeight

            // تنظیم اندازه تصادفی
            val size = Random.nextInt(8, 24)
            layoutParams = android.widget.FrameLayout.LayoutParams(size, size)

            // تنظیم شفافیت تصادفی
            alpha = Random.nextFloat() * 0.8f + 0.2f

            // شروع انیمیشن
            startStarAnimation()
        }

        return star
    }

    /**
     * شروع انیمیشن ستاره
     */
    private fun ImageView.startStarAnimation() {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.5f, 1.5f, 0.5f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.5f, 1.5f, 0.5f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.3f, 1.0f, 0.3f)

        val animator = ObjectAnimator.ofPropertyValuesHolder(this, scaleX, scaleY, alpha).apply {
            duration = Random.nextLong(2000, 5000)
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            startDelay = Random.nextLong(0, 3000)
        }

        animator.start()
    }

    /**
     * نمایش پیام خطا
     */
    private fun showErrorMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(getColor(R.color.colorError))
            .setTextColor(getColor(android.R.color.white))
            .setAction("بستن") { /* بستن */ }
            .setActionTextColor(getColor(android.R.color.white))
            .show()
    }

    /**
     * نمایش پیام موفقیت
     */
    private fun showSuccessMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(getColor(R.color.colorSuccess))
            .setTextColor(getColor(android.R.color.white))
            .show()
    }

    /**
     * نمایش Toast
     */
    fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        // مدیریت دکمه بازگشت
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

        when (currentFragment) {
            is ChatFragment -> {
                // بازگشت از چت به صفحه اصلی
                viewModel.closeChat()
            }
            is MainFragment -> {
                // خروج از برنامه با تأیید
                showExitConfirmation()
            }
            is RegistrationFragment -> {
                // خروج از برنامه
                super.onBackPressed()
            }
            else -> {
                super.onBackPressed()
            }
        }
    }

    /**
     * نمایش تأیید خروج
     */
    private fun showExitConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("خروج از برنامه")
            .setMessage("آیا می‌خواهید از برنامه خارج شوید؟")
            .setPositiveButton("خروج") { _, _ ->
                viewModel.disconnectFromServer()
                finish()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // قطع اتصال هنگام بسته شدن برنامه
        viewModel.disconnectFromServer()
    }
}