package com.example.wooauto.ui.screens.settings.viewmodel

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.utils.LanguageHelper
import com.example.wooauto.utils.PreferencesManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)

    // Language preference
    val language: StateFlow<String> = preferencesManager.language
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PreferencesManager.DEFAULT_LANGUAGE
        )

    /**
     * Update app language
     */
    fun updateLanguage(languageCode: String) {
        viewModelScope.launch {
            try {
                // 保存新的语言设置
                preferencesManager.setLanguage(languageCode)

                // 应用语言更改
                LanguageHelper.setLocale(getApplication(), languageCode)
                
                Log.d("SettingsViewModel", "语言更新成功：$languageCode")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "语言更新失败", e)
            }
        }
    }
}