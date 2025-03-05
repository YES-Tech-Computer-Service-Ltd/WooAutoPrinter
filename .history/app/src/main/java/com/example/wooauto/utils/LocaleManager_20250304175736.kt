package com.example.wooauto.utils

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.intl.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale as JavaLocale

/**
 * 应用语言管理器
 * 负责在不重启应用的情况下管理语言状态
 */
object LocaleManager {
    // 应用当前语言状态
    private val _currentAppLocale = MutableStateFlow(JavaLocale.getDefault())
    val currentAppLocale: StateFlow<JavaLocale> = _currentAppLocale.asStateFlow()
    
    // 用于 UI 状态的可观察变量
    var currentLocale by mutableStateOf(JavaLocale.getDefault())
        private set
    
    /**
     * 初始化语言管理器
     * @param context 应用上下文
     */
    fun initialize(context: Context) {
        // 从 LocaleHelper 获取保存的语言设置
        val savedLocale = LocaleHelper.loadSavedLocale(context) ?: LocaleHelper.getSystemLocale(context)
        updateLocale(savedLocale)
    }
    
    /**
     * 更新应用语言
     * @param locale 要设置的语言
     */
    fun updateLocale(locale: JavaLocale) {
        // 更新内部状态
        _currentAppLocale.value = locale
        currentLocale = locale
        
        // 使用 AppCompatDelegate 设置应用语言
        LocaleHelper.setLocale(locale)
        
        android.util.Log.d("LocaleManager", "语言已更新为: ${locale.language}, ${locale.displayName}")
    }
    
    /**
     * 保存语言设置并更新应用语言
     * @param context 应用上下文
     * @param locale 要设置的语言
     */
    fun setAndSaveLocale(context: Context, locale: JavaLocale) {
        // 保存语言设置到 SharedPreferences
        LocaleHelper.saveLocalePreference(context, locale)
        
        // 更新应用语言
        updateLocale(locale)
        
        android.util.Log.d("LocaleManager", "语言设置已保存并更新: ${locale.language}")
    }
    
    /**
     * 获取本地化字符串
     * @param context 应用上下文
     * @param resId 资源 ID
     * @return 当前语言的字符串
     */
    fun getString(context: Context, resId: Int): String {
        return try {
            LocaleHelper.getLocalizedString(context, resId, currentLocale)
        } catch (e: Exception) {
            android.util.Log.e("LocaleManager", "获取本地化字符串失败: $resId", e)
            context.getString(resId)
        }
    }
}

// 创建 CompositionLocal 以便在 Compose 中传递语言状态
val LocalAppLocale = compositionLocalOf { JavaLocale.getDefault() } 