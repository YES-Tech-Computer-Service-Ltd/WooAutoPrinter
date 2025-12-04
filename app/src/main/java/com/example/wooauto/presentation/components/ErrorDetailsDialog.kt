package com.example.wooauto.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A custom error dialog that sits on the top layer.
 * Features:
 * - Colored Title Bar
 * - User friendly message
 * - Expandable debug details
 * - Configurable Settings/Acknowledge buttons
 */
@Composable
fun ErrorDetailsDialog(
    title: String,
    userMessage: String,
    debugMessage: String,
    showSettingsButton: Boolean = true,
    showAckButton: Boolean = true,
    settingsButtonText: String = "Settings",
    ackButtonText: String = "Acknowledged",
    onSettingsClick: () -> Unit = {},
    onAckClick: () -> Unit = {},
    onDismissRequest: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // Allows customizing width
            dismissOnBackPress = false, // Usually critical errors shouldn't be dismissed by back press easily
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f) // 92% screen width
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Title Bar with Background Color
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.error) // Red background for error
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onError, // White text usually
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                }

                // Content Area
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    // 2. User Message
                    Text(
                        text = userMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Detail Toggle
                    var expanded by remember { mutableStateOf(false) }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (expanded) "Hide Details" else "Show Details",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Expandable Debug Info
                    AnimatedVisibility(visible = expanded) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp) // Max height for scrolling
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = debugMessage,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 4. Action Buttons
                    // Logic: Left = Settings, Right = Ack. If one missing, center the other.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (showSettingsButton && showAckButton) {
                            Arrangement.SpaceBetween
                        } else {
                            Arrangement.Center
                        }
                    ) {
                        if (showSettingsButton) {
                            OutlinedButton(
                                onClick = onSettingsClick,
                                shape = RoundedCornerShape(8.dp),
                                modifier = if (showSettingsButton && !showAckButton) Modifier.fillMaxWidth(0.6f) else Modifier.weight(1f)
                            ) {
                                Text(settingsButtonText)
                            }
                        }

                        if (showSettingsButton && showAckButton) {
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        if (showAckButton) {
                            Button(
                                onClick = onAckClick,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = if (showAckButton && !showSettingsButton) Modifier.fillMaxWidth(0.6f) else Modifier.weight(1f)
                            ) {
                                Text(ackButtonText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewNetworkErrorDialog() {
    ErrorDetailsDialog(
        title = "网络连接错误",
        userMessage = "无法连接到服务器，请检查您的网络连接是否正常。",
        debugMessage = "java.net.SocketTimeoutException: connect timed out\n" +
                "at java.net.PlainSocketImpl.socketConnect(Native Method)\n" +
                "at java.net.AbstractPlainSocketImpl.doConnect(AbstractPlainSocketImpl.java:350)\n" +
                "at java.net.AbstractPlainSocketImpl.connectToAddress(AbstractPlainSocketImpl.java:206)\n" +
                "at java.net.AbstractPlainSocketImpl.connect(AbstractPlainSocketImpl.java:188)\n" +
                "at java.net.SocksSocketImpl.connect(SocksSocketImpl.java:392)\n" +
                "at java.net.Socket.connect(Socket.java:589)",
        showSettingsButton = true,
        showAckButton = true,
        settingsButtonText = "网络设置",
        ackButtonText = "知道了",
        onSettingsClick = {},
        onAckClick = {},
        onDismissRequest = {}
    )
}
