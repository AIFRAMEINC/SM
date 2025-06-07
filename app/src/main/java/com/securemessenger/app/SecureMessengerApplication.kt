// مسیر: app/src/main/java/com/securemessenger/app/SecureMessengerApplication.kt
package com.securemessenger.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

@HiltAndroidApp
class SecureMessengerApplication : Application() {

    companion object {
        lateinit var instance: SecureMessengerApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize BouncyCastle provider for cryptography
        initializeCryptography()
    }

    /**
     * تنظیم BouncyCastle برای عملیات رمزنگاری
     */
    private fun initializeCryptography() {
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}