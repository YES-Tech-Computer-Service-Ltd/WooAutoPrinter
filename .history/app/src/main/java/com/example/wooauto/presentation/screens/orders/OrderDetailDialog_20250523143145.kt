package com.example.wooauto.presentation.screens.orders

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wooauto.R
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.OrderItem
import com.example.wooauto.domain.templates.TemplateType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import com.example.wooauto.licensing.LicenseStatus

/**
 * 订单详情对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composablefun OrderDetailDialog(    order: Order,    onDismiss: () -> Unit,    onStatusChange: (Long, String) -> Unit,    onMarkAsPrinted: (Long) -> Unit) {    val viewModel: OrdersViewModel = hiltViewModel()    remember { viewModel.licenseManager }    val licenseInfo by viewModel.licenseManager.licenseInfo.observeAsState()    val currencySymbol by viewModel.currencySymbol.collectAsState()
    
    var showStatusOptions by remember { mutableStateOf(false) }
    var showTemplateOptions by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    
    // 观察当前选中的订单，以便实时更新UI
    val currentOrder by viewModel.selectedOrder.collectAsState()
    
    // 使用当前的订单信息（如果有更新）或者传入的订单
    val displayOrder = currentOrder ?: order
    
    // 记录订单信息用于调试
    Log.d("OrderDetailDialog", "【打印状态修复】初始化订单详情对话框:")
    Log.d("OrderDetailDialog", "【打印状态修复】传入的order: ID=${order.id}, 打印状态=${order.isPrinted}")
    Log.d("OrderDetailDialog", "【打印状态修复】currentOrder: ID=${currentOrder?.id}, 打印状态=${currentOrder?.isPrinted}")
    Log.d("OrderDetailDialog", "【打印状态修复】最终使用的displayOrder: ID=${displayOrder.id}, 打印状态=${displayOrder.isPrinted}")
    
    // 定义打印状态相关变量
    val printStatusText = if (displayOrder.isPrinted) stringResource(R.string.printed_yes) else stringResource(R.string.printed_no)
    val printStatusColor = if (displayOrder.isPrinted) Color(0xFF4CAF50) else Color(0xFFE53935)
    
    Log.d("OrderDetailDialog", "显示订单详情，订单ID: ${displayOrder.id}, 打印状态: ${displayOrder.isPrinted}")
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // 顶部栏
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.order_details) + " #${displayOrder.number}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.close)
                            )
                        }
                    },
                    actions = {
                        // 移除右上角按钮，不再显示任何内容
                    }
                )
                
                // 内容区（可滚动）
                Box(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp)
                    ) {
                        // 只显示订单号，移除重复的订单ID行
                        OrderDetailRow(
                            label = stringResource(R.string.order_number_label), 
                            value = "#${displayOrder.number}",
                            icon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Article,
                                    contentDescription = stringResource(R.string.order_number),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                        
                        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        val formattedDate = dateFormat.format(displayOrder.dateCreated)
                        OrderDetailRow(
                            label = stringResource(R.string.order_date_label),
                            value = formattedDate,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = stringResource(R.string.order_date),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                        
                        OrderDetailRow(
                            label = stringResource(R.string.customer_name_label),
                            value = displayOrder.customerName,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = stringResource(R.string.customer_name),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                        
                        // 手机号
                        OrderDetailRow(
                            label = stringResource(R.string.contact_info_label),
                            value = displayOrder.contactInfo.ifEmpty { stringResource(R.string.not_provided) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = stringResource(R.string.contact_info),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                        
                        // 打印状态显示
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.printed_status),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.width(80.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Icon(
                                imageVector = if (displayOrder.isPrinted) Icons.Default.CheckCircle else Icons.Default.Print,
                                contentDescription = stringResource(R.string.printed_status),
                                modifier = Modifier.size(16.dp),
                                tint = printStatusColor
                            )
                            
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            Text(
                                text = printStatusText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = printStatusColor
                            )
                            
                            if (!displayOrder.isPrinted) {
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                OutlinedButton(
                                    onClick = { onMarkAsPrinted(displayOrder.id) },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                                    enabled = licenseInfo?.status == LicenseStatus.VALID || licenseInfo?.status == LicenseStatus.TRIAL
                                ) {
                                    Text(
                                        text = stringResource(R.string.mark_as_printed),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        
                        if (displayOrder.billingInfo.isNotEmpty()) {
                            OrderDetailRow(
                                label = stringResource(R.string.billing_address),
                                value = displayOrder.billingInfo,
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = stringResource(R.string.billing_address),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                        
                        // 显示订单备注信息
                        if (displayOrder.notes.isNotEmpty()) {
                            OrderDetailRow(
                                label = stringResource(R.string.order_note),
                                value = displayOrder.notes,
                                icon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.TextSnippet,
                                        contentDescription = stringResource(R.string.order_note),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                        
                        // 显示WooFood信息（如果有）
                        displayOrder.woofoodInfo?.let { wooFoodInfo ->
                            // 添加一个显眼的订单方式标签
                            val orderMethodColor = if (wooFoodInfo.isDelivery) {
                                Color(0xFF2196F3) // 蓝色用于外卖
                            } else {
                                Color(0xFF4CAF50) // 绿色用于自取
                            }
                            
                            val orderMethodText = if (wooFoodInfo.isDelivery) 
                                stringResource(R.string.delivery_order) 
                            else 
                                stringResource(R.string.pickup_order)
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = orderMethodColor.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (wooFoodInfo.isDelivery) 
                                                Icons.Default.LocalShipping 
                                            else 
                                                Icons.Default.Restaurant,
                                            contentDescription = orderMethodText,
                                            modifier = Modifier.size(18.dp),
                                            tint = orderMethodColor
                                        )
                                        
                                        Spacer(modifier = Modifier.width(6.dp))
                                        
                                        Text(
                                            text = orderMethodText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = orderMethodColor
                                        )
                                    }
                                }
                            }
                            
                            // 显示地址或者自取信息
                            if (wooFoodInfo.isDelivery) {
                                wooFoodInfo.deliveryAddress?.let { address ->
                                    OrderDetailRow(
                                        label = stringResource(R.string.delivery_address),
                                        value = address,
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.Home,
                                                contentDescription = stringResource(R.string.delivery_address),
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                }
                            } else {
                                OrderDetailRow(
                                    label = stringResource(R.string.pickup_location),
                                    value = displayOrder.billingInfo.split("\n").firstOrNull() ?: stringResource(R.string.in_store_pickup),
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = stringResource(R.string.pickup_location),
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                            
                            // 统一显示预计时间（配送/自取）
                            wooFoodInfo.deliveryTime?.let { time ->
                                OrderDetailRow(
                                    label = if (wooFoodInfo.isDelivery) stringResource(R.string.delivery_time) else stringResource(R.string.pickup_time),
                                    value = time,
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = if (wooFoodInfo.isDelivery) stringResource(R.string.delivery_time) else stringResource(R.string.pickup_time),
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                        }
                        
                        // 显示订单商品列表
                        Text(
                            text = stringResource(R.string.product_list),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                        
                        if (displayOrder.items.isNotEmpty()) {
                            OrderItemsList(items = displayOrder.items)
                        } else {
                            Text(
                                text = stringResource(R.string.no_product_info),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        
                        // 显示价格明细
                        Text(
                            text = stringResource(R.string.price_details),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                        
                                                OrderDetailRow(                            label = stringResource(R.string.subtotal),                            value = "$currencySymbol${displayOrder.subtotal}",
                            icon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = stringResource(R.string.subtotal),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                        
                        if (displayOrder.discountTotal.isNotEmpty() && displayOrder.discountTotal != "0.00") {
                            OrderDetailRow(
                                label = stringResource(R.string.discount),
                                value = "- ¥${displayOrder.discountTotal}",
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.AttachMoney,
                                        contentDescription = stringResource(R.string.discount),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                        
                        // 是否是外卖订单
                        val isDelivery = displayOrder.woofoodInfo?.isDelivery ?: false
                        
                        // 记录所有费用行，方便调试
                        Log.d("OrderDetailDialog", "【UI查找前】订单#${displayOrder.number} 费用行数量: ${displayOrder.feeLines.size}")
                        displayOrder.feeLines.forEach { feeLine ->
                            Log.d("OrderDetailDialog", "【UI查找前】费用行: '${feeLine.name}' = ${feeLine.total}")
                        }
                        
                        // 更全面的配送费名称匹配
                        val deliveryFeeLine = displayOrder.feeLines.find { 
                            it.name.equals("Shipping fee", ignoreCase = true) ||
                            it.name.equals("shipping fee", ignoreCase = true) ||
                            it.name.equals("SHIPPING FEE", ignoreCase = true) ||
                            it.name.contains("配送费", ignoreCase = true) || 
                            it.name.contains("外卖费", ignoreCase = true) ||
                            it.name.contains("delivery", ignoreCase = true) ||
                            it.name.contains("shipping", ignoreCase = true) ||
                            it.name.contains("运费", ignoreCase = true)
                        }
                        
                        // 更全面的小费名称匹配
                        val tipLine = displayOrder.feeLines.find { 
                            it.name.equals("Show Your Appreciation", ignoreCase = true) ||
                            it.name.contains("小费", ignoreCase = true) || 
                            it.name.contains("tip", ignoreCase = true) ||
                            it.name.contains("gratuity", ignoreCase = true) ||
                            it.name.contains("appreciation", ignoreCase = true)
                        }
                        
                        // 记录匹配结果
                        Log.d("OrderDetailDialog", "【UI匹配结果】配送费行: ${deliveryFeeLine?.name ?: "未找到"}, 金额: ${deliveryFeeLine?.total ?: "0.00"}")
                        Log.d("OrderDetailDialog", "【UI匹配结果】小费行: ${tipLine?.name ?: "未找到"}, 金额: ${tipLine?.total ?: "0.00"}")
                        
                        // 获取配送费和小费的值
                        var deliveryFee = "0.00"
                        var tip = "0.00"
                        
                        // 首先尝试从feeLines直接获取配送费
                        if (deliveryFeeLine != null) {
                            deliveryFee = deliveryFeeLine.total
                            Log.d("OrderDetailDialog", "【UI】从feeLines获取配送费: $deliveryFee (${deliveryFeeLine.name})")
                        }
                        
                        // 首先尝试从feeLines直接获取小费
                        if (tipLine != null) {
                            tip = tipLine.total
                            Log.d("OrderDetailDialog", "【UI】从feeLines获取小费: $tip (${tipLine.name})")
                        }
                        
                        // 如果feeLines中没有找到配送费，但woofoodInfo中有，使用woofoodInfo中的值
                        if (deliveryFee == "0.00" && displayOrder.woofoodInfo?.deliveryFee != null && displayOrder.woofoodInfo.deliveryFee != "0.00") {
                            deliveryFee = displayOrder.woofoodInfo.deliveryFee
                            Log.d("OrderDetailDialog", "【UI】从woofoodInfo获取配送费: $deliveryFee")
                        }
                        
                        // 如果feeLines中没有找到小费，但woofoodInfo中有，使用woofoodInfo中的值
                        if (tip == "0.00" && displayOrder.woofoodInfo?.tip != null && displayOrder.woofoodInfo.tip != "0.00") {
                            tip = displayOrder.woofoodInfo.tip
                            Log.d("OrderDetailDialog", "【UI】从woofoodInfo获取小费: $tip")
                        }
                        
                        // 添加详细日志，显示订单详情对话框中的数据状态
                        Log.d("OrderDetailDialog", "【UI最终数据】订单#${displayOrder.number} - 是否外卖: $isDelivery, 最终配送费: $deliveryFee, 最终小费: $tip")
                        Log.d("OrderDetailDialog", "【UI数据源】woofoodInfo值 - 配送费: ${displayOrder.woofoodInfo?.deliveryFee}, 小费: ${displayOrder.woofoodInfo?.tip}")
                        
                        // 如果是外卖订单，始终显示配送费行（即使金额为0）
                        if (isDelivery) {
                                                        OrderDetailRow(                                label = stringResource(R.string.delivery_fee),                                value = "$currencySymbol$deliveryFee",
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.LocalShipping,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                        
                        // 始终显示小费行（只要不是0.00或空字符串）
                        if (tip != "0.00" && tip.isNotEmpty()) {
                                                        OrderDetailRow(                                label = stringResource(R.string.tip_amount),                                value = "$currencySymbol$tip",
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.AttachMoney,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                        
                        // 显示其他额外费用（不是配送费或小费的其他费用）
                        displayOrder.feeLines.forEach { feeLine ->
                            // 排除已经显示过的配送费和小费
                            val isDeliveryFee = deliveryFeeLine?.id == feeLine.id || 
                                              feeLine.name.equals("Shipping fee", ignoreCase = true) ||
                                              feeLine.name.equals("shipping fee", ignoreCase = true) ||
                                              feeLine.name.equals("SHIPPING FEE", ignoreCase = true) ||
                                              feeLine.name.contains("配送费", ignoreCase = true) || 
                                              feeLine.name.contains("外卖费", ignoreCase = true) ||
                                              feeLine.name.contains("shipping", ignoreCase = true) || 
                                              feeLine.name.contains("delivery", ignoreCase = true)
                            
                            val isTip = tipLine?.id == feeLine.id ||
                                      feeLine.name.equals("Show Your Appreciation", ignoreCase = true) ||
                                      feeLine.name.contains("小费", ignoreCase = true) || 
                                      feeLine.name.contains("tip", ignoreCase = true) || 
                                      feeLine.name.contains("gratuity", ignoreCase = true) ||
                                      feeLine.name.contains("appreciation", ignoreCase = true)
                            
                            if (!isDeliveryFee && !isTip) {
                                                            OrderDetailRow(                                label = feeLine.name,                                value = "$currencySymbol${feeLine.total}",
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.AttachMoney,
                                            contentDescription = feeLine.name,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                        }
                        
                        // 记录一下所有税费行，方便调试
                        if (displayOrder.taxLines.isNotEmpty()) {
                            Log.d("OrderDetailDialog", "税费行数量: ${displayOrder.taxLines.size}")
                            displayOrder.taxLines.forEach { taxLine ->
                                Log.d("OrderDetailDialog", "税费: ${taxLine.label} (${taxLine.ratePercent}%) = ¥${taxLine.taxTotal}")
                            }
                        } else {
                            Log.d("OrderDetailDialog", "无税费行信息")
                        }
                        
                        // 遍历税费行，分别显示PST和GST
                        displayOrder.taxLines.forEach { taxLine ->
                            when {
                                taxLine.label.contains("PST", ignoreCase = true) -> {
                                                                        OrderDetailRow(                                        label = stringResource(R.string.tax_pst, taxLine.ratePercent.toString()),                                        value = "$currencySymbol${taxLine.taxTotal}",
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.AccountBalance,
                                                contentDescription = "PST税",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                }
                                taxLine.label.contains("GST", ignoreCase = true) -> {
                                                                        OrderDetailRow(                                        label = stringResource(R.string.tax_gst, taxLine.ratePercent.toString()),                                        value = "$currencySymbol${taxLine.taxTotal}",
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.AccountBalance,
                                                contentDescription = "GST税",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                }
                                else -> {
                                    // 其他税费显示
                                                                        OrderDetailRow(                                        label = "${taxLine.label} (${taxLine.ratePercent}%)",                                        value = "$currencySymbol${taxLine.taxTotal}",
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.AccountBalance,
                                                contentDescription = stringResource(R.string.tax),
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        
                        // 如果没有具体税费行，但有总税费，显示总税费
                        if (displayOrder.taxLines.isEmpty() && displayOrder.totalTax != "0.00" && displayOrder.totalTax.isNotEmpty()) {
                                                        OrderDetailRow(                                label = stringResource(R.string.tax),                                value = "$currencySymbol${displayOrder.totalTax}",
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalance,
                                        contentDescription = stringResource(R.string.tax),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        // 显示总计
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.total),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Text(
                                text = "¥${displayOrder.total}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // 显示订单状态
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.status) + ": ",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            
                            val statusText = when(displayOrder.status) {
                                "processing" -> stringResource(R.string.order_status_processing)
                                "pending" -> stringResource(R.string.order_status_pending)
                                "on-hold" -> stringResource(R.string.order_status_on_hold)
                                "completed" -> stringResource(R.string.order_status_completed)
                                "cancelled" -> stringResource(R.string.order_status_cancelled)
                                "refunded" -> stringResource(R.string.order_status_refunded)
                                "failed" -> stringResource(R.string.order_status_failed)
                                else -> displayOrder.status
                            }
                            
                            val statusColor = when(displayOrder.status) {
                                "completed" -> MaterialTheme.colorScheme.primary
                                "processing" -> Color(0xFF2196F3) // 蓝色
                                "pending" -> Color(0xFFFFA000) // 橙色
                                "on-hold" -> Color(0xFF9C27B0) // 紫色
                                "cancelled", "failed" -> MaterialTheme.colorScheme.error
                                "refunded" -> Color(0xFF4CAF50) // 绿色
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = statusColor.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .clickable(
                                        enabled = licenseInfo?.status == LicenseStatus.VALID || licenseInfo?.status == LicenseStatus.TRIAL
                                    ) { 
                                        if (licenseInfo?.status == LicenseStatus.VALID || licenseInfo?.status == LicenseStatus.TRIAL) {
                                            showStatusOptions = true
                                        }
                                    }
                            ) {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (licenseInfo?.status == LicenseStatus.VALID || licenseInfo?.status == LicenseStatus.TRIAL) 
                                        statusColor 
                                    else 
                                        statusColor.copy(alpha = 0.5f)
                                )
                            }
                        }
                        
                        // 额外添加一些底部空间，确保内容不被按钮遮挡
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // 如果证书无效，显示模糊层和提示
                    if (licenseInfo?.status != LicenseStatus.VALID && licenseInfo?.status != LicenseStatus.TRIAL) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.9f),
                                            Color.White.copy(alpha = 0.95f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.license_required),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.license_required_message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                // 获取上下文，放在Composable函数顶层
                                val context = LocalContext.current
                                
                                // 使用Row放置两个按钮
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                                ) {
                                    // 激活许可证按钮
                                    Button(
                                        onClick = {
                                            onDismiss() // 先关闭当前对话框
                                            // 导航到许可证设置页面
                                            viewModel.navigateToLicenseSettings()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VpnKey,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(text = stringResource(R.string.activate_license))
                                    }
                                    
                                    // 关闭按钮
                                    OutlinedButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(text = stringResource(R.string.close))
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 按钮操作区（固定在底部不滚动）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    // 主操作：打印
                    Button(
                        onClick = { showTemplateOptions = true },
                        modifier = Modifier.weight(1f),
                        enabled = licenseInfo?.status == LicenseStatus.VALID || licenseInfo?.status == LicenseStatus.TRIAL
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (displayOrder.isPrinted) stringResource(R.string.reprint) else stringResource(R.string.print_order))
                    }
                    // 次操作：更改订单状态
                    OutlinedButton(
                        onClick = { showStatusOptions = true },
                        modifier = Modifier.weight(1f),
                        enabled = licenseInfo?.status == LicenseStatus.VALID || licenseInfo?.status == LicenseStatus.TRIAL
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.change_order_status))
                    }
                    // 关闭按钮
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
    
    // 显示状态选择对话框（仅在证书有效时）
    if (showStatusOptions && (licenseInfo?.status == LicenseStatus.VALID || licenseInfo?.status == LicenseStatus.TRIAL)) {
        StatusChangeDialog(
            currentStatus = displayOrder.status,
            onDismiss = { showStatusOptions = false },
            onStatusSelected = { newStatus ->
                onStatusChange(displayOrder.id, newStatus)
                showStatusOptions = false
            }
        )
    }
    
    // 添加模板选择对话框（仅在证书有效时）
    if (showTemplateOptions && (licenseInfo?.status == LicenseStatus.VALID || licenseInfo?.status == LicenseStatus.TRIAL)) {
        TemplateSelectorDialog(
            onDismiss = { showTemplateOptions = false },
            onTemplateSelected = { templateType ->
                // 记录打印前的状态
                Log.d("OrderDetailDialog", "【打印状态修复】准备打印订单: ${displayOrder.id}, 当前打印状态: ${displayOrder.isPrinted}")
                
                // 使用选定的模板打印
                viewModel.printOrder(displayOrder.id, templateType)
                
                // 记录打印后的状态
                Log.d("OrderDetailDialog", "【打印状态修复】已提交打印请求，等待状态变更")
                
                // 关闭模板选择对话框
                showTemplateOptions = false
            }
        )
    }
}

/**
 * 订单详情行组件
 */
@Composable
fun OrderDetailRow(
    label: String,
    value: String,
    icon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(80.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(4.dp))
        }
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * 状态更改对话框
 */
@Composable
fun StatusChangeDialog(
    currentStatus: String,
    onDismiss: () -> Unit,
    onStatusSelected: (String) -> Unit
) {
    val statusOptions = listOf(
        "processing" to stringResource(R.string.order_status_processing),
        "pending" to stringResource(R.string.order_status_pending),
        "on-hold" to stringResource(R.string.order_status_on_hold),
        "completed" to stringResource(R.string.order_status_completed),
        "cancelled" to stringResource(R.string.order_status_cancelled),
        "refunded" to stringResource(R.string.order_status_refunded),
        "failed" to stringResource(R.string.order_status_failed)
    )
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.change_order_status),
                    style = MaterialTheme.typography.titleLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn {
                    items(statusOptions) { (status, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStatusSelected(status) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isSelected = status == currentStatus
                            val textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor
                            )
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

/**
 * 打印模板选择对话框
 */
@Composable
fun TemplateSelectorDialog(
    onDismiss: () -> Unit,
    onTemplateSelected: (TemplateType) -> Unit
) {
    val templateOptions = listOf(
        Pair(TemplateType.FULL_DETAILS, stringResource(R.string.full_details_template)),
        Pair(TemplateType.DELIVERY, stringResource(R.string.delivery_template)),
        Pair(TemplateType.KITCHEN, stringResource(R.string.kitchen_template))
    )
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.select_print_template),
                    style = MaterialTheme.typography.titleLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                templateOptions.forEach { (type, description) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onTemplateSelected(type)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when(type) {
                                TemplateType.FULL_DETAILS -> Icons.AutoMirrored.Filled.Article
                                TemplateType.DELIVERY -> Icons.Default.LocalShipping
                                TemplateType.KITCHEN -> Icons.Default.Restaurant
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    
                    if (type != TemplateType.KITCHEN) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

/**
 * 订单商品列表
 */
@Composable
fun OrderItemsList(items: List<OrderItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        items.forEach { item ->
            OrderItemRow(item = item)
            if (items.indexOf(item) < items.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            }
        }
    }
}

/**
 * 单个商品行组件
 */
@Composable
fun OrderItemRow(item: OrderItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 如果有商品图片，显示图片
        if (item.image.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.image)
                    .crossfade(true)
                    .build(),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
        Spacer(modifier = Modifier.width(8.dp))  // 增加间距
        
        // 商品信息（名称、选项、数量）
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            
            // 显示商品选项
            if (item.options.isNotEmpty()) {
                Text(
                    text = item.options.joinToString(", ") { option -> "${option.name}: ${option.value}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        
        // 数量
        Text(
            text = "x${item.quantity}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        
        // 价格
        Text(
            text = "¥${item.total}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
} 