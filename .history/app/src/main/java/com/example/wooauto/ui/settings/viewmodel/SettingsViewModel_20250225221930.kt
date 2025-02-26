package com.example.wooauto.ui.settings.viewmodel

import android.app.Application
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
            preferencesManager.setLanguage(languageCode)
            LanguageHelper.setLocale(getApplication(), languageCode)
        }
    }
}