package com.example.wooauto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.wooauto.presentation.WooAutoApp
import com.example.wooauto.presentation.theme.WooAutoTheme
import com.example.wooauto.utils.LocaleHelper
import java.util.Locale
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val TAG = "MainActivity"
    
    // 蓝牙相关权限
    private val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    
    // 通知权限
    private val notificationPermission = 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
    
    // 权限请求回调
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 记录每个权限的结果
        val deniedPermissions = permissions.filterValues { !it }.keys.toList()
        val grantedPermissions = permissions.filterValues { it }.keys.toList()
        
        if (deniedPermissions.isEmpty()) {
            Log.d(TAG, "所有请求的权限已授予")
        } else {
            Log.w(TAG, "以下权限被拒绝: ${deniedPermissions.joinToString(", ")}")
            
            // 检查被拒绝的权限类型
            val hasBluetoothDenied = deniedPermissions.any { 
                it == Manifest.permission.BLUETOOTH_CONNECT || 
                it == Manifest.permission.BLUETOOTH_SCAN ||
                it == Manifest.permission.BLUETOOTH ||
                it == Manifest.permission.BLUETOOTH_ADMIN
            }
            
            val hasNotificationDenied = deniedPermissions.any {
                it == Manifest.permission.POST_NOTIFICATIONS
            }
            
            // 提示用户手动开启相关权限
            if (hasBluetoothDenied) {
                Log.w(TAG, "蓝牙相关权限被拒绝，蓝牙打印功能将无法正常工作")
                showPermissionSettings("蓝牙权限对于打印机功能至关，请在设置中手动开启")
            }
            
            if (hasNotificationDenied && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Log.w(TAG, "通知权限被拒绝，应用将无法显示新订单通知")
                showPermissionSettings("通知权限对于接收新订单提醒至关重要，请在设置中手动开启")
            }
        }
        
        if (grantedPermissions.isNotEmpty()) {
            Log.d(TAG, "以下权限已授予: ${grantedPermissions.joinToString(", ")}")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // 在super.onCreate之前初始化应用语言
        initAppLanguage()
        
        super.onCreate(savedInstanceState)
        
        // 请求所需权限
        requestRequiredPermissions()
        
        setContent {
            WooAutoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WooAutoApp.getContent()
                }
            }
        }
    }
    
    /**
     * 初始化应用语言设置
     */
    private fun initAppLanguage() {
        try {
            // 从SharePreferences加载保存的语言设置
            val savedLocale = LocaleHelper.loadSavedLocale(this)
            
            if (savedLocale != null) {
                // 找到保存的语言设置，应用它
                Log.d(TAG, "从SharedPreferences加载语言设置: ${savedLocale.language}")
                LocaleHelper.setLocale(savedLocale)
            } else {
                // 没有保存的语言设置，使用系统语言
                val systemLocale = LocaleHelper.getSystemLocale(this)
                // 确保使用的是我们支持的语言之一
                val supportedLocale = LocaleHelper.SUPPORTED_LOCALES.find { 
                    it.language == systemLocale.language 
                } ?: Locale.ENGLISH
                
                Log.d(TAG, "没有保存的语言设置，使用系统语言: ${supportedLocale.language}")
                LocaleHelper.setLocale(supportedLocale)
                // 保存语言设置
                LocaleHelper.saveLocalePreference(this, supportedLocale)
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化应用语言失败", e)
            // 如果初始化失败，使用英语作为默认语言
            LocaleHelper.setLocale(Locale.ENGLISH)
        }
    }
    
    /**
     * 请求应用所需权限
     */
    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        val missingPermissions = mutableListOf<String>()
        
        // 检查蓝牙权限
        bluetoothPermissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
                missingPermissions.add(permission)
            }
        }
        
        // 检查通知权限
        notificationPermission.forEach { permission ->
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
                missingPermissions.add(permission)
            }
        }
        
        // 记录缺少的权限
        if (missingPermissions.isNotEmpty()) {
            Log.d(TAG, "应用缺少以下权限: ${missingPermissions.joinToString(", ")}")
        } else {
            Log.d(TAG, "所有必要权限已获取")
        }
        
        // 请求所有缺少的权限
        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "正在请求以下权限: ${permissionsToRequest.joinToString(", ")}")
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
    
    /**
     * 显示权限设置页面并提示用户
     */
    private fun showPermissionSettings(message: String) {
        // 首先显示提示消息
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        
        // 然后跳转到应用权限设置页面
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
    
    /**
     * 提供给外部组件的方法，用于重新请求所有权限
     * 可以在扫描蓝牙等功能失败时调用
     */
    fun requestAllPermissions() {
        Log.d(TAG, "外部组件请求权限")
        requestRequiredPermissions()
    }
} 