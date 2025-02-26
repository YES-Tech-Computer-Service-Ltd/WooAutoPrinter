package com.example.wooauto.utils

import android.app.Activity
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
            // 创建并设置 Locale
            val locale = Locale(languageCode)
            Locale.setDefault(locale)

            // 使用 AppCompatDelegate API 设置语言（Android 13+ 兼容）
            val localeList = LocaleListCompat.forLanguageTags(languageCode)
            AppCompatDelegate.setApplicationLocales(localeList)
            
            // 更新资源配置
            updateResources(context, languageCode)
            
            // 如果context是Activity，则重新创建Activity
            if (context is Activity) {
                context.recreate()
            }
            
            android.util.Log.d("LanguageHelper", "语言切换成功：$languageCode")
        } catch (e: Exception) {
            android.util.Log.e("LanguageHelper", "语言切换失败", e)
            // 如果新API失败，回退到传统方法
            updateResources(context, languageCode)
            if (context is Activity) {
                context.recreate()
            }
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
                val newContext = context.createConfigurationContext(config)
                updateResources(newContext, languageCode) // 确保资源被更新
                newContext
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

            // 强制更新系统配置
            context.createConfigurationContext(config)
            
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