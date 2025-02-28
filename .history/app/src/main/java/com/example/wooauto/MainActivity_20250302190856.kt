package com.example.wooauto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
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
            Manifest.permission.BLUETOOTH_ADMIN
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
        // 处理权限请求结果
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d(TAG, "所有权限已授予")
        } else {
            Log.d(TAG, "部分权限被拒绝")
            // 提示用户手动开启权限
            showPermissionSettings()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
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
     * 显示权限设置页面
     */
    private fun showPermissionSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
} 