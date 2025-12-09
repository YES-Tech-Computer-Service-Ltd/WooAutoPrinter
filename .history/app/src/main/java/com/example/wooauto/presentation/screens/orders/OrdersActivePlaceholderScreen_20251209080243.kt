package com.example.wooauto.presentation.screens.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun OrdersActivePlaceholderScreen(
    viewModel: OrdersViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { viewModel.startProcessingBuckets() }
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { ev ->
            val statusLabel = when (ev.newStatus) {
                "processing" -> "processing"
                "completed" -> "completed"
                "pending" -> "pending"
                "on-hold" -> "on-hold"
                "cancelled" -> "cancelled"
                "refunded" -> "refunded"
                "failed" -> "failed"
                else -> ev.newStatus
            }
            val msg = "#${ev.orderNumber} → ${statusLabel}"
            snackbarHostState.showSnackbar(msg)
        }
    }
    val newList by viewModel.newProcessingOrders.collectAsState()
    val inProcList by viewModel.inProcessingOrders.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    var showStartAllConfirm by remember { mutableStateOf(false) }
    var showCompleteAllConfirm by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    androidx.compose.material3.Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(hostState = snackbarHostState) }
    ) { pad ->
        Box(
            modifier = Modifier
				.fillMaxSize()
				.padding(pad)
				.padding(12.dp)
        ) {
            val spacing = 8.dp
            val dividerWidth = 1.dp
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
						.fillMaxHeight()
						.weight(1f)
                ) {
                    SectionHeader(title = stringResource(id = com.example.wooauto.R.string.orders_new), count = newList.size, actions = {
                        if (newList.size >= 2) {
                            TextButton(onClick = { showStartAllConfirm = true }) {
                                Text(text = stringResource(id = com.example.wooauto.R.string.start_all))
                            }
                        }
                    })
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(newList) { order ->
                            ActiveOrderCard(
                                order = order,
                                isNew = true,
                                currencySymbol = currencySymbol,
                                onStartProcessing = {
                                    viewModel.startProcessingFromCard(order.id)
                                },
                                onOpenDetails = {
                                    scope.launch {
                                        com.example.wooauto.presentation.EventBus.emitOpenOrderDetail(order)
                                    }
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(spacing))
                Box(
                    modifier = Modifier
						.fillMaxHeight()
						.width(dividerWidth)
						.background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                )
                Spacer(modifier = Modifier.width(spacing))
                Column(
                    modifier = Modifier
						.fillMaxHeight()
						.weight(1f)
                ) {
                    SectionHeader(title = stringResource(id = com.example.wooauto.R.string.orders_in_processing), count = inProcList.size, actions = {
                        if (inProcList.size >= 2) {
                            TextButton(onClick = { showCompleteAllConfirm = true }) {
                                Text(text = stringResource(id = com.example.wooauto.R.string.complete_all))
                            }
                        }
                    })
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(inProcList) { order ->
                            ActiveOrderCard(
                                order = order,
                                isNew = false,
                                currencySymbol = currencySymbol,
                                onStartProcessing = { },
                                onOpenDetails = {
                                    scope.launch {
                                        com.example.wooauto.presentation.EventBus.emitOpenOrderDetail(order)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 确认对话框：开始处理（新订单）
            if (showStartAllConfirm) {
                Dialog(onDismissRequest = { showStartAllConfirm = false }, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)) {
                    Card(shape = CardDefaults.shape) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(text = stringResource(id = com.example.wooauto.R.string.confirm_start_all_title), style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(0.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showStartAllConfirm = false }) { Text(text = stringResource(id = com.example.wooauto.R.string.cancel)) }
                                Spacer(modifier = Modifier.width(12.dp))
                                TextButton(onClick = {
                                    viewModel.batchStartProcessingForNewOrders()
                                    showStartAllConfirm = false
                                }) { Text(text = stringResource(id = com.example.wooauto.R.string.confirm)) }
                            }
                        }
                    }
                }
            }

            // 确认对话框：批量完成（处理中）
            if (showCompleteAllConfirm) {
                Dialog(onDismissRequest = { showCompleteAllConfirm = false }, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)) {
                    Card(shape = CardDefaults.shape) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(text = stringResource(id = com.example.wooauto.R.string.confirm_complete_all_title), style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(0.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showCompleteAllConfirm = false }) { Text(text = stringResource(id = com.example.wooauto.R.string.cancel)) }
                                Spacer(modifier = Modifier.width(12.dp))
                                TextButton(onClick = {
                                    viewModel.batchCompleteProcessingOrders()
                                    showCompleteAllConfirm = false
                                }) { Text(text = stringResource(id = com.example.wooauto.R.string.confirm)) }
                            }
                        }
                    }
                }
            }

            // show order details dialog is handled by EventBus in WooAutoApp
        }

	}

private fun cleanNotesForDisplay(notes: String): String {
    if (notes.isBlank()) return notes
    return notes
        .lines()
        .filterNot { line ->
            val trimmed = line.trim()
            trimmed.startsWith("--- 元数据") || trimmed.startsWith("exwfood_")
        }
        .joinToString("\n")
        .trim()
}

	// 关闭 OrdersActivePlaceholderScreen 函数体
}

// 结束 OrdersActivePlaceholderScreen 组合函数，以下为文件级别的私有可组合函数

    // 详情弹窗已移至 EventBus 全局处理
    // private fun ActiveOrderDetailsHost... removed

    @Composable
    private fun SectionHeader(title: String, count: Int, actions: (@Composable () -> Unit)? = null) {
        Row(
            modifier = Modifier
				.fillMaxWidth()
				.padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$title ($count)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            actions?.invoke()
        }
    }

    @Composable
    private fun ActiveOrderCard(
        order: com.example.wooauto.domain.models.Order,
        isNew: Boolean,
        onStartProcessing: () -> Unit,
        onOpenDetails: () -> Unit
    ) {
        Card(
            modifier = Modifier
				.fillMaxWidth()
				.padding(vertical = 6.dp)
				.clickable { onOpenDetails() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
					.fillMaxWidth()
					.padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "#${order.number}", style = MaterialTheme.typography.titleMedium)
                    if (isNew) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NEW",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = order.customerName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                // 时间显示：优先显示 WooFood 的 deliveryTime (预期时间)，否则显示 dateCreated (下单时间)
                val expectedTime = order.woofoodInfo?.deliveryTime?.takeIf { it.isNotBlank() }
                val displayTime = expectedTime ?: java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(order.dateCreated)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = displayTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (order.total.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AttachMoney,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = order.total,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 备注信息（仿照 OrderDetailDialog 样式，带过滤和黄色背景）
                val orderNote = cleanNotesForDisplay(order.notes)

                if (orderNote.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(androidx.compose.ui.graphics.Color(0xFFFFF9C4), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color(0xFFF57F17),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(com.example.wooauto.R.string.order_note), // 使用资源
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color(0xFFF57F17)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = orderNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = androidx.compose.ui.graphics.Color.Black
                            )
                        }
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isNew) {
                        OutlinedButton(onClick = onStartProcessing) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Start processing")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(0.dp))
                    }
					TextButton(onClick = onOpenDetails) { Text(text = "Details") }
				}
			}
		}
	}
