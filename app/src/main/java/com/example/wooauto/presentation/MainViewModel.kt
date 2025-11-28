package com.example.wooauto.presentation

import androidx.lifecycle.ViewModel
import com.example.wooauto.domain.managers.GlobalStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import androidx.lifecycle.viewModelScope
import com.example.wooauto.domain.repositories.StoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import com.example.wooauto.presentation.managers.AlertManager
import com.example.wooauto.presentation.managers.AlertType
import com.example.wooauto.presentation.managers.SystemAlert
import com.example.wooauto.utils.NetworkManager

@HiltViewModel
class MainViewModel @Inject constructor(
    val globalStoreManager: GlobalStoreManager,
    private val storeRepository: StoreRepository,
    val alertManager: AlertManager,
    private val networkManager: NetworkManager
) : ViewModel() {

    private val _showStoreInfoDialog = MutableStateFlow(false)
    val showStoreInfoDialog: StateFlow<Boolean> = _showStoreInfoDialog.asStateFlow()

    // 统一的警报流
    val activeAlerts: StateFlow<Map<AlertType, SystemAlert>> = alertManager.activeAlerts
    
    // 特定类型日志（供对话框使用）
    val networkLogs: StateFlow<List<String>> = networkManager.networkLogs
    
    val printerLogs = alertManager.getPrinterLogs()
    
    val apiLogs = alertManager.getApiLogs()

    init {
        checkStoreInfo()
    }
    
    private fun checkStoreInfo() {
        viewModelScope.launch {
            // Wait a bit for initialization
            kotlinx.coroutines.delay(2000)
            
            val store = globalStoreManager.selectedStore.first()
            if (store != null && (store.name == "My Store" || store.name.isBlank())) {
                _showStoreInfoDialog.value = true
                
                // Also trigger alert via AlertManager for unified tracking if needed, 
                // but we are keeping the dialog separate for now as requested.
                // If we want to unify, we would do:
                /*
                alertManager.setStoreInfoMissingAlert(true) {
                    // No-op, UI handles form
                }
                */
            }
        }
    }

    fun updateStoreInfo(name: String, address: String, phone: String) {
        viewModelScope.launch {
            val currentStore = globalStoreManager.selectedStore.first()
            if (currentStore != null) {
                val updatedStore = currentStore.copy(
                    name = name,
                    address = address,
                    phone = phone
                )
                storeRepository.updateStore(updatedStore)
                _showStoreInfoDialog.value = false
                // alertManager.setStoreInfoMissingAlert(false) {}
            }
        }
    }
    
    fun dismissStoreInfoDialog() {
        _showStoreInfoDialog.value = false
    }
    
    fun dismissAlert(type: AlertType) {
        alertManager.dismissAlert(type)
    }
}

