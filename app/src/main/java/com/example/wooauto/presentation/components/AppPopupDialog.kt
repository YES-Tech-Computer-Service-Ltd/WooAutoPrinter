package com.example.wooauto.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 通用应用内弹出提示框组件
 * 用于显示需要用户输入的表单、重要提示或确认操作
 * 支持自动轮询检查条件以自动关闭（例如网络恢复后自动关闭提示）
 */
@Composable
fun AppPopupDialog(
    title: String,
    onDismissRequest: () -> Unit = {}, // 默认为空，防止意外关闭，可根据需要传入
    confirmButtonText: String = "确定",
    onConfirm: () -> Unit,
    dismissButtonText: String? = null,
    onDismiss: (() -> Unit)? = null,
    dismissOnBackPress: Boolean = false,
    dismissOnClickOutside: Boolean = false,
    
    // 自动关闭相关配置
    autoDismissCondition: (suspend () -> Boolean)? = null, // 返回 true 时自动关闭
    pollingInterval: Long = 2000L, // 轮询间隔，默认2秒
    
    content: @Composable ColumnScope.() -> Unit
) {
    // 如果提供了自动关闭条件，启动轮询
    if (autoDismissCondition != null) {
        LaunchedEffect(Unit) {
            while (isActive) {
                delay(pollingInterval)
                if (autoDismissCondition()) {
                    // 满足条件，执行关闭操作
                    // 优先使用 onDismiss，如果没有则使用 onDismissRequest
                    (onDismiss ?: onDismissRequest).invoke()
                    break
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
            usePlatformDefaultWidth = false // 允许自定义宽度
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f) // 占据屏幕宽度的90%
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column {
                // 标题栏 - 带背景色
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // 内容区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    content()
                }

                // 按钮区域
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (dismissButtonText != null && onDismiss != null) {
                        TextButton(
                            onClick = onDismiss
                        ) {
                            Text(dismissButtonText)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    Button(
                        onClick = onConfirm
                    ) {
                        Text(confirmButtonText)
                    }
                }
            }
        }
    }
}
