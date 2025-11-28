package com.example.wooauto.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isNetworkAvailable = MutableStateFlow(true)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    private val _networkLogs = MutableStateFlow<List<String>>(emptyList())
    val networkLogs: StateFlow<List<String>> = _networkLogs.asStateFlow()

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            updateNetworkStatus(true, "网络已连接")
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            updateNetworkStatus(false, "网络连接丢失")
        }

        override fun onUnavailable() {
            super.onUnavailable()
            updateNetworkStatus(false, "网络不可用")
        }
        
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            
            if (hasInternet && isValidated) {
                if (!_isNetworkAvailable.value) {
                    updateNetworkStatus(true, "网络连接已验证可用")
                }
            }
        }
    }

    init {
        // Check initial state
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        
        _isNetworkAvailable.value = isConnected
        addLog(if (isConnected) "初始化：网络已连接" else "初始化：网络未连接")

        // Register callback
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            addLog("注册网络回调失败: ${e.message}")
        }
    }

    private fun updateNetworkStatus(isAvailable: Boolean, message: String) {
        if (_isNetworkAvailable.value != isAvailable) {
            _isNetworkAvailable.value = isAvailable
            addLog(message)
        }
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message"
        val currentLogs = _networkLogs.value.toMutableList()
        currentLogs.add(0, logEntry) // Add to top
        // Keep only last 50 logs
        if (currentLogs.size > 50) {
            currentLogs.removeAt(currentLogs.lastIndex)
        }
        _networkLogs.value = currentLogs
    }
    
    fun getCurrentStatus(): Boolean {
        return _isNetworkAvailable.value
    }
}

