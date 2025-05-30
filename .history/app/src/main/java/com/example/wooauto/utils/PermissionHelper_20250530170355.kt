package com.example.wooauto.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 权限请求帮助类
 * 用于处理应用所需的运行时权限
 */
object PermissionHelper {
    
    const val STORAGE_PERMISSION_REQUEST_CODE = 1001
    
    /**
     * 检查是否有存储权限
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 不需要存储权限
            true
        } else {
            // Android 9 及以下检查存储权限
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 请求存储权限
     */
    fun requestStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    /**
     * 检查是否应该显示权限说明
     */
    fun shouldShowStoragePermissionRationale(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        } else {
            false
        }
    }
    
    /**
     * 处理权限请求结果
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) {
        when (requestCode) {
            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && 
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onGranted()
                } else {
                    onDenied()
                }
            }
        }
    }
} 