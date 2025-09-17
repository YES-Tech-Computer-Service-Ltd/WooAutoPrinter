package com.example.wooauto.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
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
        Surface(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .fillMaxHeight(heightFraction),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            content()
        }
    }

    /**
     * 轻量提示框：可选标题、正文与最多两个按钮（可隐藏）。
     */
    @Composable
    fun HintCard(
        title: String? = null,
        message: String,
        primaryButtonText: String? = null,
        onPrimaryClick: (() -> Unit)? = null,
        secondaryButtonText: String? = null,
        onSecondaryClick: (() -> Unit)? = null,
        modifier: Modifier = Modifier
    ) {
        androidx.compose.material3.Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.padding(12.dp)) {
                if (!title.isNullOrEmpty()) {
                    Text(text = title, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.padding(top = 6.dp))
                }
                Text(text = message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!primaryButtonText.isNullOrEmpty() || !secondaryButtonText.isNullOrEmpty()) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (!secondaryButtonText.isNullOrEmpty() && onSecondaryClick != null) {
                            TextButton(onClick = onSecondaryClick) {
                                Text(secondaryButtonText)
                            }
                        }
                        if (!primaryButtonText.isNullOrEmpty() && onPrimaryClick != null) {
                            TextButton(onClick = onPrimaryClick) {
                                Text(primaryButtonText)
                            }
                        }
                    }
                }
            }
        }
    }
}


