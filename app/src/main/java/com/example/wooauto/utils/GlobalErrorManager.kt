package com.example.wooauto.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class GlobalErrorEvent(
    val title: String,
    val userMessage: String,
    val debugMessage: String,
    val onSettingsAction: (() -> Unit)? = null
)

/**
 * Manager to handle global error events that should be displayed to the user
 * regardless of the current screen.
 */
@Singleton
class GlobalErrorManager @Inject constructor() {
    private val _errorEvents = MutableSharedFlow<GlobalErrorEvent>(extraBufferCapacity = 1)
    val errorEvents = _errorEvents.asSharedFlow()

    fun showError(title: String, userMessage: String, debugMessage: String, onSettingsAction: (() -> Unit)? = null) {
        _errorEvents.tryEmit(GlobalErrorEvent(title, userMessage, debugMessage, onSettingsAction))
    }
}

