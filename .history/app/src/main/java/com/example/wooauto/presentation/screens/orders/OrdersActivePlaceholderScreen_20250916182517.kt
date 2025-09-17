package com.example.wooauto.presentation.screens.orders

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wooauto.presentation.screens.orders.OrderDetailDialog

@Composable
fun OrdersActivePlaceholderScreen(
	viewModel: OrdersViewModel = hiltViewModel()
) {
	LaunchedEffect(Unit) { viewModel.startProcessingBuckets() }
	val newList = viewModel.newProcessingOrders.collectAsState().value
	val inProcList = viewModel.inProcessingOrders.collectAsState().value

	Row(modifier = Modifier.fillMaxSize().padding(12.dp)) {
		Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
			SectionHeader(title = "New orders", count = newList.size)
			LazyColumn(modifier = Modifier.fillMaxSize()) {
				items(newList) { order ->
                    ActiveOrderCard(
						order = order,
						isNew = true,
						onStartProcessing = {
                            viewModel.startProcessingFromCard(order.id)
						},
                        onOpenDetails = { viewModel.openOrderDetails(order.id, OrdersViewModel.OrderDetailMode.NEW) }
					)
				}
			}
		}
		Spacer(modifier = Modifier.width(8.dp))
		Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)))
		Spacer(modifier = Modifier.width(8.dp))
		Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
			SectionHeader(title = "In processing", count = inProcList.size)
			LazyColumn(modifier = Modifier.fillMaxSize()) {
				items(inProcList) { order ->
					ActiveOrderCard(
						order = order,
						isNew = false,
						onStartProcessing = { },
                        onOpenDetails = { viewModel.openOrderDetails(order.id, OrdersViewModel.OrderDetailMode.PROCESSING) }
					)
				}
			}
		}
	}

	// show order details dialog if selected
	ActiveOrderDetailsHost(viewModel)
}

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
                OrdersViewModel.OrderDetailMode.NEW -> com.example.wooauto.presentation.screens.orders.DetailMode.NEW
                OrdersViewModel.OrderDetailMode.PROCESSING -> com.example.wooauto.presentation.screens.orders.DetailMode.PROCESSING
                else -> com.example.wooauto.presentation.screens.orders.DetailMode.AUTO
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
private fun SectionHeader(title: String, count: Int) {
	Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
		Text(text = "$title ($count)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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
		Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(text = "#${order.number}", style = MaterialTheme.typography.titleMedium)
				if (isNew) {
					Spacer(modifier = Modifier.width(8.dp))
					Text(text = "NEW", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
				}
			}
			Row(verticalAlignment = Alignment.CenterVertically) {
				Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
				Spacer(modifier = Modifier.width(6.dp))
				Text(text = order.customerName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
			}
			Row(verticalAlignment = Alignment.CenterVertically) {
				Icon(imageVector = Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
				Spacer(modifier = Modifier.width(6.dp))
				val dateText = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(order.dateCreated)
				Text(text = dateText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			if (order.total.isNotEmpty()) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Icon(imageVector = Icons.Default.AttachMoney, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
					Spacer(modifier = Modifier.width(6.dp))
					Text(text = order.total, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			}
			HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (isNew) {
                    OutlinedButton(onClick = onStartProcessing) {
						Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
						Spacer(modifier = Modifier.width(6.dp))
						Text(text = "Start processing")
					}
				}
				Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenDetails) { Text(text = "Details") }
			}
		}
	}
}
