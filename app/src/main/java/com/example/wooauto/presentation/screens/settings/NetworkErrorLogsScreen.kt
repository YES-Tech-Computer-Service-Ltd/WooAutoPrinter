package com.example.wooauto.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wooauto.R
import com.example.wooauto.diagnostics.network.NetworkErrorLogEntry
import com.example.wooauto.presentation.components.ScrollableWithEdgeScrim
import com.example.wooauto.presentation.components.SettingsSubPageScaffold
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NetworkErrorLogsScreen(
    viewModel: NetworkErrorLogsViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsState()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // stringResource 是 @Composable，只能在 Composable 上下文中调用；这里提前取值供 onClick 使用
    val copiedMsg = stringResource(R.string.network_error_logs_copied)
    val clearedMsg = stringResource(R.string.network_error_logs_cleared)

    val df = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    var selected by remember { mutableStateOf<NetworkErrorLogEntry?>(null) }

    // 进入页面时，如果有日志，确保 UI 能立即展示（无其他副作用）
    LaunchedEffect(logs.size) { /* no-op */ }

    SettingsSubPageScaffold { modifier ->
        Column(modifier = modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.network_error_logs_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = {
                        val text = exportAllText(logs, df)
                        clipboard.setText(AnnotatedString(text))
                        scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
                    },
                    enabled = logs.isNotEmpty()
                ) {
                    Text(stringResource(R.string.network_error_logs_copy_all))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        viewModel.clearAll()
                        scope.launch { snackbarHostState.showSnackbar(clearedMsg) }
                    },
                    enabled = logs.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.network_error_logs_clear))
                }
            }

            ScrollableWithEdgeScrim(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { scrollModifier, _ ->
                Column(modifier = scrollModifier.fillMaxWidth()) {
                    if (logs.isEmpty()) {
                        Text(
                            text = stringResource(R.string.network_error_logs_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        logs.forEach { entry ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clickable { selected = entry },
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            text = entry.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "×${entry.count}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = entry.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${stringResource(R.string.network_error_logs_last_seen)}: ${df.format(Date(entry.lastSeenAt))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    SnackbarHost(hostState = snackbarHostState)

    val current = selected
    if (current != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = {
                Button(
                    onClick = {
                        val text = exportSingleText(current, df)
                        clipboard.setText(AnnotatedString(text))
                        scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
                    }
                ) { Text(stringResource(R.string.network_error_logs_copy)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { selected = null }) {
                    Text(stringResource(R.string.network_error_logs_close))
                }
            },
            title = { Text(current.title) },
            text = {
                Column {
                    Text(
                        text = "count=${current.count}\nfirst=${df.format(Date(current.firstSeenAt))}\nlast=${df.format(Date(current.lastSeenAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = current.lastDetails,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!current.lastNetworkSnapshot.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = current.lastNetworkSnapshot,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    }
}

private fun exportAllText(logs: List<NetworkErrorLogEntry>, df: SimpleDateFormat): String {
    return buildString {
        appendLine("=== Network Error Logs ===")
        appendLine("unique=${logs.size}")
        logs.forEach { e ->
            appendLine()
            appendLine("-----")
            appendLine("${e.title}  x${e.count}")
            appendLine("first=${df.format(Date(e.firstSeenAt))}")
            appendLine("last=${df.format(Date(e.lastSeenAt))}")
            appendLine("summary=${e.summary}")
            appendLine()
            appendLine(e.lastDetails)
            if (!e.lastNetworkSnapshot.isNullOrBlank()) {
                appendLine()
                appendLine(e.lastNetworkSnapshot)
            }
            if (e.recentSamples.isNotEmpty()) {
                appendLine()
                appendLine("== recentSamples ==")
                e.recentSamples.forEach { s -> appendLine(s) }
            }
        }
        appendLine()
        appendLine("=== END ===")
    }.trimEnd()
}

private fun exportSingleText(e: NetworkErrorLogEntry, df: SimpleDateFormat): String {
    return buildString {
        appendLine("=== Network Error Log ===")
        appendLine("${e.title}  x${e.count}")
        appendLine("first=${df.format(Date(e.firstSeenAt))}")
        appendLine("last=${df.format(Date(e.lastSeenAt))}")
        appendLine("summary=${e.summary}")
        appendLine()
        appendLine(e.lastDetails)
        if (!e.lastNetworkSnapshot.isNullOrBlank()) {
            appendLine()
            appendLine(e.lastNetworkSnapshot)
        }
        if (e.recentSamples.isNotEmpty()) {
            appendLine()
            appendLine("== recentSamples ==")
            e.recentSamples.forEach { s -> appendLine(s) }
        }
        appendLine()
        appendLine("=== END ===")
    }.trimEnd()
}


