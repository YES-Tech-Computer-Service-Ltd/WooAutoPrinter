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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wooauto.presentation.screens.settings.SettingsViewModel
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.wooauto.R
import com.example.wooauto.domain.models.TemplateConfig
import com.example.wooauto.domain.templates.TemplateType
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
                                showStoreName = config.showStoreName,
                                showStoreAddress = config.showStoreAddress,
                                showStorePhone = config.showStorePhone,
                                showOrderInfo = config.showOrderInfo,
                                showOrderNumber = config.showOrderNumber,
                                showOrderDate = config.showOrderDate,
                                showCustomerInfo = config.showCustomerInfo,
                                showCustomerName = config.showCustomerName,
                                showCustomerPhone = config.showCustomerPhone,
                                showDeliveryInfo = config.showDeliveryInfo,
                                showOrderContent = config.showOrderContent,
                                showItemDetails = config.showItemDetails,
                                showItemPrices = config.showItemPrices,
                                showOrderNotes = config.showOrderNotes,
                                showTotals = config.showTotals,
                                showPaymentInfo = config.showPaymentInfo,
                                showFooter = config.showFooter,
                                footerText = config.footerText
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
    showStoreName: Boolean,
    showStoreAddress: Boolean,
    showStorePhone: Boolean,
    showOrderInfo: Boolean,
    showOrderNumber: Boolean,
    showOrderDate: Boolean,
    showCustomerInfo: Boolean,
    showCustomerName: Boolean,
    showCustomerPhone: Boolean,
    showDeliveryInfo: Boolean,
    showOrderContent: Boolean,
    showItemDetails: Boolean,
    showItemPrices: Boolean,
    showOrderNotes: Boolean,
    showTotals: Boolean,
    showPaymentInfo: Boolean,
    showFooter: Boolean,
    footerText: String,
    paperWidth: Int = 80, // 默认80mm
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    // 获取商店信息
    val storeName by settingsViewModel.storeName.collectAsState()
    val storeAddress by settingsViewModel.storeAddress.collectAsState()
    val storePhone by settingsViewModel.storePhone.collectAsState()
    
    // 加载商店信息
    LaunchedEffect(Unit) {
        settingsViewModel.loadStoreInfo()
    }
    
    // 根据纸张宽度计算显示宽度
    val displayWidth = when (paperWidth) {
        57 -> 200.dp // 58mm纸张对应较窄的显示
        80 -> 300.dp // 80mm纸张对应较宽的显示
        else -> 250.dp // 默认宽度
    }
    
    val paperWidthText = when (paperWidth) {
        57 -> "58mm"
        80 -> "80mm" 
        else -> "${paperWidth}mm"
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 纸张宽度提示
        Card(
            modifier = Modifier
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text(
                text = "预览宽度: $paperWidthText 热敏纸",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        
        Card(
            modifier = Modifier
                .width(displayWidth)
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
                    var hasStoreContent = false
                    
                    if (showStoreName && storeName.isNotEmpty()) {
                        Text(
                            text = storeName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        hasStoreContent = true
                    }
                    
                    if (showStoreAddress && storeAddress.isNotEmpty()) {
                        Text(
                            text = storeAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        hasStoreContent = true
                    }
                    
                    if (showStorePhone && storePhone.isNotEmpty()) {
                        Text(
                            text = "Tel: $storePhone",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        hasStoreContent = true
                    }
                    
                    // 如果用户没有设置商店信息，显示示例信息
                    if (!hasStoreContent) {
                        if (showStoreName) {
                            Text(
                                text = "示例商店名称",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            hasStoreContent = true
                        }
                        
                        if (showStoreAddress) {
                            Text(
                                text = "示例商店地址",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            hasStoreContent = true
                        }
                        
                        if (showStorePhone) {
                            Text(
                                text = "Tel: 示例电话号码",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            hasStoreContent = true
                        }
                    }
                    
                    if (hasStoreContent) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                // 订单基本信息
                if (showOrderInfo) {
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
                                text = "ORD20250315001",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
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
                                text = "2025-03-15 14:30",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    if (showOrderNumber || showOrderDate) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                // 客户信息
                if (showCustomerInfo) {
                    var hasCustomerContent = false
                    
                    Text(
                        text = "CUSTOMER INFORMATION",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    if (showCustomerName) {
                        Text(
                            text = "Name: Customer A",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        hasCustomerContent = true
                    }
                    
                    if (showCustomerPhone) {
                        Text(
                            text = "Phone: +1 555-0123",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        hasCustomerContent = true
                    }
                    
                    if (hasCustomerContent) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
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
                        text = "Address: 123 Business Avenue, Unit 100",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "City: Business District, ST 12345",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Delivery Method: Standard Delivery",
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
                if (showOrderContent && showItemDetails) {
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
                if (showOrderContent && showOrderNotes) {
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
                if (showOrderContent && showTotals) {
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
                if (showFooter && footerText.isNotBlank()) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 支持多行页脚文本
                    footerText.split("\n").forEach { line ->
                        if (line.isNotBlank()) {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
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
    
    // 获取商店信息状态，用于检查是否已填写商店信息
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val storeName by settingsViewModel.storeName.collectAsState()
    val storeAddress by settingsViewModel.storeAddress.collectAsState()
    val storePhone by settingsViewModel.storePhone.collectAsState()
    
    // 检查商店信息是否已完整填写
    val hasStoreInfo = storeName.isNotBlank() && (storeAddress.isNotBlank() || storePhone.isNotBlank())
    
    // 加载商店信息
    LaunchedEffect(Unit) {
        settingsViewModel.loadStoreInfo()
    }
    
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
        
        // === 商店信息组 ===
        SettingCheckbox(
            label = "Store Information",
            isChecked = config.showStoreInfo,
            onCheckedChange = { newValue ->
                // 主选项控制所有子选项
                onConfigChange(config.copy(
                    showStoreInfo = newValue,
                    showStoreName = newValue,
                    showStoreAddress = newValue,
                    showStorePhone = newValue
                ))
            },
            enabled = hasStoreInfo,
            subtitle = if (!hasStoreInfo) "Please configure store information in Settings → Store Settings first" else null
        )
        
        // 商店信息子选项
        if (config.showStoreInfo && hasStoreInfo) {
            SettingCheckbox(
                label = "Store Name",
                isChecked = config.showStoreName,
                onCheckedChange = { newValue ->
                    val updatedConfig = config.copy(showStoreName = newValue)
                    if (!updatedConfig.showStoreName && !updatedConfig.showStoreAddress && !updatedConfig.showStorePhone) {
                        onConfigChange(updatedConfig.copy(showStoreInfo = false))
                    } else {
                        onConfigChange(updatedConfig)
                    }
                },
                indented = true,
                enabled = storeName.isNotBlank()
            )
            
            SettingCheckbox(
                label = "Store Address", 
                isChecked = config.showStoreAddress,
                onCheckedChange = { newValue ->
                    val updatedConfig = config.copy(showStoreAddress = newValue)
                    if (!updatedConfig.showStoreName && !updatedConfig.showStoreAddress && !updatedConfig.showStorePhone) {
                        onConfigChange(updatedConfig.copy(showStoreInfo = false))
                    } else {
                        onConfigChange(updatedConfig)
                    }
                },
                indented = true,
                enabled = storeAddress.isNotBlank()
            )
            
            SettingCheckbox(
                label = "Store Phone",
                isChecked = config.showStorePhone,
                onCheckedChange = { newValue ->
                    val updatedConfig = config.copy(showStorePhone = newValue)
                    if (!updatedConfig.showStoreName && !updatedConfig.showStoreAddress && !updatedConfig.showStorePhone) {
                        onConfigChange(updatedConfig.copy(showStoreInfo = false))
                    } else {
                        onConfigChange(updatedConfig)
                    }
                },
                indented = true,
                enabled = storePhone.isNotBlank()
            )
        }
        
        // === 订单基本信息组 ===
        SettingCheckbox(
            label = "Order Information",
            isChecked = config.showOrderInfo,
            onCheckedChange = { newValue ->
                onConfigChange(config.copy(
                    showOrderInfo = newValue,
                    showOrderNumber = newValue,
                    showOrderDate = newValue
                ))
            }
        )
        
        // 订单信息子选项
        if (config.showOrderInfo) {
            SettingCheckbox(
                label = "Order Number",
                isChecked = config.showOrderNumber,
                onCheckedChange = { newValue ->
                    val updatedConfig = config.copy(showOrderNumber = newValue)
                    if (!updatedConfig.showOrderNumber && !updatedConfig.showOrderDate) {
                        onConfigChange(updatedConfig.copy(showOrderInfo = false))
                    } else {
                        onConfigChange(updatedConfig)
                    }
                },
                indented = true
            )
            
            SettingCheckbox(
                label = "Order Date & Time",
                isChecked = config.showOrderDate,
                onCheckedChange = { newValue ->
                    val updatedConfig = config.copy(showOrderDate = newValue)
                    if (!updatedConfig.showOrderNumber && !updatedConfig.showOrderDate) {
                        onConfigChange(updatedConfig.copy(showOrderInfo = false))
                    } else {
                        onConfigChange(updatedConfig)
                    }
                },
                indented = true
            )
        }
        
        // === 客户信息组 ===
        SettingCheckbox(
            label = "Customer Information",
            isChecked = config.showCustomerInfo,
            onCheckedChange = { newValue ->
                onConfigChange(config.copy(
                    showCustomerInfo = newValue,
                    showCustomerName = newValue,
                    showCustomerPhone = newValue,
                    showDeliveryInfo = newValue
                ))
            }
        )
        
        // 客户信息子选项
        if (config.showCustomerInfo) {
            SettingCheckbox(
                label = "Customer Name",
                isChecked = config.showCustomerName,
                onCheckedChange = { newValue ->
                    val updatedConfig = config.copy(showCustomerName = newValue)
                    if (!updatedConfig.showCustomerName && !updatedConfig.showCustomerPhone && !updatedConfig.showDeliveryInfo) {
                        onConfigChange(updatedConfig.copy(showCustomerInfo = false))
                    } else {
                        onConfigChange(updatedConfig)
                    }
                },
                indented = true
            )
            
            SettingCheckbox(
                label = "Customer Phone",
                isChecked = config.showCustomerPhone,
                onCheckedChange = { newValue ->
                    val updatedConfig = config.copy(showCustomerPhone = newValue)
                    if (!updatedConfig.showCustomerName && !updatedConfig.showCustomerPhone && !updatedConfig.showDeliveryInfo) {
                        onConfigChange(updatedConfig.copy(showCustomerInfo = false))
                    } else {
                        onConfigChange(updatedConfig)
                    }
                },
                indented = true
            )
            
            SettingCheckbox(
                label = "Delivery Information",
                isChecked = config.showDeliveryInfo,
                onCheckedChange = { newValue ->
                    val updatedConfig = config.copy(showDeliveryInfo = newValue)
                    if (!updatedConfig.showCustomerName && !updatedConfig.showCustomerPhone && !updatedConfig.showDeliveryInfo) {
                        onConfigChange(updatedConfig.copy(showCustomerInfo = false))
                    } else {
                        onConfigChange(updatedConfig)
                    }
                },
                indented = true
            )
        }
        
        // === 订单内容组 ===
        SettingCheckbox(
            label = "Order Content",
            isChecked = config.showOrderContent,
            onCheckedChange = { newValue ->
                onConfigChange(config.copy(
                    showOrderContent = newValue,
                    showItemDetails = newValue,
                    showItemPrices = newValue,
                    showOrderNotes = newValue,
                    showTotals = newValue
                ))
            }
        )
        
        // 订单内容子选项
        if (config.showOrderContent) {
            SettingCheckbox(
                label = "Order Items",
                isChecked = config.showItemDetails,
                onCheckedChange = { newValue ->
                    val updatedConfig = config.copy(showItemDetails = newValue)
                    if (!updatedConfig.showItemDetails && !updatedConfig.showOrderNotes && !updatedConfig.showTotals) {
                        onConfigChange(updatedConfig.copy(showOrderContent = false))
                    } else {
                        onConfigChange(updatedConfig)
                    }
                },
                indented = true
            )
            
            SettingCheckbox(
                label = "Item Prices",
                isChecked = config.showItemPrices,
                onCheckedChange = { onConfigChange(config.copy(showItemPrices = it)) },
                indented = true,
                enabled = config.showItemDetails,
                subtitle = if (!config.showItemDetails) "Enable Order Items first" else null
            )
            
            SettingCheckbox(
                label = "Order Notes",
                isChecked = config.showOrderNotes,
                onCheckedChange = { newValue ->
                    val updatedConfig = config.copy(showOrderNotes = newValue)
                    if (!updatedConfig.showItemDetails && !updatedConfig.showOrderNotes && !updatedConfig.showTotals) {
                        onConfigChange(updatedConfig.copy(showOrderContent = false))
                    } else {
                        onConfigChange(updatedConfig)
                    }
                },
                indented = true
            )
            
            SettingCheckbox(
                label = "Order Totals",
                isChecked = config.showTotals,
                onCheckedChange = { newValue ->
                    val updatedConfig = config.copy(showTotals = newValue)
                    if (!updatedConfig.showItemDetails && !updatedConfig.showOrderNotes && !updatedConfig.showTotals) {
                        onConfigChange(updatedConfig.copy(showOrderContent = false))
                    } else {
                        onConfigChange(updatedConfig)
                    }
                },
                indented = true
            )
        }
        
        // === 支付信息组 ===
        SettingCheckbox(
            label = "Payment Information",
            isChecked = config.showPaymentInfo,
            onCheckedChange = { onConfigChange(config.copy(showPaymentInfo = it)) }
        )
        
        // === 页脚组 ===
        SettingCheckbox(
            label = "Footer",
            isChecked = config.showFooter,
            onCheckedChange = { onConfigChange(config.copy(showFooter = it)) }
        )
        
        // 页脚文本编辑
        if (config.showFooter) {
            FooterTextEditor(
                footerText = config.footerText,
                onFooterTextChange = { newText ->
                    onConfigChange(config.copy(footerText = newText))
                }
            )
        }
        
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
        
        // 显示商店信息状态提示
        if (!hasStoreInfo) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column {
                        Text(
                            text = "Store Information Required",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Please configure your store name and contact details in Settings → Store Settings to enable store information display.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingCheckbox(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    indented: Boolean = false,
    enabled: Boolean = true,
    subtitle: String? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (indented) 32.dp else 0.dp,
                    top = 8.dp,
                    bottom = if (subtitle != null) 4.dp else 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) 
                    MaterialTheme.colorScheme.onSurface 
                else 
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
        
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(
                    start = if (indented) 32.dp + 48.dp else 48.dp, // 留出checkbox的空间
                    bottom = 8.dp
                )
            )
        }
        
        HorizontalDivider(
            modifier = Modifier.padding(start = if (indented) 32.dp else 0.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePreviewDialogContent(
    templateId: String,
    onClose: () -> Unit,
    customTemplateName: String? = null,
    viewModel: TemplateConfigViewModel = hiltViewModel()
) {
    // 确定模板类型
    val templateType = when {
        templateId == "full_details" -> TemplateType.FULL_DETAILS
        templateId == "delivery" -> TemplateType.DELIVERY
        templateId == "kitchen" -> TemplateType.KITCHEN
        templateId == "new" || templateId.startsWith("custom_") -> TemplateType.FULL_DETAILS // 自定义模板默认为完整详情模板
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
    LaunchedEffect(templateId, customTemplateName) {
        viewModel.loadConfigById(templateId, templateType, customTemplateName)
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
    val templateName = currentConfig?.templateName ?: customTemplateName ?: when (templateId) {
        "full_details" -> "Full Order Details"
        "delivery" -> "Delivery Receipt"
        "kitchen" -> "Kitchen Order"
        "new" -> "New Custom Template"
        else -> if (templateId.startsWith("custom_")) "Custom Template" else "Unknown Template"
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
                        
                        // 关闭按钮
                        IconButton(onClick = { onClose() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close"
                            )
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
                                    showStoreName = config.showStoreName,
                                    showStoreAddress = config.showStoreAddress,
                                    showStorePhone = config.showStorePhone,
                                    showOrderInfo = config.showOrderInfo,
                                    showOrderNumber = config.showOrderNumber,
                                    showOrderDate = config.showOrderDate,
                                    showCustomerInfo = config.showCustomerInfo,
                                    showCustomerName = config.showCustomerName,
                                    showCustomerPhone = config.showCustomerPhone,
                                    showDeliveryInfo = config.showDeliveryInfo,
                                    showOrderContent = config.showOrderContent,
                                    showItemDetails = config.showItemDetails,
                                    showItemPrices = config.showItemPrices,
                                    showOrderNotes = config.showOrderNotes,
                                    showTotals = config.showTotals,
                                    showPaymentInfo = config.showPaymentInfo,
                                    showFooter = config.showFooter,
                                    footerText = config.footerText
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
                                    onClose = onClose,
                                    snackbarHostState = snackbarHostState
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FooterTextEditor(
    footerText: String,
    onFooterTextChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
    ) {
        Text(
            text = "Custom Footer Text",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        androidx.compose.material3.OutlinedTextField(
            value = footerText,
            onValueChange = onFooterTextChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { 
                Text("Enter custom footer text (supports multiple lines)")
            },
            minLines = 2,
            maxLines = 4,
            singleLine = false
        )
        
        Text(
            text = "Tip: Use line breaks to create multiple lines. Leave blank to hide footer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
} 