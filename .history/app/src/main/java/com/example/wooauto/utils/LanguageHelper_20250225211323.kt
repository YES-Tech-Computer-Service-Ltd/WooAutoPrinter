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
        try {
            // 使用 AppCompatDelegate API 设置语言（Android 13+ 兼容）
            val localeList = LocaleListCompat.forLanguageTags(languageCode)
            AppCompatDelegate.setApplicationLocales(localeList)
            
            // 为了确保在所有Android版本上都生效，同时更新资源配置
            updateResources(context, languageCode)
            
            // 记录语言切换成功
            android.util.Log.d("LanguageHelper", "语言切换成功：$languageCode")
        } catch (e: Exception) {
            android.util.Log.e("LanguageHelper", "语言切换失败", e)
            // 如果新API失败，回退到传统方法
            updateResources(context, languageCode)
        }
    }

    /**
     * Update base context locale for older Android versions
     * This method should be called from attachBaseContext in Application class
     */
    fun updateBaseContextLocale(context: Context, languageCode: String): Context {
        return try {
            val locale = Locale(languageCode)
            Locale.setDefault(locale)

            val config = Configuration(context.resources.configuration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                config.setLocale(locale)
                context.createConfigurationContext(config)
            } else {
                @Suppress("DEPRECATION")
                config.locale = locale
                @Suppress("DEPRECATION")
                context.resources.updateConfiguration(config, context.resources.displayMetrics)
                context
            }
        } catch (e: Exception) {
            android.util.Log.e("LanguageHelper", "更新基础Context语言失败", e)
            context
        }
    }

    private fun updateResources(context: Context, languageCode: String) {
        try {
            val locale = Locale(languageCode)
            Locale.setDefault(locale)

            val resources = context.resources
            val config = Configuration(resources.configuration)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                config.setLocale(locale)
            } else {
                @Suppress("DEPRECATION")
                config.locale = locale
            }
            
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
            
            android.util.Log.d("LanguageHelper", "资源配置更新成功")
        } catch (e: Exception) {
            android.util.Log.e("LanguageHelper", "更新资源配置失败", e)
        }
    }

    /**
     * Get display name for language code
     */
    fun getLanguageDisplayName(languageCode: String): String {
        return when (languageCode) {
            "en" -> "English"
            "zh" -> "中文"
            else -> Locale(languageCode).displayLanguage
        }
    }

    /**
     * Get supported language codes
     */
    fun getSupportedLanguages(): List<Pair<String, String>> {
        return listOf(
            Pair("en", "English"),
            Pair("zh", "中文")
        )
    }
}