package com.example.wooauto.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Helper class to manage application language settings
 */
object LanguageHelper {

    /**
     * Set application locale using the new AppCompatDelegate API (Android 13+ compatible)
     */
    fun setLocale(context: Context, languageCode: String) {
        // 对于 Android 13 及以上版本，使用新的 API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeList = LocaleListCompat.forLanguageTags(languageCode)
            AppCompatDelegate.setApplicationLocales(localeList)
        } else {
            // 对于旧版本，使用传统方法
            updateResources(context, languageCode)
        }
    }

    /**
     * Update base context locale for older Android versions
     * This method should be called from attachBaseContext in Application class
     */
    fun updateBaseContextLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun updateResources(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val resources = context.resources
        val config = Configuration(resources.configuration)
        config.setLocale(locale)

        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    /**
     * Get display name for language code
     */
    fun getLanguageDisplayName(languageCode: String): String {
        return when (languageCode) {
            "en" -> "English"
            "zh" -> "中文 (Chinese)"
            else -> Locale(languageCode).displayLanguage
        }
    }

    /**
     * Get supported language codes
     */
    fun getSupportedLanguages(): List<Pair<String, String>> {
        return listOf(
            Pair("en", "English"),
            Pair("zh", "中文 (Chinese)")
        )
    }
}