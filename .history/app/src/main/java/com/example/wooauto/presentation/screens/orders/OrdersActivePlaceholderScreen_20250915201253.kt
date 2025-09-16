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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun OrdersActivePlaceholderScreen(
	viewModel: OrdersViewModel = hiltViewModel()
) {
	LaunchedEffect(Unit) { viewModel.startProcessingBuckets() }
	val newList = viewModel.newProcessingOrders.collectAsState().value
	val inProcList = viewModel.inProcessingOrders.collectAsState().value

	Row(modifier = Modifier.fillMaxSize().padding(12.dp)) {
		Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
			Text(text = "New orders (${newList.size})", style = MaterialTheme.typography.titleMedium)
			LazyColumn(modifier = Modifier.fillMaxSize()) {
				items(newList) { order ->
					ActiveOrderCard(
						order = order,
						isNew = true,
						onStartProcessing = { viewModel.markOrderAsRead(order.id) },
						onOpenDetails = { viewModel.getOrderDetails(order.id) }
					)
				}
			}
		}
		Spacer(modifier = Modifier.width(12.dp))
		Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
			Text(text = "In processing (${inProcList.size})", style = MaterialTheme.typography.titleMedium)
			LazyColumn(modifier = Modifier.fillMaxSize()) {
				items(inProcList) { order ->
					ActiveOrderCard(
						order = order,
						isNew = false,
						onStartProcessing = { },
						onOpenDetails = { viewModel.getOrderDetails(order.id) }
					)
				}
			}
		}
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
		modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
			Text(text = order.customerName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
			val dateText = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(order.dateCreated)
			Text(text = dateText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			if (order.total.isNotEmpty()) {
				Text(text = order.total, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
				Text(
					text = if (isNew) "Start processing" else "View details",
					color = MaterialTheme.colorScheme.primary,
					modifier = Modifier
						.padding(end = 16.dp)
						.clickable { if (isNew) onStartProcessing() else onOpenDetails() }
				)
				Text(text = "Details", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onOpenDetails() })
			}
		}
	}
}
