package com.example.wooauto.presentation.screens.orders

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
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.wooauto.BuildConfig
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import com.example.wooauto.licensing.LicenseStatus
import com.example.wooauto.licensing.EligibilityStatus
import com.example.wooauto.presentation.screens.templatePreview.TemplateConfigViewModel

/**
 * 订单详情对话框
 */
// 模板模式：AUTO 按订单状态自动判断；NEW/PROCESSING 为显式模式
enum class DetailMode { AUTO, NEW, PROCESSING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailDialog(
    order: Order,
    onDismiss: () -> Unit,
    mode: DetailMode = DetailMode.AUTO,
    onStatusChange: (Long, String) -> Unit,
    onMarkAsPrinted: (Long) -> Unit,
    onMarkAsRead: ((Long) -> Unit)? = null
) {
    val viewModel: OrdersViewModel = hiltViewModel()
    // 临时获取仓库以便调用原始元数据接口
    val repo = remember { viewModel.orderRepository }
    remember { viewModel.licenseManager }
    // val licenseInfo by viewModel.licenseManager.licenseInfo.observeAsState()
    val eligibilityInfo by viewModel.licenseManager.eligibilityInfo.observeAsState()
    val hasEligibility = eligibilityInfo?.status == EligibilityStatus.ELIGIBLE
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    
    var showStatusOptions by remember { mutableStateOf(false) }
    var showTemplateOptions by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    
    // 观察当前选中的订单，以便实时更新UI
    val currentOrder by viewModel.selectedOrder.collectAsState()
    
    // 使用当前的订单信息（如果有更新）或者传入的订单
    val displayOrder = currentOrder ?: order
    
    // 创建一个包装的onDismiss函数：不再自动标记为已读
    val wrappedOnDismiss = {
        onDismiss()
    }
    
    // 记录订单信息用于调试
//    Log.d("OrderDetailDialog", "【打印状态修复】初始化订单详情对话框:")
//    Log.d("OrderDetailDialog", "【打印状态修复】传入的order: ID=${order.id}, 打印状态=${order.isPrinted}")
//    Log.d("OrderDetailDialog", "【打印状态修复】currentOrder: ID=${currentOrder?.id}, 打印状态=${currentOrder?.isPrinted}")
//    Log.d("OrderDetailDialog", "【打印状态修复】最终使用的displayOrder: ID=${displayOrder.id}, 打印状态=${displayOrder.isPrinted}")
    
    // 定义打印状态相关变量
    val printStatusText = if (displayOrder.isPrinted) stringResource(R.string.printed_yes) else stringResource(R.string.printed_no)
    val printStatusColor = if (displayOrder.isPrinted) Color(0xFF4CAF50) else Color(0xFFE53935)
    
    // Debug log removed to reduce noise

    // 替换 Dialog 为全屏 Surface 覆盖层，以确保优先级低于 NewOrderPopup (zIndex 999) 和 GlobalErrorDialog (Window)
    // 并解决全屏显示时的边距问题
    androidx.activity.compose.BackHandler {
        wrappedOnDismiss()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(50f) // 确保在普通内容之上，但低于 NewOrderPopup
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { wrappedOnDismiss() } // 点击遮罩层关闭
    ) {
        // 使用 Surface 并填满整个屏幕大小，移除 Card 的圆角和外边距
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { /* 拦截点击事件，防止穿透到遮罩层关闭 */ },
            color = MaterialTheme.colorScheme.surface
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
                        IconButton(onClick = wrappedOnDismiss) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.close)
                            )
                        }
                    },
                    actions = {
                        // 顶部右侧状态标签（紧急/今日稍后/预订单），展示相对日期字符串
                        val wooFoodInfo = displayOrder.woofoodInfo
                        val timeText = wooFoodInfo?.deliveryTime
                        val scheduledDateStr = extractScheduledDateFromNotes(displayOrder.notes)
                        // 本地化字符串资源
                        val statusStrings = StatusStrings(
                            urgentTitle = stringResource(R.string.status_urgent_title),
                            todayLaterTitle = stringResource(R.string.status_today_later_title),
                            preOrderTitle = stringResource(R.string.status_preorder_title),
                            today = stringResource(R.string.relative_today),
                            tomorrow = stringResource(R.string.relative_tomorrow),
                            dayAfterTomorrow = stringResource(R.string.relative_day_after_tomorrow),
                            daysLaterFormat = stringResource(R.string.relative_days_later),
                            dateWithRelativeFormat = stringResource(R.string.status_date_with_relative)
                        )
                        val status = remember(displayOrder.id, scheduledDateStr, timeText, statusStrings) {
                            buildOrderStatusLabel(
                                createdAt = displayOrder.dateCreated,
                                scheduledDateStr = scheduledDateStr,
                                deliveryTimeText = timeText,
                                strings = statusStrings
                            )
                        }
                        // 调试日志已移除
                        if (status != null) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .background(color = status.backgroundColor, shape = RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = status.foregroundColor
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = status.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = status.foregroundColor
                                    )
                                }
                            }
                        }
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
                                    enabled = hasEligibility
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
                        
                        // 显示订单备注信息（过滤掉我们为解析而注入的元数据行）
                        run {
                            val noteForDisplay = cleanNotesForDisplay(displayOrder.notes)
                            if (noteForDisplay.isNotEmpty()) {
                                OrderDetailRow(
                                    label = stringResource(R.string.order_note),
                                    value = noteForDisplay,
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
                        }
                        
                        // 显示WooFood信息（如果有）
                        displayOrder.woofoodInfo?.let { wooFoodInfo ->
                            // 根据新的 orderMethod 区分颜色和文案
                            val orderMethodColor = when(wooFoodInfo.orderMethod) {
                                "delivery" -> Color(0xFF2196F3) // 蓝色用于外卖
                                "dinein" -> Color(0xFFFF9800)   // 橙色用于堂食
                                else -> Color(0xFF4CAF50)       // 绿色用于自取
                            }
                            
                            val orderMethodText = when(wooFoodInfo.orderMethod) {
                                "delivery" -> stringResource(R.string.delivery_order)
                                "dinein" -> "Dine-in Order" // TODO: Add resource string
                                else -> stringResource(R.string.pickup_order)
                            }
                            
                            val orderMethodIcon = when(wooFoodInfo.orderMethod) {
                                "delivery" -> Icons.Default.LocalShipping
                                "dinein" -> Icons.Default.RestaurantMenu
                                else -> Icons.Default.ShoppingBag
                            }
                            
                            // 临时调试：仅针对#1700显示完整元数据与备注预览
                            // ... (debug code omitted)

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
                                            imageVector = orderMethodIcon,
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
                            
                            // 内容区分显示逻辑
                            // 1. Delivery: 显示配送地址
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
                            } 
                            // 2. Dine-in: 不显示地址，可能显示桌号（如果有提取到的话）
                            else if (wooFoodInfo.orderMethod == "dinein") {
                                // 目前暂时没有专门的桌号字段，如果 notes 里有可以提取
                                // 暂无需显示额外地址信息
                            }
                            // 3. Pickup: 显示店铺地址或自取提示
                            else {
                                OrderDetailRow(
                                    label = stringResource(R.string.pickup_location),
                                    value = stringResource(R.string.in_store_pickup), // 简化显示
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Store,
                                            contentDescription = stringResource(R.string.pickup_location),
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                )
                            }
                            
                            // 统一显示预计时间
                            // 逻辑：Delivery 和 Pickup 都显示时间，Dine-in 暂时隐藏（通常堂食是即时的）
                            if (wooFoodInfo.orderMethod != "dinein") {
                                wooFoodInfo.deliveryTime?.let { time ->
                                    val scheduledDateStr = extractScheduledDateFromNotes(displayOrder.notes)
                                    val dateOnly = parseDateOnly(scheduledDateStr) ?: stripTime(displayOrder.dateCreated)
                                    val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(dateOnly)
                                    val strings = StatusStrings(
                                        urgentTitle = stringResource(R.string.status_urgent_title),
                                        todayLaterTitle = stringResource(R.string.status_today_later_title),
                                        preOrderTitle = stringResource(R.string.status_preorder_title),
                                        today = stringResource(R.string.relative_today),
                                        tomorrow = stringResource(R.string.relative_tomorrow),
                                        dayAfterTomorrow = stringResource(R.string.relative_day_after_tomorrow),
                                        daysLaterFormat = stringResource(R.string.relative_days_later),
                                        dateWithRelativeFormat = stringResource(R.string.status_date_with_relative)
                                    )
                                    val relative = computeRelativeDayLabel(
                                        createdAt = displayOrder.dateCreated,
                                        scheduled = dateOnly,
                                        strings = strings
                                    )
                                    val datePortion = if (relative != null) String.format(strings.dateWithRelativeFormat, dateStr, relative) else dateStr
                                    val dateTimeText = "$datePortion $time"
                                    OrderDetailRow(
                                        label = if (wooFoodInfo.isDelivery) stringResource(R.string.delivery_time) else stringResource(R.string.pickup_time),
                                        value = dateTimeText,
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
                        }
                        
                        // 显示订单商品列表
                        Text(
                            text = stringResource(R.string.product_list),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                        
                        if (displayOrder.items.isNotEmpty()) {
                            OrderItemsList(items = displayOrder.items, currencySymbol = currencySymbol)
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
                        
                        // Debug logs removed
                        
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
                            val name = it.name.lowercase()
                            name.contains("show your appreciation") ||
                            name.contains("appreciation") ||
                            name.contains("tip") ||
                            name.contains("gratuity") ||
                            name.contains("小费") ||
                            name.contains("感谢")
                        }
                        
                        // Debug logs removed
                        
                        // 获取配送费和小费的值
                        var deliveryFee = "0.00"
                        var tip = "0.00"
                        
                        // 首先尝试从feeLines直接获取配送费
                        if (deliveryFeeLine != null) {
                            deliveryFee = deliveryFeeLine.total
                            // Debug log removed
                        }
                        
                        // 首先尝试从feeLines直接获取小费
                        if (tipLine != null) {
                            tip = tipLine.total
                            // Debug log removed
                        }
                        
                        // 如果feeLines中没有找到配送费，但woofoodInfo中有，使用woofoodInfo中的值
                        if (deliveryFee == "0.00" && displayOrder.woofoodInfo?.deliveryFee != null && displayOrder.woofoodInfo.deliveryFee != "0.00") {
                            deliveryFee = displayOrder.woofoodInfo.deliveryFee
                            // Debug log removed
                        }
                        
                        // 如果feeLines中没有找到小费，但woofoodInfo中有，使用woofoodInfo中的值
                        if (tip == "0.00" && displayOrder.woofoodInfo?.tip != null && displayOrder.woofoodInfo.tip != "0.00") {
                            tip = displayOrder.woofoodInfo.tip
                            // Debug log removed
                        }
                        
                        // Debug logs removed
                        
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
                                      feeLine.name.lowercase().let { name ->
                                          name.contains("show your appreciation") ||
                                          name.contains("appreciation") ||
                                          name.contains("tip") ||
                                          name.contains("gratuity") ||
                                          name.contains("小费") ||
                                          name.contains("感谢")
                                      }
                            
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
                        
                        // Debug logs removed
                        
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
                            
                                                        Text(                                text = "$currencySymbol${displayOrder.total}",                                style = MaterialTheme.typography.titleMedium,                                fontWeight = FontWeight.Bold,                                color = MaterialTheme.colorScheme.primary                            )
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
                                        enabled = hasEligibility
                                    ) { 
                                        if (hasEligibility) {
                                            showStatusOptions = true
                                        }
                                    }
                            ) {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (hasEligibility) 
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
                    if (!hasEligibility) {
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
                                
                                // 获取上下文（已不直接使用）
                                // val context = LocalContext.current
                                
                                // 使用Row放置两个按钮
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                                ) {
                                    // 激活许可证按钮
                                    Button(
                                        onClick = {
                                            wrappedOnDismiss() // 先关闭当前对话框
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
                                        onClick = wrappedOnDismiss,
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
                    // 主操作：打印（最左）
                    Button(
                        onClick = { showTemplateOptions = true },
                        modifier = Modifier.weight(1f),
                        enabled = hasEligibility
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (displayOrder.isPrinted) stringResource(R.string.reprint) else stringResource(R.string.print_order))
                    }
                    // 中间按钮：优先根据模板模式渲染，其次回退到自动判断
                    when (mode) {
                        DetailMode.NEW -> {
                            OutlinedButton(
                                onClick = {
                                    onMarkAsRead?.invoke(displayOrder.id)
                                    if (displayOrder.status != "processing") {
                                        onStatusChange(displayOrder.id, "processing")
                                    }
                                    wrappedOnDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                enabled = hasEligibility
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.start_processing))
                            }
                        }
                        DetailMode.PROCESSING -> {
                            OutlinedButton(
                                onClick = {
                                    onStatusChange(displayOrder.id, "completed")
                                    wrappedOnDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                enabled = hasEligibility
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.mark_as_completed))
                            }
                        }
                        DetailMode.AUTO -> {
                            if (displayOrder.status == "processing") {
                                OutlinedButton(
                                    onClick = { onStatusChange(displayOrder.id, "completed") },
                                    modifier = Modifier.weight(1f),
                                    enabled = hasEligibility
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.mark_as_completed))
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { showStatusOptions = true },
                                    modifier = Modifier.weight(1f),
                                    enabled = hasEligibility
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.change_order_status))
                                }
                            }
                        }
                    }
                    // 最右：关闭
                    OutlinedButton(
                        onClick = wrappedOnDismiss,
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
    if (showStatusOptions && (hasEligibility)) {
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
    if (showTemplateOptions && (hasEligibility)) {
        TemplateSelectorDialog(
            onDismiss = { showTemplateOptions = false },
            onConfirm = { selectedCopies ->
                // 多模板打印（手动）：按用户在弹窗中设置的份数逐一打印
                viewModel.printOrderWithTemplates(displayOrder.id, selectedCopies)
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
    onConfirm: (Map<String, Int>) -> Unit
) {
    val templateConfigViewModel: TemplateConfigViewModel = hiltViewModel()
    val allConfigs by templateConfigViewModel.allConfigs.collectAsState()
    val isLoading by templateConfigViewModel.isLoading.collectAsState()
    
    // 加载所有模板配置
    LaunchedEffect(Unit) {
        templateConfigViewModel.loadAllConfigs()
    }
    
    // 获取字符串资源
    val fullDetailsTemplate = stringResource(R.string.full_details_template)
    val deliveryTemplate = stringResource(R.string.delivery_template) 
    val kitchenTemplate = stringResource(R.string.kitchen_template)
    
    // 准备显示的模板选项（默认 + 自定义）
    val templateOptions = remember(allConfigs, fullDetailsTemplate, deliveryTemplate, kitchenTemplate) {
        val defaultTemplates = listOf(
            Triple("full_details", fullDetailsTemplate, Icons.AutoMirrored.Filled.Article),
            Triple("delivery", deliveryTemplate, Icons.Default.LocalShipping),
            Triple("kitchen", kitchenTemplate, Icons.Default.Restaurant)
        )
        
        val customTemplates = allConfigs
            .filter { it.templateId.startsWith("custom_") }
            .map { config ->
                Triple(config.templateId, config.templateName, Icons.Default.Description)
            }
        
        defaultTemplates + customTemplates
    }
    
    // 选择集合与份数字典（仅用于本次手动打印，不持久化）
    var selectedTemplateIds by remember { mutableStateOf(setOf<String>()) }
    var copyCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    
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
                
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    templateOptions.forEachIndexed { index, (templateId, description, icon) ->
                        val checked = selectedTemplateIds.contains(templateId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTemplateIds = selectedTemplateIds.toMutableSet().apply {
                                        if (contains(templateId)) remove(templateId) else add(templateId)
                                    }
                                    // 默认份数：首次选中时设为1
                                    if (!checked && !copyCounts.containsKey(templateId)) {
                                        copyCounts = copyCounts.toMutableMap().apply { put(templateId, 1) }
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selectedTemplateIds = selectedTemplateIds.toMutableSet().apply {
                                        if (checked) remove(templateId) else add(templateId)
                                    }
                                    if (!checked && !copyCounts.containsKey(templateId)) {
                                        copyCounts = copyCounts.toMutableMap().apply { put(templateId, 1) }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            // 份数步进器（禁用态时降低透明度）
                            val current = (copyCounts[templateId] ?: 1).coerceAtLeast(1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val next = (current - 1).coerceAtLeast(1)
                                        copyCounts = copyCounts.toMutableMap().apply { put(templateId, next) }
                                    },
                                    enabled = checked
                                ) { Icon(imageVector = Icons.Default.RemoveCircleOutline, contentDescription = null) }
                                Text(text = current.toString(), color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                IconButton(
                                    onClick = {
                                        val next = (current + 1).coerceAtMost(99)
                                        copyCounts = copyCounts.toMutableMap().apply { put(templateId, next) }
                                    },
                                    enabled = checked
                                ) { Icon(imageVector = Icons.Default.AddCircleOutline, contentDescription = null) }
                            }
                        }
                        if (index < templateOptions.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            // 仅提交被选中的模板及其份数，份数最少1
                            val payload = selectedTemplateIds.associateWith { id -> (copyCounts[id] ?: 1).coerceAtLeast(1) }
                            onConfirm(payload)
                        },
                        enabled = selectedTemplateIds.isNotEmpty()
                    ) {
                        // 复用“打印订单”文案
                        Text(text = stringResource(id = R.string.print_order))
                    }
                    Button(
                        onClick = onDismiss
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

/**
 * 订单商品列表
 */
@Composable
fun OrderItemsList(items: List<OrderItem>, currencySymbol: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        items.forEach { item ->
            OrderItemRow(item = item, currencySymbol = currencySymbol)
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
fun OrderItemRow(item: OrderItem, currencySymbol: String) {
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
            text = "$currencySymbol${item.total}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
} 

// 从订单备注中提取预定日期（例如 exwfood_date_deli），并格式化为 yyyy-MM-dd
private fun extractScheduledDateFromNotes(note: String): String? {
    if (note.isBlank()) return null
    return try {
        // 优先匹配 WooFood/ExWooFood 元数据键
        val keys = listOf(
            "exwfood_date_deli",
            "exwfood_date_pick",
            "woofood_date_deli",
            "woofood_date_pick",
            "delivery_date",
            "pickup_date",
            "Pickup Date",
            "Delivery Date"
        )
        // 调试日志已移除
        for (key in keys) {
            // 形如: key: October 29, 2025
            val pattern = ("(?im)^\\s*" + key + "\\s*[:：]\\s*(.+)$").toRegex()
            val match = pattern.find(note)
            if (match != null && match.groupValues.size > 1) {
                val raw = match.groupValues[1].trim()
                val formatted = tryFormatDateString(raw)
                return formatted ?: raw
            }
        }
        // 调试日志已移除
        null
    } catch (e: Exception) {
        null
    }
}

// 仅用于UI展示：移除我们注入到 notes 中的调试/解析元数据行
private fun cleanNotesForDisplay(notes: String): String {
    if (notes.isBlank()) return notes
    val lines = notes.lines()
    val filtered = lines.filterNot { line ->
        val trimmed = line.trim()
        trimmed.startsWith("--- 元数据") ||
        trimmed.startsWith("exwfood_order_method:") ||
        trimmed.startsWith("exwfood_date_deli:") ||
        trimmed.startsWith("exwfood_date_deli_unix:") ||
        trimmed.startsWith("exwfood_datetime_deli_unix:") ||
        trimmed.startsWith("exwfood_time_deli:") ||
        trimmed.startsWith("exwfood_timeslot:")
    }
    return filtered.joinToString("\n").trim()
}
// 将多种常见输入日期格式转为 yyyy-MM-dd
private fun tryFormatDateString(raw: String): String? {
    val inputs = listOf(
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "MM/dd/yyyy",
        "dd/MM/yyyy",
        "MM-dd-yyyy",
        "MMM d, yyyy",
        "MMM d yyyy",
        "MMMM d, yyyy"
    )
    inputs.forEach { pattern ->
        try {
            val df = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
            df.isLenient = true
            val date = df.parse(raw)
            if (date != null) {
                val out = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                return out.format(date)
            }
        } catch (_: Exception) { }
    }
    return null
}

// 顶部状态标签的数据载体
private data class OrderStatusLabel(
    val text: String,
    val backgroundColor: Color,
    val foregroundColor: Color
)

private data class StatusStrings(
    val urgentTitle: String,
    val todayLaterTitle: String,
    val preOrderTitle: String,
    val today: String,
    val tomorrow: String,
    val dayAfterTomorrow: String,
    val daysLaterFormat: String, // e.g. "%1$d days later"
    val dateWithRelativeFormat: String // e.g. "%1$s（%2$s）"
)

// 构建顶部右侧状态标签：红/黄/绿 + 相对日期字符串
private fun buildOrderStatusLabel(
    createdAt: java.util.Date,
    scheduledDateStr: String?,
    deliveryTimeText: String?,
    strings: StatusStrings
): OrderStatusLabel? {
    // 解析计划日期（仅日期部分）
    val dateOnly = parseDateOnly(scheduledDateStr) ?: stripTime(createdAt)
    val startMillis = parseStartTimeMillis(dateOnly, deliveryTimeText) ?: dateOnly.time
    val diffMillis = startMillis - createdAt.time

    val relative = computeRelativeDayLabel(createdAt, dateOnly, strings)
    val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(dateOnly)
    val timeDisplay = deliveryTimeText?.trim().orEmpty()
    val datePortion = if (relative != null) String.format(strings.dateWithRelativeFormat, dateStr, relative) else dateStr

    // 颜色预设
    val red = Color(0xFFE53935)
    val yellow = Color(0xFFFFA000)
    val green = Color(0xFF4CAF50)
    val fgOnLight = Color(0xFF0F0F0F)

    // 分类
    val twoHoursMs = 2 * 60 * 60 * 1000L
    return when {
        diffMillis in Long.MIN_VALUE..(twoHoursMs - 1) -> {
            val text = buildString {
                append(strings.urgentTitle)
                append(" · ")
                append(datePortion)
                if (timeDisplay.isNotEmpty()) {
                    append(' ')
                    append(timeDisplay)
                }
            }
            OrderStatusLabel(text, red.copy(alpha = 0.12f), red)
        }
        isSameDay(createdAt, dateOnly) -> {
            val text = buildString {
                append(strings.todayLaterTitle)
                append(" · ")
                append(String.format(strings.dateWithRelativeFormat, dateStr, strings.today))
                if (timeDisplay.isNotEmpty()) {
                    append(' ')
                    append(timeDisplay)
                }
            }
            OrderStatusLabel(text, yellow.copy(alpha = 0.12f), yellow)
        }
        else -> {
            val text = buildString {
                append(strings.preOrderTitle)
                append(" · ")
                val rel = relative ?: ""
                append(String.format(strings.dateWithRelativeFormat, dateStr, rel))
                if (timeDisplay.isNotEmpty()) {
                    append(' ')
                    append(timeDisplay)
                }
            }
            OrderStatusLabel(text, green.copy(alpha = 0.12f), green)
        }
    }
}

// 解析仅日期（去除时分秒），支持 yyyy-MM-dd 等多种格式
private fun parseDateOnly(dateStr: String?): java.util.Date? {
    if (dateStr.isNullOrBlank()) return null
    val candidate = tryFormatDateString(dateStr) ?: dateStr
    val patterns = listOf("yyyy-MM-dd", "yyyy/MM/dd", "MM/dd/yyyy", "dd/MM/yyyy", "MM-dd-yyyy")
    patterns.forEach { p ->
        try {
            val df = java.text.SimpleDateFormat(p, java.util.Locale.getDefault())
            df.isLenient = true
            val d = df.parse(candidate)
            if (d != null) return stripTime(d)
        } catch (_: Exception) { }
    }
    return null
}

// 去除时间部分，只保留日期
private fun stripTime(date: java.util.Date): java.util.Date {
    val cal = java.util.Calendar.getInstance()
    cal.time = date
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.time
}

// 计算相对日期：今天/明天/后天/N天后；若为过去则返回""
private fun computeRelativeDayLabel(createdAt: java.util.Date, scheduled: java.util.Date, strings: StatusStrings): String? {
    val start = stripTime(createdAt)
    val end = stripTime(scheduled)
    val days = ((end.time - start.time) / (24 * 60 * 60 * 1000L)).toInt()
    return when (days) {
        0 -> strings.today
        1 -> strings.tomorrow
        2 -> strings.dayAfterTomorrow
        else -> if (days > 2) String.format(strings.daysLaterFormat, days) else null
    }
}

private fun isSameDay(a: java.util.Date, b: java.util.Date): Boolean {
    val ca = java.util.Calendar.getInstance().apply { time = a }
    val cb = java.util.Calendar.getInstance().apply { time = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
            ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}

// 从时间文案解析起始时间戳（取区间的开始），结合日期
private fun parseStartTimeMillis(dateOnly: java.util.Date, timeText: String?): Long? {
    if (timeText.isNullOrBlank()) return null
    val firstPart = timeText
        .split("-", "–", "~", "到", "至")
        .firstOrNull()
        ?.trim()
        ?: return null

    val datePart = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(dateOnly)
    val candidates = listOf(
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd H:mm",
        "yyyy-MM-dd hh:mm a",
        "yyyy-MM-dd h:mm a"
    )
    candidates.forEach { p ->
        try {
            val df = java.text.SimpleDateFormat(p, java.util.Locale.getDefault())
            df.isLenient = true
            val dt = df.parse("$datePart $firstPart")
            if (dt != null) return dt.time
        } catch (_: Exception) { }
    }
    return null
}
