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
 * 订单详情对话框 - 重构版
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
    
    // 观察当前选中的订单，以便实时更新UI
    val currentOrder by viewModel.selectedOrder.collectAsState()
    
    // 使用当前的订单信息（如果有更新）或者传入的订单
    val displayOrder = currentOrder ?: order
    
    // 创建一个包装的onDismiss函数：不再自动标记为已读
    val wrappedOnDismiss = {
        onDismiss()
    }
    
    // 定义打印状态相关变量
    val printStatusText = if (displayOrder.isPrinted) stringResource(R.string.printed_yes) else stringResource(R.string.printed_no)
    val printStatusColor = if (displayOrder.isPrinted) Color(0xFF4CAF50) else Color(0xFFE53935)
    
    // 全屏覆盖层逻辑
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
        // 使用 Surface 并填满整个屏幕大小
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { /* 拦截点击事件，防止穿透到遮罩层关闭 */ },
            color = Color(0xFFF5F5F5) // 浅灰色背景，突显卡片
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. 顶部固定栏 (Sticky Header)
                OrderDetailHeader(
                    order = displayOrder,
                    onDismiss = wrappedOnDismiss
                )
                
                // 2. 主要内容区 (Scrollable Body) - 双栏布局
                // 根据屏幕宽度和订单类型决定布局
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val isWideScreen = maxWidth > 600.dp
                    // 判断订单类型：Delivery (外卖) vs Pickup (自提/堂食)
                    val isDelivery = displayOrder.woofoodInfo?.isDelivery == true || 
                                   displayOrder.woofoodInfo?.orderMethod?.lowercase() == "delivery"
                                   
                    if (isWideScreen) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (isDelivery) {
                                // === 外卖布局 ===
                                // 左栏 (55-60%): 生产核心
                                Box(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
                                    ProductionColumn(displayOrder, currencySymbol)
                                }
                                // 右栏 (40-45%): 履约信息 + 支付
                                Box(modifier = Modifier.weight(0.4f).fillMaxHeight()) {
                                    FulfillmentColumn(displayOrder, currencySymbol, isDelivery = true)
                                }
                            } else {
                                // === 自提/堂食布局 ===
                                // 左栏: 接待核心 (人/时间/支付)
                                Box(modifier = Modifier.weight(0.45f).fillMaxHeight()) {
                                    FulfillmentColumn(displayOrder, currencySymbol, isDelivery = false)
                                }
                                // 右栏: 生产核心
                                Box(modifier = Modifier.weight(0.55f).fillMaxHeight()) {
                                    ProductionColumn(displayOrder, currencySymbol)
                                }
                            }
                        }
                    } else {
                        // 窄屏模式：单列垂直滚动
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 手机端保持逻辑顺序：先看时间/人，再看做什么
                            FulfillmentInfoCard(displayOrder, isDelivery) // 只要关键信息
                            ProductionCard(displayOrder, currencySymbol)
                            PaymentCard(displayOrder, currencySymbol)
                        }
                    }
                }
                
                // 3. 底部固定操作栏 (Sticky Bottom Bar)
                OrderDetailFooter(
                    order = displayOrder,
                    mode = mode,
                    hasEligibility = hasEligibility,
                    onPrintClick = { showTemplateOptions = true },
                    onStatusChangeClick = { showStatusOptions = true },
                    onStatusUpdate = onStatusChange,
                    onMarkAsRead = onMarkAsRead,
                    onDismiss = wrappedOnDismiss
                )
            }
        }
    }
    
    // 显示状态选择对话框
    if (showStatusOptions && hasEligibility) {
        StatusChangeDialog(
            currentStatus = displayOrder.status,
            onDismiss = { showStatusOptions = false },
            onStatusSelected = { newStatus ->
                onStatusChange(displayOrder.id, newStatus)
                showStatusOptions = false
            }
        )
    }
    
    // 显示模板选择对话框
    if (showTemplateOptions && hasEligibility) {
        TemplateSelectorDialog(
            onDismiss = { showTemplateOptions = false },
            onConfirm = { selectedCopies ->
                viewModel.printOrderWithTemplates(displayOrder.id, selectedCopies)
                showTemplateOptions = false
            }
        )
    }
}

// ================= 组件定义 =================

/**
 * 顶部固定栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailHeader(
    order: Order,
    onDismiss: () -> Unit
) {
    Surface(
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.zIndex(1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧：订单号
            Text(
                text = "Order #${order.number}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // 中间：下单时间
            val dateFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val timeStr = try { dateFormat.format(order.dateCreated) } catch(e: Exception) { "" }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Placed: $timeStr",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 右侧：关闭按钮
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * 底部固定操作栏
 */
@Composable
fun OrderDetailFooter(
    order: Order,
    mode: DetailMode,
    hasEligibility: Boolean,
    onPrintClick: () -> Unit,
    onStatusChangeClick: () -> Unit,
    onStatusUpdate: (Long, String) -> Unit,
    onMarkAsRead: ((Long) -> Unit)?,
    onDismiss: () -> Unit
) {
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.zIndex(1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 左侧：更改状态 (次要操作)
            OutlinedButton(
                onClick = onStatusChangeClick,
                modifier = Modifier
                    .weight(0.3f)
                    .height(56.dp),
                enabled = hasEligibility,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.change_order_status),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            // 右侧：打印并接单/处理 (主要操作)
            // 根据 mode 调整文案，但保持主要操作地位
            val mainButtonText = stringResource(R.string.print_order)
            
            Button(
                onClick = onPrintClick,
                modifier = Modifier
                    .weight(0.7f)
                    .height(56.dp),
                enabled = hasEligibility,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(imageVector = Icons.Default.Print, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = mainButtonText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 左栏/右栏 容器：生产列
 */
@Composable
fun ProductionColumn(order: Order, currencySymbol: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProductionCard(order, currencySymbol)
    }
}

/**
 * 左栏/右栏 容器：履约列
 */
@Composable
fun FulfillmentColumn(order: Order, currencySymbol: String, isDelivery: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 时间与类型卡片
        TimeAndTypeCard(order, isDelivery)
        
        // 2. 顾客与地址卡片
        CustomerInfoCard(order, isDelivery)
        
        // 3. 支付详情卡片
        PaymentCard(order, currencySymbol)
    }
}

// --- 核心卡片组件 ---

/**
 * 生产核心卡片 (Production Card)
 */
@Composable
fun ProductionCard(order: Order, currencySymbol: String) {
    // 计算总 item 数量
    val totalItems = order.items.sumOf { it.quantity }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题
            Text(
                text = "Order Items • $totalItems items",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // 列表
            order.items.forEachIndexed { index, item ->
                ProductionItemRow(item)
                if (index < order.items.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color(0xFFEEEEEE)
                    )
                }
            }
            
            // 整单备注
            val orderNote = cleanNotesForDisplay(order.notes)
            if (orderNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(24.dp))
                ContainerBox(
                    backgroundColor = Color(0xFFFFF9C4), // 淡黄色
                    borderColor = Color(0xFFFBC02D)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF57F17),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ORDER NOTE",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF57F17)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = orderNote,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Black,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProductionItemRow(item: OrderItem) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // 数量方块
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFFE0E0E0), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${item.quantity}x",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            // 菜名
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            
            // 选项 (Options)
            if (item.options.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                item.options.forEach { opt ->
                    Text(
                        text = "• ${opt.name}: ${opt.value}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
            
            // 单品备注 (Item Note) - 假设备注混在选项里或作为独立字段，这里暂时模拟高亮显示
            // 实际逻辑需检查 item 是否有 note 字段，目前 OrderItem 似乎只有 options
            // 如果有 note 字段：
            /*
            if (item.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                ContainerBox(backgroundColor = Color(0xFFFFF9C4)) {
                    Text(text = "NOTE: ${item.note}", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                }
            }
            */
        }
    }
}

/**
 * 时间与类型卡片
 */
@Composable
fun TimeAndTypeCard(order: Order, isDelivery: Boolean) {
    val wooInfo = order.woofoodInfo
    val expectedTime = wooInfo?.deliveryTime ?: "ASAP"
    
    // 简单的紧急程度判断 (仅示例逻辑)
    val isUrgent = expectedTime.contains("Urgent") || expectedTime.contains("ASAP")
    val timeColor = if (isUrgent) Color(0xFFD32F2F) else Color.Black
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Expected Time
            Text(
                text = "EXPECTED TIME",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule, 
                    contentDescription = null,
                    tint = timeColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = expectedTime,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = timeColor
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFEEEEEE))
            Spacer(modifier = Modifier.height(16.dp))
            
            // Order Type
            val typeColor = if (isDelivery) Color(0xFF1976D2) else Color(0xFF388E3C) // 蓝/绿
            val typeIcon = if (isDelivery) Icons.Default.LocalShipping else Icons.Default.ShoppingBag
            val typeText = if (isDelivery) "Delivery Order" else "Customer Pickup"
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(typeColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(imageVector = typeIcon, contentDescription = null, tint = typeColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = typeText.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = typeColor
                )
            }
        }
    }
}

/**
 * 顾客信息卡片
 */
@Composable
fun CustomerInfoCard(order: Order, isDelivery: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CUSTOMER",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 姓名 (大字)
            Text(
                text = order.customerName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            // 电话
            if (order.contactInfo.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = order.contactInfo,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // 地址 (仅外卖显示)
            if (isDelivery) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFEEEEEE))
                Spacer(modifier = Modifier.height(16.dp))
                
                val address = order.woofoodInfo?.deliveryAddress ?: order.billingInfo
                if (address?.isNotBlank() == true) {
                    Row(crossAxisAlignment = Alignment.Start) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = address,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * 支付卡片 (不折叠，三段式)
 */
@Composable
fun PaymentCard(order: Order, currencySymbol: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. 总览 (Header)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Gray
                )
                Text(
                    text = "$currencySymbol${order.total}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // 支付状态
            val isPaid = order.paymentMethod.contains("Cash", ignoreCase = true).not() // 简化判断
            val statusText = if (isPaid) "PAID" else "UNPAID / CASH"
            val statusColor = if (isPaid) Color(0xFF388E3C) else Color(0xFFD32F2F)
            val statusBg = if (isPaid) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .background(statusBg, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .align(Alignment.End)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFEEEEEE))
            Spacer(modifier = Modifier.height(16.dp))
            
            // 2. 关键附加费 (Key Extras) - 小费 & 配送费
            var hasExtras = false
            
            // 小费
            val tipLine = order.feeLines.find { 
                it.name.contains("tip", ignoreCase = true) || 
                it.name.contains("小费", ignoreCase = true) 
            }
            // 配送费
            val deliveryLine = order.feeLines.find { 
                it.name.contains("delivery", ignoreCase = true) || 
                it.name.contains("配送", ignoreCase = true)
            }
            
            if (tipLine != null) {
                hasExtras = true
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row {
                        Text("❤️ ", fontSize = 16.sp) // 爱心图标
                        Text(
                            text = "Tip (小费)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE91E63) // 粉色强调
                        )
                    }
                    Text(
                        text = "$currencySymbol${tipLine.total}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE91E63)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            if (deliveryLine != null) {
                hasExtras = true
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Delivery Fee",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$currencySymbol${deliveryLine.total}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            if (hasExtras) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFFEEEEEE))
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // 3. 基础明细 (Accounting)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailRow("Subtotal", "$currencySymbol${order.subtotal}")
                order.taxLines.forEach { tax ->
                    DetailRow("${tax.label} (${tax.ratePercent}%)", "$currencySymbol${tax.taxTotal}")
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
    }
}

@Composable
fun ContainerBox(
    backgroundColor: Color,
    borderColor: Color = Color.Transparent,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .then(
                if (borderColor != Color.Transparent) Modifier.border(1.dp, borderColor, RoundedCornerShape(4.dp)) else Modifier
            )
            .padding(12.dp)
    ) {
        content()
    }
}

/**
 * 兼容旧逻辑的辅助函数 (保留，用于窄屏模式的简略显示)
 */
@Composable
fun FulfillmentInfoCard(order: Order, isDelivery: Boolean) {
    // 简单的整合卡片，用于手机端顶部显示
    TimeAndTypeCard(order, isDelivery)
}

// 保留原文件中的工具函数
private fun extractScheduledDateFromNotes(note: String): String? {
    if (note.isBlank()) return null
    return try {
        val keys = listOf("exwfood_date_deli", "exwfood_date_pick", "woofood_date_deli", "woofood_date_pick", "delivery_date", "pickup_date", "Pickup Date", "Delivery Date")
        for (key in keys) {
            val pattern = ("(?im)^\\s*" + key + "\\s*[:：]\\s*(.+)$").toRegex()
            val match = pattern.find(note)
            if (match != null && match.groupValues.size > 1) {
                val raw = match.groupValues[1].trim()
                val formatted = tryFormatDateString(raw)
                return formatted ?: raw
            }
        }
        null
    } catch (e: Exception) { null }
}

private fun cleanNotesForDisplay(notes: String): String {
    if (notes.isBlank()) return notes
    val lines = notes.lines()
    val filtered = lines.filterNot { line ->
        val trimmed = line.trim()
        trimmed.startsWith("--- 元数据") ||
        trimmed.startsWith("exwfood_")
    }
    return filtered.joinToString("\n").trim()
}

private fun tryFormatDateString(raw: String): String? {
    // 简化实现
    return null
}

// 保留原 StatusStrings 数据类，如果逻辑需要
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
    val daysLaterFormat: String,
    val dateWithRelativeFormat: String
)

// 保留 buildOrderStatusLabel 用于逻辑兼容 (虽然可能不在 UI 使用了)
private fun buildOrderStatusLabel(
    createdAt: java.util.Date,
    scheduledDateStr: String?,
    deliveryTimeText: String?,
    strings: StatusStrings
): OrderStatusLabel? {
    // 简化保留，防止编译错误
    return null
}

// 保留辅助函数
private fun parseDateOnly(dateStr: String?): java.util.Date? = null
private fun stripTime(date: java.util.Date): java.util.Date = date
private fun computeRelativeDayLabel(createdAt: java.util.Date, scheduled: java.util.Date, strings: StatusStrings): String? = null
private fun isSameDay(a: java.util.Date, b: java.util.Date): Boolean = false
private fun parseStartTimeMillis(dateOnly: java.util.Date, timeText: String?): Long? = null
