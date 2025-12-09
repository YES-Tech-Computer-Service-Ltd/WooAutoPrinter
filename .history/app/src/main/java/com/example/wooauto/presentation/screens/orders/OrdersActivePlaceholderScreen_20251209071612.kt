package com.example.wooauto.presentation.screens.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

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
    val newList = viewModel.newProcessingOrders.collectAsState().value
    val inProcList = viewModel.inProcessingOrders.collectAsState().value
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
                                onStartProcessing = {
                                    viewModel.startProcessingFromCard(order.id)
                                },
                                onOpenDetails = {
                                    viewModel.openOrderDetails(
                                        order.id,
                                        OrdersViewModel.OrderDetailMode.NEW
                                    )
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
                                onStartProcessing = { },
                                onOpenDetails = {
                                    viewModel.openOrderDetails(
                                        order.id,
                                        OrdersViewModel.OrderDetailMode.PROCESSING
                                    )
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

            // show order details dialog if selected
            ActiveOrderDetailsHost(viewModel)
        }

	}

	// 关闭 OrdersActivePlaceholderScreen 函数体
}

// 结束 OrdersActivePlaceholderScreen 组合函数，以下为文件级别的私有可组合函数

    // 详情弹窗（沿用 History 的组件）
    @Composable
    private fun ActiveOrderDetailsHost(viewModel: OrdersViewModel) {
        val selectedOrder = viewModel.selectedOrder.collectAsState().value
        val mode = viewModel.selectedDetailMode.collectAsState().value
        if (selectedOrder != null) {
            OrderDetailDialog(
                order = selectedOrder,
                onDismiss = { viewModel.clearSelectedOrder() },
                mode = when (mode) {
                    OrdersViewModel.OrderDetailMode.NEW -> DetailMode.NEW
                    OrdersViewModel.OrderDetailMode.PROCESSING -> DetailMode.PROCESSING
                    else -> DetailMode.AUTO
                },
                onStatusChange = { id, status ->
                    viewModel.updateOrderStatus(id, status)
                    // 由 Active 宿主负责真正关闭（不重置模式，避免在刷新期间再弹回）
                    viewModel.clearSelectedOrder()
                },
                onMarkAsPrinted = { id -> viewModel.markOrderAsPrinted(id) },
                onMarkAsRead = { id -> viewModel.markOrderAsRead(id) }
            )
        }
    }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val dateText =
                        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                            .format(order.dateCreated)
                    Text(
                        text = dateText,
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
