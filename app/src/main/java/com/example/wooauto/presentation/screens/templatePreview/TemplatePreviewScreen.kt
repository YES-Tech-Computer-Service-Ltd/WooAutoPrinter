package com.example.wooauto.presentation.screens.templatePreview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.wooauto.presentation.screens.printTemplates.TemplateType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePreviewScreen(
    navController: NavController,
    templateId: String
) {
    // 确定模板类型
    val templateType = when (templateId) {
        "full_details" -> TemplateType.FULL_DETAILS
        "delivery" -> TemplateType.DELIVERY
        "kitchen" -> TemplateType.KITCHEN
        "new" -> TemplateType.FULL_DETAILS // 默认为完整详情模板
        else -> TemplateType.FULL_DETAILS
    }
    
    // 获取模板名称
    val templateName = when (templateId) {
        "full_details" -> "Full Order Details"
        "delivery" -> "Delivery Receipt"
        "kitchen" -> "Kitchen Order"
        "new" -> "New Custom Template"
        else -> "Custom Template"
    }
    
    // 是否正在创建新模板
    val isNewTemplate = templateId == "new"
    
    // 创建一个state来跟踪当前选中的选项卡 (预览/设置)
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Preview", "Settings")
    
    // 创建复选框状态
    var showStoreInfo by remember { mutableStateOf(true) }
    var showOrderNumber by remember { mutableStateOf(true) }
    var showCustomerInfo by remember { mutableStateOf(templateType != TemplateType.KITCHEN) }
    var showOrderDate by remember { mutableStateOf(true) }
    var showDeliveryInfo by remember { mutableStateOf(templateType == TemplateType.DELIVERY) }
    var showPaymentInfo by remember { mutableStateOf(templateType == TemplateType.FULL_DETAILS) }
    var showItemDetails by remember { mutableStateOf(true) }
    var showItemPrices by remember { mutableStateOf(templateType != TemplateType.KITCHEN) }
    var showOrderNotes by remember { mutableStateOf(templateType != TemplateType.KITCHEN) }
    var showTotals by remember { mutableStateOf(templateType != TemplateType.KITCHEN) }
    var showFooter by remember { mutableStateOf(templateType != TemplateType.KITCHEN) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(templateName) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isNewTemplate) {
                        IconButton(onClick = {
                            // 保存新模板
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Template saved")
                                navController.popBackStack()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save"
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            // 编辑现有模板
                        }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 选项卡
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title) },
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index }
                    )
                }
            }
            
            when (selectedTabIndex) {
                0 -> {
                    // 预览选项卡
                    TemplatePreview(
                        templateType = templateType,
                        showStoreInfo = showStoreInfo,
                        showOrderNumber = showOrderNumber,
                        showCustomerInfo = showCustomerInfo,
                        showOrderDate = showOrderDate,
                        showDeliveryInfo = showDeliveryInfo,
                        showPaymentInfo = showPaymentInfo,
                        showItemDetails = showItemDetails,
                        showItemPrices = showItemPrices,
                        showOrderNotes = showOrderNotes,
                        showTotals = showTotals,
                        showFooter = showFooter
                    )
                }
                1 -> {
                    // 设置选项卡
                    TemplateSettings(
                        showStoreInfo = showStoreInfo,
                        onShowStoreInfoChange = { showStoreInfo = it },
                        showOrderNumber = showOrderNumber,
                        onShowOrderNumberChange = { showOrderNumber = it },
                        showCustomerInfo = showCustomerInfo,
                        onShowCustomerInfoChange = { showCustomerInfo = it },
                        showOrderDate = showOrderDate,
                        onShowOrderDateChange = { showOrderDate = it },
                        showDeliveryInfo = showDeliveryInfo,
                        onShowDeliveryInfoChange = { showDeliveryInfo = it },
                        showPaymentInfo = showPaymentInfo,
                        onShowPaymentInfoChange = { showPaymentInfo = it },
                        showItemDetails = showItemDetails,
                        onShowItemDetailsChange = { showItemDetails = it },
                        showItemPrices = showItemPrices,
                        onShowItemPricesChange = { showItemPrices = it },
                        showOrderNotes = showOrderNotes,
                        onShowOrderNotesChange = { showOrderNotes = it },
                        showTotals = showTotals,
                        onShowTotalsChange = { showTotals = it },
                        showFooter = showFooter,
                        onShowFooterChange = { showFooter = it }
                    )
                }
            }
        }
    }
}

@Composable
fun TemplatePreview(
    templateType: TemplateType,
    showStoreInfo: Boolean,
    showOrderNumber: Boolean,
    showCustomerInfo: Boolean,
    showOrderDate: Boolean,
    showDeliveryInfo: Boolean,
    showPaymentInfo: Boolean,
    showItemDetails: Boolean,
    showItemPrices: Boolean,
    showOrderNotes: Boolean,
    showTotals: Boolean,
    showFooter: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 热敏打印机格式 - 通常使用固定宽度的字体和简单排版
                
                // 商店信息
                if (showStoreInfo) {
                    Text(
                        text = "MY STORE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "123 Main Street, City",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "Tel: (123) 456-7890",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // 订单编号
                if (showOrderNumber) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ORDER #:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "WO12345",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                // 订单日期
                if (showOrderDate) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "DATE:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "2025-03-06 14:30",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // 客户信息
                if (showCustomerInfo) {
                    Text(
                        text = "CUSTOMER INFORMATION",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Name: John Smith",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Phone: (987) 654-3210",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // 配送信息
                if (showDeliveryInfo) {
                    Text(
                        text = "DELIVERY INFORMATION",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Address: 456 Oak Street, Apt 789",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "City: Springfield, ST 12345",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Delivery Method: Express Delivery",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // 支付信息
                if (showPaymentInfo) {
                    Text(
                        text = "PAYMENT METHOD",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Credit Card (XXXX-XXXX-XXXX-1234)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Divider()
                
                // 商品明细
                if (showItemDetails) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "ORDER ITEMS",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // 表头
                    if (showItemPrices) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "ITEM",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1.5f)
                            )
                            Text(
                                text = "QTY",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(0.5f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "PRICE",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "ITEM",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1.5f)
                            )
                            Text(
                                text = "QTY",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(0.5f),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    // 商品项 1
                    if (showItemPrices) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.weight(1.5f)
                            ) {
                                Text(
                                    text = "Chicken Fried Rice",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "No onions, extra sauce",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Text(
                                text = "2",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.5f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "$25.90",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.weight(1.5f)
                            ) {
                                Text(
                                    text = "Chicken Fried Rice",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "No onions, extra sauce",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Text(
                                text = "2",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.5f),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // 商品项 2
                    if (showItemPrices) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.weight(1.5f)
                            ) {
                                Text(
                                    text = "Spring Rolls",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                text = "4",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.5f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "$12.00",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Spring Rolls",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1.5f)
                            )
                            Text(
                                text = "4",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.5f),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // 订单备注
                if (showOrderNotes) {
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "ORDER NOTES:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Please deliver to back door. Call when arrived.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // 合计
                if (showTotals) {
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Subtotal:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$37.90",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Delivery Fee:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$5.99",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tax:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$3.40",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "TOTAL:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$47.29",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // 页脚
                if (showFooter) {
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Thank you for your order!",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "Visit us online at www.mystore.com",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun TemplateSettings(
    showStoreInfo: Boolean,
    onShowStoreInfoChange: (Boolean) -> Unit,
    showOrderNumber: Boolean,
    onShowOrderNumberChange: (Boolean) -> Unit,
    showCustomerInfo: Boolean,
    onShowCustomerInfoChange: (Boolean) -> Unit,
    showOrderDate: Boolean,
    onShowOrderDateChange: (Boolean) -> Unit,
    showDeliveryInfo: Boolean,
    onShowDeliveryInfoChange: (Boolean) -> Unit,
    showPaymentInfo: Boolean,
    onShowPaymentInfoChange: (Boolean) -> Unit,
    showItemDetails: Boolean,
    onShowItemDetailsChange: (Boolean) -> Unit,
    showItemPrices: Boolean,
    onShowItemPricesChange: (Boolean) -> Unit,
    showOrderNotes: Boolean,
    onShowOrderNotesChange: (Boolean) -> Unit,
    showTotals: Boolean,
    onShowTotalsChange: (Boolean) -> Unit,
    showFooter: Boolean,
    onShowFooterChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Receipt Content",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "Select which elements to show in the receipt template:",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        SettingCheckbox(
            label = "Store Information",
            isChecked = showStoreInfo,
            onCheckedChange = onShowStoreInfoChange
        )
        
        SettingCheckbox(
            label = "Order Number",
            isChecked = showOrderNumber,
            onCheckedChange = onShowOrderNumberChange
        )
        
        SettingCheckbox(
            label = "Order Date and Time",
            isChecked = showOrderDate,
            onCheckedChange = onShowOrderDateChange
        )
        
        SettingCheckbox(
            label = "Customer Information",
            isChecked = showCustomerInfo,
            onCheckedChange = onShowCustomerInfoChange
        )
        
        SettingCheckbox(
            label = "Delivery Information",
            isChecked = showDeliveryInfo,
            onCheckedChange = onShowDeliveryInfoChange
        )
        
        SettingCheckbox(
            label = "Payment Method",
            isChecked = showPaymentInfo,
            onCheckedChange = onShowPaymentInfoChange
        )
        
        SettingCheckbox(
            label = "Order Items",
            isChecked = showItemDetails,
            onCheckedChange = onShowItemDetailsChange
        )
        
        SettingCheckbox(
            label = "Item Prices",
            isChecked = showItemPrices,
            onCheckedChange = onShowItemPricesChange,
            indented = true,
            enabled = showItemDetails
        )
        
        SettingCheckbox(
            label = "Order Notes",
            isChecked = showOrderNotes,
            onCheckedChange = onShowOrderNotesChange
        )
        
        SettingCheckbox(
            label = "Order Totals",
            isChecked = showTotals,
            onCheckedChange = onShowTotalsChange
        )
        
        SettingCheckbox(
            label = "Footer",
            isChecked = showFooter,
            onCheckedChange = onShowFooterChange
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { /* TODO: 保存模板设置 */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Apply Settings")
        }
    }
}

@Composable
fun SettingCheckbox(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    indented: Boolean = false,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (indented) 32.dp else 0.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) 
                MaterialTheme.colorScheme.onSurface 
            else 
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
    
    Divider(
        modifier = Modifier.padding(start = if (indented) 32.dp else 0.dp)
    )
} 