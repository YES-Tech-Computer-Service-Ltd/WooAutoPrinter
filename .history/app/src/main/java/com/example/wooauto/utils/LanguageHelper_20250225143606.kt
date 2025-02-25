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
        val localeList = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    /**
     * Update base context locale for older Android versions
     * This method should be called from attachBaseContext in Application class
     */
    fun updateBaseContextLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            updateResourcesLocale(context, locale)
        } else {
            updateResourcesLegacy(context, locale)
        }
    }

    /**
     * Update resources configuration for Android N and above
     */
    private fun updateResourcesLocale(context: Context, locale: Locale): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }

    /**
     * Update resources configuration for Android versions below N
     */
    @Suppress("DEPRECATION")
    private fun updateResourcesLegacy(context: Context, locale: Locale): Context {
        val resources = context.resources
        val configuration = resources.configuration
        configuration.locale = locale
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return context
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