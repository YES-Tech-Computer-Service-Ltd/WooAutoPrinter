package com.example.wooauto.wooauto.presentation.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.wooauto.data.local.WooCommerceConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val wooCommerceConfig: WooCommerceConfig
) : ViewModel() {

    private val _isConfigured = MutableStateFlow(false)
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        checkConfiguration()
    }

    private fun checkConfiguration() {
        viewModelScope.launch {
            try {
                wooCommerceConfig.isConfigured.collectLatest { configured ->
                    _isConfigured.value = configured
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _isConfigured.value = false
                _isLoading.value = false
            }
        }
    }
} 