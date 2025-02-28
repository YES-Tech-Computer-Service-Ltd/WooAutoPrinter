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
        // 获取系统当前的Locale
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }
    
    /**
     * 设置应用语言
     * @param locale 要设置的语言
     */
    fun setLocale(locale: Locale) {
        // 使用新的AppCompat API设置应用程序的语言
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.create(locale)
        )
    }
    
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
            // 使用更现代的方法设置locale
            val localeListCompat = LocaleListCompat.create(locale)
            AppCompatDelegate.setApplicationLocales(localeListCompat)
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
} 