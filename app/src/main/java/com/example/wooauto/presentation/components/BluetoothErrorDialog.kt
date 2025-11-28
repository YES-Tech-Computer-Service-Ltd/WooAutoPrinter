package com.example.wooauto.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BluetoothErrorDialog(
    onDismiss: () -> Unit,
    isBluetoothConnected: Boolean, // 用于自动关闭检查
    logs: List<String>
) {
    var showDetails by remember { mutableStateOf(false) }

    AppPopupDialog(
        title = "打印机连接问题",
        onConfirm = onDismiss,
        confirmButtonText = "我知道了",
        onDismissRequest = onDismiss,
        dismissOnClickOutside = false,
        dismissOnBackPress = false, // 强制显示
        autoDismissCondition = { 
            isBluetoothConnected
        },
        pollingInterval = 1000L
    ) {
        Text("检测到打印机连接已断开，请检查设备蓝牙设置或打印机状态。")
        Text(
            text = "连接恢复后，此提示将自动关闭。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 详情展开按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDetails = !showDetails }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "显示连接日志",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (showDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        
        // 可折叠的日志区域
        AnimatedVisibility(visible = showDetails) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(8.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "暂无日志",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(logs) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }
}

