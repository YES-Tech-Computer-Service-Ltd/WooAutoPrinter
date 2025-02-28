package com.example.wooauto.presentation.screens.products

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductsViewModel @Inject constructor() : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _hasError = MutableStateFlow(false)
    val hasError: StateFlow<Boolean> = _hasError.asStateFlow()
    
    fun refreshData() {
        Log.d("ProductsViewModel", "Refreshing products data")
        viewModelScope.launch {
            try {
                _isLoading.value = true
                // 这里应该是从API获取产品数据
                // 目前使用的是模拟数据，所以暂时不需要实际API调用
                // 未来可以在这里实现与WooCommerce API的集成
                _isLoading.value = false
            } catch (e: Exception) {
                Log.e("ProductsViewModel", "Error refreshing products: ${e.message}")
                _hasError.value = true
                _isLoading.value = false
            }
        }
    }
} 