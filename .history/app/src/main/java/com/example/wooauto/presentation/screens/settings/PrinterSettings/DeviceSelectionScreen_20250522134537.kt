package com.example.wooauto.presentation.screens.settings.PrinterSettings

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.wooauto.MainActivity
import com.example.wooauto.R
import com.example.wooauto.domain.printer.PrinterDevice
import com.example.wooauto.domain.printer.PrinterStatus

private const val TAG = "DeviceSelectionScreen"

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun DeviceSelectionScreen(
    paddingValues: PaddingValues,
    availablePrinters: List<PrinterDevice>,
    isScanning: Boolean,
    onScanClick: () -> Unit,
    onDeviceSelected: (PrinterDevice) -> Unit
) {
    Log.d(TAG, "DeviceSelectionScreen 构建开始")
    
    // 添加LaunchedEffect在屏幕首次显示时自动开始扫描
    LaunchedEffect(key1 = Unit) {
        Log.d(TAG, "DeviceSelectionScreen 自动开始扫描")
        onScanClick()
    }
    
    LaunchedEffect(key1 = availablePrinters) {
        Log.d(TAG, "可用打印机列表变化: ${availablePrinters.size}台设备")
        availablePrinters.forEach { device ->
            Log.d(TAG, "设备: ${device.name}, ${device.address}")
        }
    }
    
    LaunchedEffect(key1 = isScanning) {
        Log.d(TAG, "扫描状态变化: $isScanning")
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
    ) {
        Log.d(TAG, "Column 构建中")
        
        // 头部说明
        Text(
            text = stringResource(id = R.string.select_bluetooth_printer),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(id = R.string.select_printer_instruction),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 刷新按钮
        Button(
            onClick = {
                Log.d(TAG, "点击刷新按钮")
                onScanClick()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(id = R.string.refresh)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isScanning) stringResource(id = R.string.scanning_devices) else stringResource(id = R.string.refresh_device_list))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 设备列表或加载状态
        if (isScanning) {
            Log.d(TAG, "显示扫描中状态")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(id = R.string.scanning_bluetooth))
                }
            }
        } else if (availablePrinters.isEmpty()) {
            Log.d(TAG, "显示无可用设备状态")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.no_device_found),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.ensure_bluetooth_paired),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    RequestPermissionButton()
                }
            }
        } else {
            Log.d(TAG, "显示设备列表: ${availablePrinters.size}台设备")
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(availablePrinters) { device ->
                    val isPaired = isPairedDevice(device)
                    Log.d(TAG, "列表项: ${device.name}, ${device.address}, 配对状态: $isPaired")
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { 
                                Log.d(TAG, "选择设备: ${device.name}, ${device.address}")
                                onDeviceSelected(device) 
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 设备状态指示器
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        when (device.status) {
                                            PrinterStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                                            PrinterStatus.ERROR -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = CircleShape
                                    )
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    if (isPaired) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(id = R.string.paired),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = device.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = stringResource(id = R.string.select),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        
        // 底部提示
        Text(
            text = stringResource(id = R.string.bluetooth_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Log.d(TAG, "DeviceSelectionScreen 构建完成")
}

/**
 * 检查设备是否已配对
 */
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
private fun isPairedDevice(device: PrinterDevice): Boolean {
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return false
    try {
        @Suppress("DEPRECATION")
        val pairedDevices = bluetoothAdapter.bondedDevices
        return pairedDevices?.any { it.address == device.address } ?: false
    } catch (e: Exception) {
        Log.e("DeviceSelectionScreen", "检查设备配对状态失败", e)
        return false
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun RequestPermissionButton() {
    val context = LocalContext.current
    Button(
        onClick = {
            if (context is MainActivity) {
                context.requestAllPermissions()
            }
        },
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(text = stringResource(id = R.string.request_bluetooth_location_permission))
    }
} 