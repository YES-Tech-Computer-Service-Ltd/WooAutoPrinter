package com.example.wooauto.presentation.screens.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wooauto.R
import com.example.wooauto.domain.models.ItemOption
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
import java.util.Locale

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
                    val isWideScreen = this.maxWidth > 600.dp
                    // 判断订单类型：Delivery (外卖) vs Pickup (自提/堂食)
                    val orderMethod = displayOrder.woofoodInfo?.orderMethod?.lowercase() ?: ""
                    val isDelivery = displayOrder.woofoodInfo?.isDelivery == true || orderMethod == "delivery"
                                   
                    if (isWideScreen) {
                        val productionWeight = 0.65f
                        val infoWeight = 1f - productionWeight
                        Row(
                modifier = Modifier
                    .fillMaxSize()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (isDelivery) {
                                // === 外卖布局 ===
                                // 左栏 (55-60%): 生产核心
                                Box(modifier = Modifier.weight(productionWeight).fillMaxHeight()) {
                                    ProductionColumn(displayOrder, currencySymbol)
                                }
                                // 右栏 (40-45%): 履约信息 + 支付
                                Box(modifier = Modifier.weight(infoWeight).fillMaxHeight()) {
                                    FulfillmentColumn(displayOrder, currencySymbol, isDelivery = true)
                        }
                            } else {
                                // === 自提/堂食布局 ===
                                // 左栏: 接待核心 (人/时间/支付)
                                Box(modifier = Modifier.weight(infoWeight).fillMaxHeight()) {
                                    FulfillmentColumn(displayOrder, currencySymbol, isDelivery = false)
                                }
                                // 右栏: 生产核心
                                Box(modifier = Modifier.weight(productionWeight).fillMaxHeight()) {
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
                wrappedOnDismiss()
            }
        )
    }
    
    // 显示模板选择对话框
    if (showTemplateOptions && hasEligibility) {
        TemplateSelectorDialog(
            onDismiss = { showTemplateOptions = false },
            onConfirm = { selectedCopies ->
                // 明确参数类型，解决重载歧义
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
            // 左侧：订单号 + 打印状态
            Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                text = stringResource(R.string.order_detail_header_title, order.number),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                Spacer(modifier = Modifier.width(12.dp))
                
                // 打印状态标签
                val isPrinted = order.isPrinted
                val printStatusText = if (isPrinted) stringResource(R.string.printed_yes) else stringResource(R.string.printed_no)
                val printStatusColor = if (isPrinted) Color(0xFF2E7D32) else Color(0xFFC62828) // 深绿/深红
                val printStatusBg = if (isPrinted) Color(0xFFE8F5E9) else Color(0xFFFFEBEE) // 淡绿/淡红
                
                Surface(
                    color = printStatusBg,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                            Text(
                                text = printStatusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = printStatusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // 中间：下单时间
            val locale = java.util.Locale.getDefault()
            val timeFormat = java.text.SimpleDateFormat("HH:mm", locale)
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", locale)
            val timeStr = try { timeFormat.format(order.dateCreated) } catch(e: Exception) { "" }
            val dateStr = try { dateFormat.format(order.dateCreated) } catch(e: Exception) { "" }
            val displayTime = timeStr.ifBlank { "--" }
            val displayDate = dateStr.ifBlank { "--" }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.order_detail_header_time, displayDate, displayTime),
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
                // 添加导航栏内边距，防止按钮被系统导航条遮挡
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val isProcessingStatus = order.status.equals("processing", ignoreCase = true)
            val effectiveMode = when {
                mode == DetailMode.NEW -> DetailMode.NEW
                mode == DetailMode.PROCESSING -> DetailMode.PROCESSING
                isProcessingStatus && order.isRead -> DetailMode.PROCESSING
                else -> DetailMode.AUTO
            }

            val isNewMode = effectiveMode == DetailMode.NEW
            val isProcessingMode = effectiveMode == DetailMode.PROCESSING
            val statusButtonText = when {
                isNewMode -> stringResource(R.string.start_processing)
                isProcessingMode -> stringResource(R.string.mark_completed)
                else -> stringResource(R.string.change_order_status)
            }
            
            Button(
                onClick = {
                    if (isNewMode) {
                        onMarkAsRead?.invoke(order.id)
                        onStatusUpdate(order.id, "processing")
                        onDismiss()
                    } else if (isProcessingMode) {
                        onStatusUpdate(order.id, "completed")
                        onDismiss()
                    } else {
                        onStatusChangeClick()
                    }
                },
                modifier = Modifier
                    .weight(0.7f)
                    .height(56.dp),
                enabled = hasEligibility,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = statusButtonText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // 右侧：打印订单 (次要操作)
            val printButtonText = stringResource(R.string.print_order)
            
            OutlinedButton(
                onClick = onPrintClick,
                modifier = Modifier
                    .weight(0.3f)
                    .height(56.dp),
                enabled = hasEligibility,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Print, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = printButtonText,
                    style = MaterialTheme.typography.titleMedium
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
        PaymentCard(order, currencySymbol)
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
                text = stringResource(R.string.production_card_title, totalItems),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // 列表
            order.items.forEachIndexed { index, item ->
                ProductionItemRow(item, currencySymbol)
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
                            text = stringResource(R.string.order_note_section_title),
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
fun ProductionItemRow(item: OrderItem, currencySymbol: String) {
    val formattedTotal = remember(item.total, item.price, item.quantity, currencySymbol) {
        val normalizedTotal = item.total.trim()
        val numericTotal = normalizedTotal.toDoubleOrNull()
            ?: item.price.trim().toDoubleOrNull()?.let { it * item.quantity }

        numericTotal?.let { totalValue ->
            val rounded = String.format(Locale.getDefault(), "%.2f", totalValue)
            "$currencySymbol$rounded"
        } ?: normalizedTotal.takeIf { it.isNotBlank() }
            ?.let { "$currencySymbol$it" }
        ?: "$currencySymbol--"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // 数量方块：科学防错设计
        // 1x (常规): 浅灰底黑字
        // >1x (例外): 黑底白字 (高对比度，防止少做)
        val isMultiple = item.quantity > 1
        val qtyBgColor = if (isMultiple) Color(0xFF212121) else Color(0xFFEEEEEE)
        val qtyTextColor = if (isMultiple) Color.White else Color.Black
        
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(qtyBgColor, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
                        Text(
                text = "${item.quantity}x",
                            style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = qtyTextColor
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
            
            if (item.options.isNotEmpty()) {
                val (instructionOptions, standardOptions) = remember(item.options) {
                    val instructionKeywords = listOf("instruction", "special", "备注", "note", "要求", "说明")
                    val instructions = mutableListOf<ItemOption>()
                    val standard = mutableListOf<ItemOption>()
                    item.options.forEach { option ->
                        val key = option.name.lowercase(Locale.ROOT)
                        if (instructionKeywords.any { key.contains(it) }) {
                            instructions.add(option)
                        } else {
                            standard.add(option)
                        }
                    }
                    instructions to standard
                }
                Spacer(modifier = Modifier.height(8.dp))
                ContainerBox(
                    backgroundColor = Color(0xFFFFFDE7), // 同系更浅色
                    borderColor = Color(0xFFFBC02D).copy(alpha = 0.4f)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.item_options_label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF795548)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        instructionOptions.forEachIndexed { index, opt ->
                            Text(
                                text = "${stringResource(R.string.item_special_instruction_label)}: ${opt.value}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = Color(0xFFD84315),
                                fontWeight = FontWeight.Medium
                            )
                            if (index < instructionOptions.size - 1) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }

                        if (instructionOptions.isNotEmpty() && standardOptions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        standardOptions.forEachIndexed { index, opt ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "• ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                Text(
                                    text = "${opt.name}: ${opt.value}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Black
                                )
                            }
                            if (index < standardOptions.size - 1) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formattedTotal,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 60.dp)
        )
    }
                        }
                        
/**
 * 时间与类型卡片
 */
@Composable
fun TimeAndTypeCard(order: Order, isDelivery: Boolean) {
    val wooInfo = order.woofoodInfo
    val asapLabel = stringResource(R.string.expected_time_asap)
    val deliveryDisplayInfo = remember(
        wooInfo?.deliveryDate,
        wooInfo?.deliveryTime,
        order.dateCreated.time
    ) {
        DeliveryDisplayFormatter.format(wooInfo, order.dateCreated, Locale.getDefault())
    }
    val expectedTime = deliveryDisplayInfo.timeLabel.takeIf { it.isNotBlank() } ?: asapLabel
    val dateHeadlineColor = when {
        !deliveryDisplayInfo.hasDate -> MaterialTheme.colorScheme.onSurfaceVariant
        deliveryDisplayInfo.isFutureOrToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    // 简单的紧急程度判断 (仅示例逻辑)
    val isUrgent = expectedTime.contains("Urgent", ignoreCase = true) ||
        expectedTime.contains("ASAP", ignoreCase = true) ||
        expectedTime.contains(asapLabel, ignoreCase = true)
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
                text = stringResource(R.string.expected_time_label),
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = dateHeadlineColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = deliveryDisplayInfo.headline,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = dateHeadlineColor
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
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
            val orderMethod = order.woofoodInfo?.orderMethod?.trim()?.lowercase(java.util.Locale.ROOT)
            val isDineIn = when {
                !order.woofoodInfo?.dineInPersonCount.isNullOrBlank() -> true
                orderMethod == "dinein" -> true
                orderMethod == "dine-in" -> true
                orderMethod == "dine_in" -> true
                orderMethod?.contains("堂食") == true -> true
                else -> false
            }
            val typeColor = when {
                isDineIn -> Color(0xFF6A1B9A)
                isDelivery -> Color(0xFF1976D2)
                else -> Color(0xFF388E3C)
            }
            val typeIcon = when {
                isDineIn -> Icons.Default.Restaurant
                isDelivery -> Icons.Default.LocalShipping
                else -> Icons.Default.ShoppingBag
            }
            val typeText = when {
                isDineIn -> stringResource(R.string.delivery_type_dine_in)
                isDelivery -> stringResource(R.string.delivery_type_delivery)
                else -> stringResource(R.string.delivery_type_pickup)
            }
            
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
                    text = typeText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = typeColor
                                        )
                                    }

            // Dine-in people count (only for dine-in orders)
            if (isDineIn) {
                val rawPeople = order.woofoodInfo?.dineInPersonCount?.trim().orEmpty()
                val displayPeople = rawPeople.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.not_provided_bilingual)

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.dine_in_people_value, displayPeople),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
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
                    text = stringResource(R.string.customer_info_section_title),
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
            
            // 地址 (仅外卖显示，默认折叠)
            if (isDelivery) {
                val address = order.woofoodInfo?.deliveryAddress ?: order.billingInfo
                if (address?.isNotBlank() == true) {
                    var showAddress by remember { mutableStateOf(false) }
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF9F9F9))
                            .clickable { showAddress = !showAddress }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    if (showAddress) R.string.hide_delivery_address else R.string.show_delivery_address
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFB71C1C),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (showAddress) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        }

                        if (showAddress) {
                            Spacer(modifier = Modifier.height(8.dp))
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
            // 支付状态
            val isPaid = order.paymentMethod.contains("Cash", ignoreCase = true).not() // 简化判断
            val statusText = if (isPaid) {
                stringResource(R.string.payment_status_paid)
            } else {
                stringResource(R.string.payment_status_cash_due)
            }
            val statusColor = if (isPaid) Color(0xFF388E3C) else Color(0xFFD32F2F)
            val statusBg = if (isPaid) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)

            // 1. 总览 (Header)
                        Row(
                modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.payment_total_label),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(statusBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
                            Text(
                    text = "$currencySymbol${order.total}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
                        Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFEEEEEE))
            Spacer(modifier = Modifier.height(16.dp))
            
            // 2. 关键附加费 (Key Extras) - 小费 & 配送费
            // 小费：匹配多种可能的名称，包括长句式的小费描述
            val tipLine = order.feeLines.find { 
                val name = it.name.lowercase()
                name.contains("tip") || 
                name.contains("小费") ||
                name.contains("gratuity") ||
                name.contains("appreciation") ||
                // 适配新的长句描述："Help keep our team thriving..."
                name.contains("thriving") ||
                name.contains("team") && name.contains("keep")
            }
            // 优先取 feeLine，其次取 woofoodInfo，最后默认为 "0.00"
            val tipAmount = tipLine?.total ?: order.woofoodInfo?.tip?.takeIf { it.isNotEmpty() } ?: "0.00"
            
            // 配送费
            val deliveryLine = order.feeLines.find { 
                val name = it.name.lowercase()
                name.contains("delivery") || 
                name.contains("shipping") || 
                name.contains("配送") ||
                name.contains("外卖") ||
                name.contains("运费")
            }
            val deliveryAmount = deliveryLine?.total ?: order.woofoodInfo?.deliveryFee?.takeIf { it != "0.00" && it.isNotEmpty() }
            
            // 始终显示小费行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.tip_amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (tipAmount != "0.00") Color(0xFFE91E63) else Color.Gray
                )
                Text(
                    text = "$currencySymbol$tipAmount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (tipAmount != "0.00") Color(0xFFE91E63) else Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
                                
            if (deliveryAmount != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.payment_delivery_fee_label),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$currencySymbol$deliveryAmount",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            HorizontalDivider(color = Color(0xFFEEEEEE))
            Spacer(modifier = Modifier.height(16.dp))
            
            // 3. 基础明细 (Accounting)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailRow(stringResource(R.string.subtotal), "$currencySymbol${order.subtotal}")
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
        
// ================= 恢复丢失的 Dialog 组件 =================

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
    
    LaunchedEffect(Unit) {
        templateConfigViewModel.loadAllConfigs()
    }
    
    val fullDetailsTemplate = stringResource(R.string.full_details_template)
    val deliveryTemplate = stringResource(R.string.delivery_template) 
    val kitchenTemplate = stringResource(R.string.kitchen_template)
    
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
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(templateOptions) { (templateId, description, icon) ->
                        val checked = selectedTemplateIds.contains(templateId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTemplateIds = selectedTemplateIds.toMutableSet().apply {
                                        if (contains(templateId)) remove(templateId) else add(templateId)
                                    }
                                    if (!checked && !copyCounts.containsKey(templateId)) {
                                        copyCounts = copyCounts.toMutableMap().apply { put(templateId, 1) }
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                    onCheckedChange = { isChecked ->
                                    selectedTemplateIds = selectedTemplateIds.toMutableSet().apply {
                                            if (isChecked) add(templateId) else remove(templateId)
                                    }
                                        if (isChecked && !copyCounts.containsKey(templateId)) {
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
                                
                            val current = (copyCounts[templateId] ?: 1).coerceAtLeast(1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val next = (current - 1).coerceAtLeast(1)
                                        copyCounts = copyCounts.toMutableMap().apply { put(templateId, next) }
                                    },
                                    enabled = checked
                                    ) { Icon(Icons.Default.RemoveCircleOutline, null) }
                                    
                                    Text(
                                        text = current.toString(),
                                        color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    
                                IconButton(
                                    onClick = {
                                        val next = (current + 1).coerceAtMost(99)
                                        copyCounts = copyCounts.toMutableMap().apply { put(templateId, next) }
                                    },
                                    enabled = checked
                                    ) { Icon(Icons.Default.AddCircleOutline, null) }
                            }
                        }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            val payload = selectedTemplateIds.associateWith { id -> (copyCounts[id] ?: 1).coerceAtLeast(1) }
                            onConfirm(payload)
                        },
                        enabled = selectedTemplateIds.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.print_order))
                    }
                    Button(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

// 保留原文件中的工具函数 (精简)
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