package com.example.wooauto.presentation.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun SettingsModal(
    onDismissRequest: () -> Unit,
    widthFraction: Float = 0.95f,
    heightFraction: Float = 0.9f,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside
        )
    ) {
        // 使用圆角 Surface 作为容器（无阴影），提供统一圆角与裁剪；避免外层白框
        val baseModifier = if (heightFraction > 0f) {
            Modifier
                .fillMaxWidth(widthFraction)
                .fillMaxHeight(heightFraction)
        } else {
            // heightFraction <= 0 表示按内容自适应高度
            Modifier
                .fillMaxWidth(widthFraction)
        }
        Surface(
            modifier = baseModifier,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            content()
        }
    }

}

