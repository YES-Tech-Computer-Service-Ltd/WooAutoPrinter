package com.example.wooauto.ui.orders

import android.content.Context
import android.content.res.Resources
import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wooauto.R
import com.example.wooauto.data.api.models.LineItem
import com.example.wooauto.data.database.entities.OrderEntity
import com.example.wooauto.ui.components.ErrorState
import com.example.wooauto.ui.components.LoadingIndicator
import com.example.wooauto.ui.components.StatusBadge
import com.example.wooauto.ui.components.getOrderStatusColor
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailsScreen(
    orderId: Long,
    onBackClick: () -> Unit,
    viewModel: OrderViewModel = viewModel()
) {
    val orderDetailState by viewModel.orderDetailState.collectAsState()

    // Load order details
    LaunchedEffect(orderId) {
        viewModel.loadOrderDetails(orderId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.order_details)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (orderDetailState) {
                is OrderDetailState.Loading -> {
                    LoadingIndicator()
                }
                is OrderDetailState.Success -> {
                    val order = (orderDetailState as OrderDetailState.Success).order
                    OrderDetailsContent(
                        order = order,
                        onMarkAsCompleteClick = { viewModel.markOrderAsComplete(orderId) },
                        onPrintOrderClick = { viewModel.printOrder(orderId) },
                        onValidateClick = { viewModel.validateOrderData(orderId) }
                    )
                }
                is OrderDetailState.Error -> {
                    val message = (orderDetailState as OrderDetailState.Error).message
                    ErrorState(
                        message = message,
                        onRetry = { viewModel.loadOrderDetails(orderId) }
                    )
                }
            }
        }
    }
}

@Composable
fun OrderDetailsContent(

    order: OrderEntity,
    isUpdating: Boolean = false,
    onMarkAsCompleteClick: () -> Unit,
    onPrintOrderClick: () -> Unit,
    onValidateClick: () -> Unit = {}

) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Order header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Order number and status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.order_number, order.number),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        StatusBadge(
                            status = order.status,
                            color = getOrderStatusColor(order.status)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Order date
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val formattedDate = dateFormat.format(order.dateCreated)
                    Text(
                        text = stringResource(R.string.order_date, formattedDate),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Payment method
                    Text(
                        text = "Payment: ${order.paymentMethodTitle}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Order Method (if available)
                    order.orderMethod?.let { method ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            val iconRes = when (method.lowercase()) {
                                "delivery" -> R.drawable.ic_delivery
                                "pickup" -> R.drawable.ic_pickup
                                else -> R.drawable.ic_order
                            }

                            Icon(
                                painter = painterResource(id = iconRes),
                                contentDescription = "Delivery Method",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                text = "Delivery Method: ${method.replaceFirstChar { it.uppercase() }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Order total
                    Text(
                        text = stringResource(R.string.order_amount, order.total),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Delivery details (if available)
        if (order.deliveryDate != null || order.deliveryTime != null || order.orderMethod != null || 
            order.tip != null || order.deliveryFee != null) {
            item {
                Log.d("OrderDetails", """
                    ===== 显示订单配送信息 =====
                    订单ID: ${order.id}
                    订单编号: ${order.number}
                    配送信息:
                    - 配送方式: ${order.orderMethod ?: "未设置"}
                    - 配送日期: ${order.deliveryDate ?: "未设置"}
                    - 配送时间: ${order.deliveryTime ?: "未设置"}
                    费用信息:
                    - 小费: ${order.tip ?: "未设置"}
                    - 配送费: ${order.deliveryFee ?: "未设置"}
                """.trimIndent())

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.delivery_details),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 配送方式
                        order.orderMethod?.let { method ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                val iconRes = when (method.lowercase()) {
                                    "delivery" -> R.drawable.ic_delivery
                                    "pickup" -> R.drawable.ic_pickup
                                    else -> R.drawable.ic_order
                                }

                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = stringResource(R.string.delivery_method, method),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        // 配送日期和时间
                        if (order.deliveryDate != null || order.deliveryTime != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_calendar),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Column {
                                    order.deliveryDate?.let { date ->
                                        Text(
                                            text = stringResource(R.string.delivery_date, date),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    order.deliveryTime?.let { time ->
                                        Text(
                                            text = stringResource(R.string.delivery_time, time),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }

                        // 费用信息
                        if (order.tip != null || order.deliveryFee != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Text(
                                text = stringResource(R.string.fee_details),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            order.tip?.let { tip ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.tip),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = tip,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            order.deliveryFee?.let { fee ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.delivery_fee),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = fee,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Log.d("OrderDetails", """
                ===== 订单无配送信息 =====
                订单ID: ${order.id}
                订单编号: ${order.number}
                所有字段均为空:
                - 配送方式: ${order.orderMethod}
                - 配送日期: ${order.deliveryDate}
                - 配送时间: ${order.deliveryTime}
                - 小费: ${order.tip}
                - 配送费: ${order.deliveryFee}
            """.trimIndent())
        }

        // Customer info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Customer Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.customer_name, order.customerName),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "Shipping Address",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = order.shippingAddress,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "Billing Address",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = order.billingAddress,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Order items
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Order Items",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Parse line items from JSON
                    val gson = Gson()
                    val itemType = object : TypeToken<List<LineItem>>() {}.type
                    val lineItems: List<LineItem> = try {
                        Log.d("OrderDetails", "解析订单项目, JSON长度: ${order.lineItemsJson.length}")
                        val items = gson.fromJson<List<LineItem>>(order.lineItemsJson, itemType) ?: emptyList()
                        Log.d("OrderDetails", "解析结果项目数量: ${items.size}")
                        items
                    } catch (e: Exception) {
                        Log.e("OrderDetails", "解析订单项目JSON失败", e)
                        emptyList()
                    }

                    if (lineItems.isEmpty()) {
                        Text(
                            text = "No items available",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Product",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(4f)
                            )
                            Text(
                                text = "Qty",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Total",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(2f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider()

                        // Items
                        lineItems.forEach { item ->
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Format the product name to remove the raw metadata
                                val formattedName = getFormattedProductName(item)
                                Text(
                                    text = formattedName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(4f)
                                )
                                Text(
                                    text = item.quantity.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = item.total,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(2f)
                                )
                            }

                            // Show formatted options and customizations
                            val options = getProductOptions(item)
                            options.forEach { option ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp)
                                ) {
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Show other metadata (excluding the ones we've already handled)
                            item.metaData?.forEach { meta ->
                                if (meta.key != "_" && meta.key != "_qty" && meta.key != "_exceptions") {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp)
                                    ) {
                                        Text(
                                            text = "${meta.key}: ${meta.value}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        // Order Summary with fees
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Order Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Display delivery fee if available
                    order.deliveryFee?.let { fee ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Delivery Fee",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = fee,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Display tip if available
                    order.tip?.let { tip ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Tip",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = tip,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Total
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = order.total,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Actions
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Print button
                Button(
                    onClick = onPrintOrderClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_print),
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.print_order))
                    }
                }

                // Mark as complete button (if not already completed)
                if (order.status != "completed") {
                    Button(
                        onClick = onMarkAsCompleteClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.mark_as_complete))
                    }
                }

                // Print status
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (order.isPrinted)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else
                                MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (order.isPrinted) "Order has been printed" else "Order has not been printed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (order.isPrinted)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * 检查资源是否存在
 */
fun hasResource(context: Context, resId: Int): Boolean {
    return try {
        context.resources.getResourceName(resId)
        true
    } catch (e: Resources.NotFoundException) {
        false
    }
}

/**
 * 格式化产品名称，移除原始元数据并显示所选选项
 */
@Composable
private fun getFormattedProductName(item: LineItem): String {
    // Start with the original name
    var name = item.name

    // Check if there's an _exceptions field with options
    val optionValue = getSelectedOptionValue(item)

    if (optionValue.isNotEmpty()) {
        // For items like "N6 House Special Low Mien or Shanghai Noodles"
        // We want to show "N6 House Special Shanghai Noodles"
        if (name.contains(" or ")) {
            val baseName = name.substring(0, name.indexOf(" or "))
            name = "$baseName $optionValue"
        }
    }

    return name
}

/**
 * 从_exceptions元数据中提取选项值
 */
private fun getSelectedOptionValue(item: LineItem): String {
    item.metaData?.forEach { meta ->
        if (meta.key == "_exceptions") {
            val value = meta.value.toString()

            // Extract value from format: {(name=, value=Shanghai Noodles (Hig Round Noodles), type_of_price=, price=0.0, _type=radio)}
            val valuePattern = "value=([^,)]+)"
            val matcher = Regex(valuePattern).find(value)
            return matcher?.groupValues?.getOrNull(1)?.trim() ?: ""
        }
    }
    return ""
}

/**
 * 获取所有产品选项，以便格式化显示
 */
private fun getProductOptions(item: LineItem): List<String> {
    val options = mutableListOf<String>()

    item.metaData?.forEach { meta ->
        when (meta.key) {
            "_exceptions" -> {
                val value = meta.value.toString()
                if (value.contains("value=")) {
                    // Format: {(name=, value=Shanghai Noodles (Hig Round Noodles), type_of_price=, price=0.0, _type=radio)}
                    val typePattern = "_type=([^,)]+)"
                    val valuePattern = "value=([^,)]+)"

                    val typeMatch = Regex(typePattern).find(value)
                    val valueMatch = Regex(valuePattern).find(value)

                    val optionType = typeMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
                    val optionValue = valueMatch?.groupValues?.getOrNull(1)?.trim() ?: ""

                    if (optionValue.isNotEmpty()) {
                        if (optionType == "radio") {
                            // For radio options (single selection)
                            options.add("Option: $optionValue")
                        } else {
                            // For other option types
                            options.add("$optionType: $optionValue")
                        }
                    }
                }
            }
        }
    }

    return options
}