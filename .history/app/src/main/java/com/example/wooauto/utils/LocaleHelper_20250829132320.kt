package com.example.wooauto.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * 语言切换帮助工具
 * 负责APP的语言切换功能
 */
/**
 * 仅用于Locale相关的工具方法。
 * 注意：不要在UI或入口处直接调用setLocale进行应用语言切换，
 * 应统一通过 LocaleManager.setAndSaveLocale / updateLocale。
 */
object LocaleHelper {
    
    // 支持的语言列表
    val SUPPORTED_LOCALES = listOf(
        Locale.ENGLISH,
        Locale.SIMPLIFIED_CHINESE
    )
    
    /**
     * 获取当前语言
     */
    fun getSelectedLocale(context: Context): Locale {
        // 首先尝试从AppCompatDelegate获取当前应用语言
        val currentLocaleList = AppCompatDelegate.getApplicationLocales()
        if (!currentLocaleList.isEmpty) {
            val locale = currentLocaleList.get(0)
            if (locale != null) {
                return locale
            }
        }
        
        // 如果上面的方法无法获取，则尝试从Resources获取
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }
    
    // 已移除旧版全局切换入口。请统一使用 LocaleManager.updateLocale / setAndSaveLocale。
    
    /**
     * 获取适用于特定语言的Resources
     * @param context 上下文
     * @param locale 目标语言
     * @return 配置了特定语言的Resources
     */
    fun getLocalizedResources(context: Context, locale: Locale): Resources {
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 创建一个新的LocaleList并设置到配置中
            val localeList = LocaleList(locale)
            config.setLocales(localeList)
        } else {
            // 兼容旧版本
            @Suppress("DEPRECATION")
            config.setLocale(locale)
        }
        return context.createConfigurationContext(config).resources
    }
    
    /**
     * 获取本地化的字符串资源
     * @param context 上下文
     * @param resId 资源ID
     * @param locale 目标语言
     * @return 本地化的字符串
     */
    fun getLocalizedString(context: Context, resId: Int, locale: Locale): String {
        return getLocalizedResources(context, locale).getString(resId)
    }

    /**
     * 获取当前系统语言
     * @param context 上下文
     * @return 当前系统语言
     */
    fun getSystemLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            // 使用扩展函数替换过时的API
            LocaleListCompat.getDefault()[0] ?: Locale.getDefault()
        }
    }
    
    /**
     * 保存应用程序语言设置到SharedPreferences
     */
    fun saveLocalePreference(context: Context, locale: Locale) {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("app_locale", locale.toLanguageTag())
            .apply()
        android.util.Log.d("LocaleHelper", "已保存语言偏好到SharedPreferences: ${locale.toLanguageTag()}")
    }
    
    /**
     * 从SharedPreferences加载保存的语言设置
     */
    fun loadSavedLocale(context: Context): Locale? {
        val savedTag = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getString("app_locale", null)
        
        if (savedTag.isNullOrEmpty()) {
            return null
        }
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Locale.forLanguageTag(savedTag)
            } else {
                // 简单处理，仅支持简单的语言和国家代码
                val parts = savedTag.split("-")
                if (parts.size > 1) {
                    Locale(parts[0], parts[1])
                } else {
                    Locale(parts[0])
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LocaleHelper", "解析保存的语言标记失败: $savedTag", e)
            null
        }
    }
} 