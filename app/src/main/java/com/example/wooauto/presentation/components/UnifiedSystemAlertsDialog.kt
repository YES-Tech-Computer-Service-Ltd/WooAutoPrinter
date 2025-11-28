package com.example.wooauto.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wooauto.presentation.managers.AlertType
import com.example.wooauto.presentation.managers.SystemAlert

/**
 * 统一系统警报对话框
 * 能够同时展示多个系统警报，按优先级排列
 */
@Composable
fun UnifiedSystemAlertsDialog(
    activeAlerts: Map<AlertType, SystemAlert>,
    onDismissAlert: (AlertType) -> Unit,
    networkLogs: List<String> = emptyList(),
    printerLogs: List<String> = emptyList(),
    apiLogs: List<String> = emptyList()
) {
    // 如果没有警报，直接返回
    if (activeAlerts.isEmpty()) return

    val context = LocalContext.current

    // 确定要显示的最高优先级警报
    // 优先级顺序：NETWORK > PRINTER > STORE_INFO > API_ERROR
    val priorityOrder = listOf(
        AlertType.NETWORK_ERROR,
        AlertType.PRINTER_ERROR,
        AlertType.API_ERROR,
        AlertType.STORE_INFO_MISSING
    )
    
    val currentAlertType = priorityOrder.firstOrNull { activeAlerts.containsKey(it) } ?: return
    val currentAlert = activeAlerts[currentAlertType] ?: return

    var showDetails by remember { mutableStateOf(false) }
    
    // 当警报类型切换时，重置详情展开状态
    LaunchedEffect(currentAlertType) {
        showDetails = false
    }

    // 根据警报类型选择日志源
    val currentLogs = when (currentAlertType) {
        AlertType.NETWORK_ERROR -> networkLogs
        AlertType.PRINTER_ERROR -> printerLogs
        AlertType.API_ERROR -> apiLogs
        else -> emptyList()
    }

    AppPopupDialog(
        title = currentAlert.title,
        confirmButtonText = currentAlert.actionLabel ?: "我知道了",
        onConfirm = {
            // 如果有自定义操作，执行操作；否则视为忽略
            if (currentAlert.onAction != null) {
                currentAlert.onAction.invoke()
            } else {
                if (currentAlert.isDismissible) {
                    onDismissAlert(currentAlertType)
                }
            }
        },
        // 如果有关闭按钮或可忽略，允许取消
        dismissButtonText = if (currentAlert.isDismissible && currentAlert.actionLabel != null) "稍后提醒" else null,
        onDismiss = if (currentAlert.isDismissible) { { onDismissAlert(currentAlertType) } } else null,
        
        // 只有部分警报允许自动关闭
        dismissOnBackPress = currentAlert.isDismissible,
        dismissOnClickOutside = false
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (currentAlertType == AlertType.STORE_INFO_MISSING) Icons.Default.Info else Icons.Default.Warning,
                contentDescription = null,
                tint = if (currentAlertType == AlertType.STORE_INFO_MISSING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp).padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = currentAlert.message,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // 如果有日志且日志不为空，显示详情折叠面板
        if (currentLogs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        MaterialTheme.shapes.small
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDetails = !showDetails }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "查看详细日志",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showDetails) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                AnimatedVisibility(visible = showDetails) {
                    Column {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp)
                                .padding(start = 12.dp, end = 12.dp)
                        ) {
                            items(currentLogs) { log ->
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            }
                        }
                        
                        // 复制按钮
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText(
                                        "WooAutoPrinter Logs",
                                        "=== 系统诊断信息 ===\n" +
                                        "警报类型: ${currentAlert.title}\n" +
                                        "时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n\n" +
                                        currentLogs.take(20).joinToString("\n") // 只复制最近20条
                                    )
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("复制诊断信息")
                            }
                        }
                    }
                }
            }
        }
        
        // 如果有多个警报，显示提示
        if (activeAlerts.size > 1) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "还有 ${activeAlerts.size - 1} 个其他系统提示",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
