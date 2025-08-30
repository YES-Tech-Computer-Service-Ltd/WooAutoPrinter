package com.example.wooauto.utils

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale as JavaLocale

/**
 * 应用语言管理器
 * 负责在不重启应用的情况下管理语言状态
 */
object LocaleManager {
    private const val TAG = "LocaleManager"
    
    // 应用当前语言状态 - 使用MutableStateFlow以便可以被观察
    private val _currentAppLocale = MutableStateFlow(JavaLocale.getDefault())
    val currentAppLocale: StateFlow<JavaLocale> = _currentAppLocale.asStateFlow()
    
    // 用于UI状态的可观察变量 - 使用mutableStateOf触发Compose重组
    var currentLocale by mutableStateOf(JavaLocale.getDefault())
        private set
    
    /**
     * 初始化语言管理器
     * @param context 应用上下文
     */
    fun initialize(context: Context) {
        try {
            // 从LocaleHelper获取保存的语言设置
            val savedLocale = LocaleHelper.loadSavedLocale(context) ?: LocaleHelper.getSystemLocale(context)
            Log.d(TAG, "初始化语言管理器: 加载的语言为 ${savedLocale.language}")
            
            // 统一入口：直接更新应用语言（避免重复设置导致的竞态）
            updateLocale(savedLocale)
        } catch (e: Exception) {
            Log.e(TAG, "初始化语言管理器失败", e)
            // 如果初始化失败，使用系统默认语言
            updateLocale(JavaLocale.getDefault())
        }
    }
    
    /**
     * 更新应用语言
     * @param locale 要设置的语言
     */
    fun updateLocale(locale: JavaLocale) {
        try {
            Log.d(TAG, "更新应用语言: 从 ${currentLocale.language} 到 ${locale.language}")
            
            // 更新内部状态 - 两种状态都需要更新
            _currentAppLocale.value = locale
            currentLocale = locale
            
            // 使用AppCompatDelegate设置应用语言（统一入口）
            val localeList = androidx.core.os.LocaleListCompat.create(locale)
            AppCompatDelegate.setApplicationLocales(localeList)
            
            // 同步默认Locale，确保日期/数字等格式化逻辑一致
            JavaLocale.setDefault(locale)
            
            Log.d(TAG, "语言已更新为: ${locale.language}, ${locale.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "更新应用语言失败", e)
        }
    }
    
    /**
     * 设置应用语言
     * @param context 上下文
     * @param locale 要设置的语言
     */
    fun setLocale(context: Context, locale: JavaLocale) {
        try {
            // 统一走 updateLocale，避免重复设置与闪切
            updateLocale(locale)
            Log.d(TAG, "已设置应用语言(统一入口): ${locale.language}, ${locale.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "设置应用语言失败: ${e.message}", e)
        }
    }
    
    /**
     * 保存语言设置并更新应用语言
     * @param context 应用上下文
     * @param locale 要设置的语言
     */
    fun setAndSaveLocale(context: Context, locale: JavaLocale) {
        try {
            Log.d(TAG, "保存并更新语言: ${locale.language}")
            
            // 保存语言设置到SharedPreferences
            LocaleHelper.saveLocalePreference(context, locale)
            
            // 统一设置应用语言
            updateLocale(locale)
            
            // 立即刷新UI
            forceRefreshUI(context)
            
            Log.d(TAG, "语言设置已保存并更新: ${locale.language}")
        } catch (e: Exception) {
            Log.e(TAG, "保存并更新语言失败", e)
        }
    }
    
    /**
     * 获取本地化字符串
     * @param context 应用上下文
     * @param resId 资源ID
     * @return 当前语言的字符串
     */
    fun getString(context: Context, resId: Int): String {
        return try {
            LocaleHelper.getLocalizedString(context, resId, currentLocale)
        } catch (e: Exception) {
            Log.e(TAG, "获取本地化字符串失败: $resId", e)
            context.getString(resId)
        }
    }
    
    /**
     * 强制刷新整个应用UI
     * 此方法可以在语言切换不生效时调用
     */
    fun forceRefreshUI() {
        try {
            // 保持当前语言，重新应用当前Locale，避免临时切换导致的“自动切换”现象
            updateLocale(currentLocale)
            Log.d(TAG, "请求UI刷新（保持当前语言）")
        } catch (e: Exception) {
            Log.e(TAG, "强制刷新UI失败: ${e.message}", e)
        }
    }
    
    /**
     * 强制刷新特定上下文的UI
     * @param context 上下文
     */
    fun forceRefreshUI(context: Context) {
        try {
            // 如果上下文是Activity，尝试重新创建所有视图
            if (context is AppCompatActivity) {
                ActivityCompat.recreate(context)
                Log.d(TAG, "已请求重新创建Activity")
            } else {
                // 否则使用通用方法
                forceRefreshUI()
            }
        } catch (e: Exception) {
            Log.e(TAG, "强制刷新特定上下文UI失败: ${e.message}", e)
            // 失败时回退到通用方法
            forceRefreshUI()
        }
    }
}

// 创建CompositionLocal以便在Compose中传递语言状态
val LocalAppLocale = compositionLocalOf { JavaLocale.getDefault() } 