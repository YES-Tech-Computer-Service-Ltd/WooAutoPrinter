package com.example.wooauto.presentation.screens.templatePreview

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.R
import com.example.wooauto.domain.models.TemplateConfig
import com.example.wooauto.presentation.screens.settings.TemplateType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePreviewScreen(
    navController: NavController,
    templateId: String,
    viewModel: TemplateConfigViewModel = hiltViewModel()
) {
    // 确定模板类型
    val templateType = when (templateId) {
        "full_details" -> TemplateType.FULL_DETAILS
        "delivery" -> TemplateType.DELIVERY
        "kitchen" -> TemplateType.KITCHEN
        "new" -> TemplateType.FULL_DETAILS
        else -> TemplateType.FULL_DETAILS
    }
    
    // 从ViewModel获取状态
    val currentConfig by viewModel.currentConfig.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    
    // 是否正在创建新模板
    val isNewTemplate = templateId == "new"
    
    // 创建一个state来跟踪当前选中的选项卡 (预览/设置)
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Preview", "Settings")
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // 加载配置
    LaunchedEffect(templateId) {
        viewModel.loadConfigById(templateId, templateType)
    }
    
    // 处理错误消息
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearErrorMessage()
        }
    }
    
    // 处理成功消息
    LaunchedEffect(successMessage) {
        successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSuccessMessage()
        }
    }
    
    // 获取模板名称
    val templateName = currentConfig?.templateName ?: when (templateId) {
        "full_details" -> "Full Order Details"
        "delivery" -> "Delivery Receipt"
        "kitchen" -> "Kitchen Order"
        "new" -> "New Custom Template"
        else -> "Custom Template"
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(templateName) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // 重置为默认配置按钮
                    IconButton(
                        onClick = {
                            viewModel.resetToDefault(templateId, templateType)
                        },
                        enabled = !isLoading && !isSaving
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset to Default"
                        )
                    }
                    
                    // 保存按钮
                    IconButton(
                        onClick = {
                            viewModel.saveCurrentConfig()
                        },
                        enabled = !isLoading && !isSaving && currentConfig != null
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(4.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save"
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
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        currentConfig?.let { config ->
                            TemplatePreview(
                                showStoreInfo = config.showStoreInfo,
                                showOrderNumber = config.showOrderNumber,
                                showCustomerInfo = config.showCustomerInfo,
                                showOrderDate = config.showOrderDate,
                                showDeliveryInfo = config.showDeliveryInfo,
                                showPaymentInfo = config.showPaymentInfo,
                                showItemDetails = config.showItemDetails,
                                showItemPrices = config.showItemPrices,
                                showOrderNotes = config.showOrderNotes,
                                showTotals = config.showTotals,
                                showFooter = config.showFooter
                            )
                        }
                    }
                }
                1 -> {
                    // 设置选项卡
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        currentConfig?.let { config ->
                            TemplateSettings(
                                config = config,
                                onConfigChange = { updatedConfig ->
                                    viewModel.updateCurrentConfig(updatedConfig)
                                },
                                onClose = { navController.navigateUp() },
                                snackbarHostState = snackbarHostState
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TemplatePreview(
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
                    HorizontalDivider()
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
                
                HorizontalDivider()
                
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
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
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
                    HorizontalDivider()
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
                    HorizontalDivider()
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
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
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
                    HorizontalDivider()
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
    config: TemplateConfig,
    onConfigChange: (TemplateConfig) -> Unit,
    onClose: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val coroutineScope = rememberCoroutineScope()
    val settingsSavedMessage = stringResource(R.string.settings_saved)
    
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
            isChecked = config.showStoreInfo,
            onCheckedChange = { onConfigChange(config.copy(showStoreInfo = it)) }
        )
        
        SettingCheckbox(
            label = "Order Number",
            isChecked = config.showOrderNumber,
            onCheckedChange = { onConfigChange(config.copy(showOrderNumber = it)) }
        )
        
        SettingCheckbox(
            label = "Order Date and Time",
            isChecked = config.showOrderDate,
            onCheckedChange = { onConfigChange(config.copy(showOrderDate = it)) }
        )
        
        SettingCheckbox(
            label = "Customer Information",
            isChecked = config.showCustomerInfo,
            onCheckedChange = { onConfigChange(config.copy(showCustomerInfo = it)) }
        )
        
        SettingCheckbox(
            label = "Delivery Information",
            isChecked = config.showDeliveryInfo,
            onCheckedChange = { onConfigChange(config.copy(showDeliveryInfo = it)) }
        )
        
        SettingCheckbox(
            label = "Payment Method",
            isChecked = config.showPaymentInfo,
            onCheckedChange = { onConfigChange(config.copy(showPaymentInfo = it)) }
        )
        
        SettingCheckbox(
            label = "Order Items",
            isChecked = config.showItemDetails,
            onCheckedChange = { onConfigChange(config.copy(showItemDetails = it)) }
        )
        
        SettingCheckbox(
            label = "Item Prices",
            isChecked = config.showItemPrices,
            onCheckedChange = { onConfigChange(config.copy(showItemPrices = it)) },
            indented = true,
            enabled = config.showItemDetails
        )
        
        SettingCheckbox(
            label = "Order Notes",
            isChecked = config.showOrderNotes,
            onCheckedChange = { onConfigChange(config.copy(showOrderNotes = it)) }
        )
        
        SettingCheckbox(
            label = "Order Totals",
            isChecked = config.showTotals,
            onCheckedChange = { onConfigChange(config.copy(showTotals = it)) }
        )
        
        SettingCheckbox(
            label = "Footer",
            isChecked = config.showFooter,
            onCheckedChange = { onConfigChange(config.copy(showFooter = it)) }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Template Info",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Template ID: ${config.templateId}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "Template Type: ${config.templateType.name}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "Enabled Fields: ${config.getEnabledFieldCount()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
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
    
    HorizontalDivider(
        modifier = Modifier.padding(start = if (indented) 32.dp else 0.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePreviewDialogContent(
    templateId: String,
    onClose: () -> Unit,
    viewModel: TemplateConfigViewModel = hiltViewModel()
) {
    // 确定模板类型
    val templateType = when (templateId) {
        "full_details" -> TemplateType.FULL_DETAILS
        "delivery" -> TemplateType.DELIVERY
        "kitchen" -> TemplateType.KITCHEN
        "new" -> TemplateType.FULL_DETAILS // 默认为完整详情模板
        else -> TemplateType.FULL_DETAILS
    }
    
    // 从ViewModel获取状态
    val currentConfig by viewModel.currentConfig.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    
    // 是否正在创建新模板
    val isNewTemplate = templateId == "new"
    
    // 创建一个state来跟踪当前选中的选项卡 (预览/设置)
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Preview", "Settings")
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // 加载配置
    LaunchedEffect(templateId) {
        viewModel.loadConfigById(templateId, templateType)
    }
    
    // 处理错误消息
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearErrorMessage()
        }
    }
    
    // 处理成功消息
    LaunchedEffect(successMessage) {
        successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSuccessMessage()
        }
    }
    
    // 获取模板名称
    val templateName = currentConfig?.templateName ?: when (templateId) {
        "full_details" -> "Full Order Details"
        "delivery" -> "Delivery Receipt"
        "kitchen" -> "Kitchen Order"
        "new" -> "New Custom Template"
        else -> "Custom Template"
    }
    
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(templateName) },
                    navigationIcon = {
                        IconButton(onClick = { onClose() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to templates"
                            )
                        }
                    },
                    actions = {
                        if (isNewTemplate) {
                            // 提前获取字符串资源
                            val successMessage = stringResource(R.string.settings_saved)
                            IconButton(onClick = {
                                // 保存新模板并立即关闭对话框
                                onClose() // 先关闭对话框
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(successMessage)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = stringResource(R.string.save)
                                )
                            }
                        } else {
                            // 获取关闭按钮的字符串资源
                            val closeString = stringResource(R.string.close)
                            IconButton(onClick = { onClose() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = closeString
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
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
                            onShowFooterChange = { showFooter = it },
                            onClose = onClose,
                            snackbarHostState = snackbarHostState
                        )
                    }
                }
            }
        }
    }
} 