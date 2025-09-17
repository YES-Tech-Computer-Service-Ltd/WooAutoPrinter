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
			Text(text = "New orders", style = MaterialTheme.typography.titleMedium)
			Spacer(modifier = Modifier.width(8.dp))
			LazyColumn(modifier = Modifier.fillMaxSize()) {
				items(newList) { order ->
					Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
						Text(text = "#${order.number} - ${order.customerName}", modifier = Modifier.padding(12.dp))
					}
				}
			}
		}
		Spacer(modifier = Modifier.width(12.dp))
		Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
			Text(text = "In processing", style = MaterialTheme.typography.titleMedium)
			LazyColumn(modifier = Modifier.fillMaxSize()) {
				items(inProcList) { order ->
					Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
						Text(text = "#${order.number} - ${order.customerName}", modifier = Modifier.padding(12.dp))
					}
				}
			}
		}
	}
}
