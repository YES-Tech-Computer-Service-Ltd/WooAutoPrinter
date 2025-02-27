package com.example.wooauto.ui.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wooauto.R
import com.example.wooauto.data.database.entities.OrderEntity
import com.example.wooauto.ui.components.EmptyState
import com.example.wooauto.ui.components.LoadingIndicator
import com.example.wooauto.ui.components.SearchField
import com.example.wooauto.ui.components.StatusBadge
import com.example.wooauto.ui.components.getOrderStatusColor
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    onOrderClick: (Long) -> Unit,
    viewModel: OrderViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedStatusFilter by viewModel.selectedStatusFilter.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val printAllState by viewModel.printAllState.collectAsState()
    val apiConfigured by viewModel.apiConfigured.collectAsState()

    val pullToRefreshState = rememberPullToRefreshState()

    if (pullToRefreshState.isRefreshing && apiConfigured) {
        LaunchedEffect(true) {
            viewModel.refreshOrders()
        }
    }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }

    // Order status filter options
    val statusFilters = remember {
        listOf(
            "pending",
            "processing",
            "on-hold",
            "completed",
            "cancelled",
            "refunded",
            "failed"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.orders)) },
                actions = {
                    if (apiConfigured) {
                        // Print all unprinted orders button
                        IconButton(onClick = { viewModel.printAllUnprintedOrders() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_print),
                                contentDescription = stringResource(R.string.print_all_unprinted)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                if (apiConfigured) {
                    // Search bar
                    SearchField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = stringResource(R.string.search_orders)
                    )

                    // Status filters
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "All" filter option
                        item {
                            FilterChip(
                                selected = selectedStatusFilter == null,
                                onClick = { viewModel.updateStatusFilter(null) },
                                label = { Text("All") }
                            )
                        }

                        // Status filter options
                        items(statusFilters) { status ->
                            FilterChip(
                                selected = selectedStatusFilter == status,
                                onClick = { viewModel.updateStatusFilter(status) },
                                label = { Text(status.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }

                    // Print all unprinted orders status
                    when (printAllState) {
                        is PrintAllState.Printing -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Printing all unprinted orders...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        is PrintAllState.Completed -> {
                            val state = printAllState as PrintAllState.Completed
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Printed ${state.printed} orders, ${state.failed} failed",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            // Reset the print all state after showing the result
                            LaunchedEffect(printAllState) {
                                kotlinx.coroutines.delay(3000)
                                viewModel.resetPrintAllState()
                            }
                        }
                        is PrintAllState.NoOrdersToPrint -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No unprinted orders to print",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            // Reset the print all state after showing the result
                            LaunchedEffect(printAllState) {
                                kotlinx.coroutines.delay(3000)
                                viewModel.resetPrintAllState()
                            }
                        }
                        is PrintAllState.Error -> {
                            val state = printAllState as PrintAllState.Error
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Error: ${state.message}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            // Reset the print all state after showing the error
                            LaunchedEffect(printAllState) {
                                kotlinx.coroutines.delay(3000)
                                viewModel.resetPrintAllState()
                            }
                        }
                        else -> { /* Do nothing for Idle state */ }
                    }
                }

                // Orders content
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (uiState) {
                        is OrdersUiState.Loading -> {
                            LoadingIndicator()
                        }
                        is OrdersUiState.Empty -> {
                            EmptyState(
                                message = stringResource(R.string.no_orders_found),
                                icon = R.drawable.ic_order
                            )
                        }
                        is OrdersUiState.Success -> {
                            val orders = (uiState as OrdersUiState.Success).orders
                            OrdersList(
                                orders = orders,
                                onOrderClick = onOrderClick,
                                onMarkAsCompleteClick = { viewModel.markOrderAsComplete(it) }
                            )
                        }
                        is OrdersUiState.Error -> {
                            val message = (uiState as OrdersUiState.Error).message
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        is OrdersUiState.ApiNotConfigured -> {
                            // API未配置状态，显示友好提示，引导用户去设置
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_web),
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = "请先在设置中配置WooCommerce API",
                                        style = MaterialTheme.typography.titleMedium,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "前往 '设置 > 网站连接'进行配置",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    if (apiConfigured && (pullToRefreshState.isRefreshing || pullToRefreshState.progress > 0)) {
                        PullToRefreshContainer(
                            state = pullToRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OrdersList(
    orders: List<OrderEntity>,
    onOrderClick: (Long) -> Unit,
    onMarkAsCompleteClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        items(
            items = orders,
            key = { it.id }
        ) { order ->
            OrderItem(
                order = order,
                onClick = { onOrderClick(order.id) },
                onMarkAsCompleteClick = { onMarkAsCompleteClick(order.id) }
            )
        }
    }
}

@Composable
fun OrderItem(
    order: OrderEntity,
    onClick: () -> Unit,
    onMarkAsCompleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Order header (number, date, status)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.order_number, order.number),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                StatusBadge(
                    status = order.status,
                    color = getOrderStatusColor(order.status)
                )
            }

            order.orderMethod?.let { method ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(
                            id = when (method.lowercase()) {
                                "delivery" -> R.drawable.ic_delivery
                                "pickup" -> R.drawable.ic_pickup
                                else -> R.drawable.ic_order
                            }
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = method.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Order date
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val formattedDate = dateFormat.format(order.dateCreated)
            Text(
                text = stringResource(R.string.order_date, formattedDate),
                style = MaterialTheme.typography.bodyMedium
            )

            // Customer name
            Text(
                text = stringResource(R.string.customer_name, order.customerName),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Order total
            Text(
                text = stringResource(R.string.order_amount, order.total),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Show Mark as Complete button only for non-completed orders
                if (order.status != "completed") {
                    Button(
                        onClick = { onMarkAsCompleteClick() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.mark_as_complete),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Print status indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_print),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (order.isPrinted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (order.isPrinted) "Printed" else "Not Printed",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}