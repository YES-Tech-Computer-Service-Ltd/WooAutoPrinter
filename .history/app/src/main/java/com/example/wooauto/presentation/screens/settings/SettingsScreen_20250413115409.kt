package com.example.wooauto.presentation.screens.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.wooauto.R
import com.example.wooauto.utils.LocaleHelper
import com.example.wooauto.presentation.navigation.Screen
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.material3.HorizontalDivider
import kotlinx.coroutines.delay
import com.example.wooauto.presentation.theme.WooAutoTheme
import com.example.wooauto.utils.LocaleManager
import com.example.wooauto.domain.templates.TemplateType
import androidx.compose.material3.TextField
import androidx.compose.material3.RadioButton
import kotlinx.coroutines.runBlocking
import androidx.compose.material3.IconButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navController: NavController
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // API配置相关状态
    val siteUrl by viewModel.siteUrl.collectAsState()
    val consumerKey by viewModel.consumerKey.collectAsState()
    val consumerSecret by viewModel.consumerSecret.collectAsState()
    val pollingInterval by viewModel.pollingInterval.collectAsState()
    val useWooCommerceFood by viewModel.useWooCommerceFood.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val connectionTestResult by viewModel.connectionTestResult.collectAsState()
    
    // 获取当前语言
    val currentLocale by viewModel.currentLocale.collectAsState()
    
    // 预先获取需要在非Composable上下文中使用的字符串资源
    val scanSuccessText = stringResource(R.string.scan_successful)
    val scanQrCodePromptText = stringResource(R.string.scan_woocommerce_site_url_qr)
    val connectionSuccessText = stringResource(R.string.connection_test_success)
    val connectionFailedText = stringResource(R.string.connection_test_failed)
    val featureComingSoonText = stringResource(R.string.feature_coming_soon)
    val appVersionText = stringResource(R.string.app_version)
    val fillAllFieldsText = stringResource(R.string.fill_all_fields)
    
    // 自动化任务状态
    var automaticOrderProcessing by remember { mutableStateOf(true) }
    var automaticPrinting by remember { mutableStateOf(false) }
    var inventoryAlerts by remember { mutableStateOf(true) }
    var dailyBackup by remember { mutableStateOf(false) }
    
    // 默认打印模板选择
    var selectedTemplate by remember { 
        mutableStateOf(
            runBlocking { 
                viewModel.getDefaultTemplateType() ?: TemplateType.FULL_DETAILS 
            }
        )
    }
    
    // 自动更新状态
    var autoUpdate by remember { mutableStateOf(false) }
    
    // 显示不同设置对话框的状态
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showApiDialog by remember { mutableStateOf(false) }
    var showAutomationDialog by remember { mutableStateOf(false) }
    
    // API设置表单状态
    var siteUrlInput by remember { mutableStateOf(siteUrl) }
    var consumerKeyInput by remember { mutableStateOf(consumerKey) }
    var consumerSecretInput by remember { mutableStateOf(consumerSecret) }
    var pollingIntervalInput by remember { mutableStateOf(pollingInterval.toString()) }
    var useWooCommerceFoodInput by remember { mutableStateOf(useWooCommerceFood) }
    
    // 二维码扫描器
    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { scanResult ->
            // 不直接设置siteUrlInput，而是交给ViewModel处理
            viewModel.handleQrCodeResult(scanResult)
            coroutineScope.launch {
                snackbarHostState.showSnackbar(scanSuccessText)
            }
        }
    }
    
    // 处理二维码扫描事件
    LaunchedEffect(viewModel) {
        viewModel.scanQrCodeEvent.collect {
            val options = ScanOptions().apply {
                setPrompt(scanQrCodePromptText)
                setBeepEnabled(true)
                setOrientationLocked(false)
            }
            barcodeLauncher.launch(options)
        }
    }
    
    // 当配置发生变化时更新输入值
    LaunchedEffect(siteUrl, consumerKey, consumerSecret, pollingInterval, useWooCommerceFood) {
        siteUrlInput = siteUrl
        consumerKeyInput = consumerKey
        consumerSecretInput = consumerSecret
        pollingIntervalInput = pollingInterval.toString()
        useWooCommerceFoodInput = useWooCommerceFood
    }
    
    // 监听连接测试结果
    LaunchedEffect(connectionTestResult) {
        connectionTestResult?.let { result ->
            when (result) {
                is SettingsViewModel.ConnectionTestResult.Success -> {
                    // 记录成功日志
                    Log.d("SettingsScreen", "API连接测试成功")
                    
                    // 显示成功提示
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(connectionSuccessText)
                    }
                    
                    // 延迟一小段时间后返回到订单页面
                    delay(1500)
                    
                    // 导航回订单页面
                    navController.navigate("orders") {
                        // 清空导航栈，避免重复页面
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is SettingsViewModel.ConnectionTestResult.Error -> {
                    // 显示错误消息
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(connectionFailedText + ": " + result.message)
                    }
                }
            }
            
            // 处理完成后清除结果，避免再次触发
            viewModel.clearConnectionTestResult()
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
    Column(
        modifier = Modifier
            .fillMaxSize()
                .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
                text = stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
            // API配置卡片
            SettingsCategoryCard {
                SettingsNavigationItem(
                    title = stringResource(R.string.api_configuration),
                    subTitle = if (siteUrl.isNotEmpty() && consumerKey.isNotEmpty() && consumerSecret.isNotEmpty()) {
                        stringResource(R.string.api_configured)
                    } else {
                        stringResource(R.string.api_not_configured)
                    },
            icon = Icons.Filled.Cloud,
                    onClick = {
                        Log.d("设置导航", "点击了API配置项")
                        showApiDialog = true
                    }
                )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 自动化任务卡片
        SettingsCategoryCard {
            SettingsNavigationItem(
                title = stringResource(R.string.automation_tasks),
                subTitle = stringResource(R.string.automatic_order_processing_desc),
                icon = Icons.Filled.AutoAwesome,
                onClick = {
                    Log.d("设置导航", "点击了自动化任务项")
                    navController.navigate(Screen.AutomationSettings.route)
                }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
            // 其他设置卡片
            SettingsCategoryCard {
                SettingsNavigationItem(
                    title = stringResource(R.string.printer_settings),
                    icon = Icons.Filled.Print,
                    onClick = {
                        Log.d("设置导航", "点击了打印设置项")
                        navController.navigate(Screen.PrinterSettings.route)
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                SettingsNavigationItem(
                    title = stringResource(R.string.sound_settings),
                    icon = Icons.Default.VolumeUp,
                    onClick = {
                        Log.d("设置导航", "点击了声音设置项")
                        navController.navigate(Screen.SoundSettings.route)
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                SettingsNavigationItem(
                    title = stringResource(R.string.store_settings),
                    icon = Icons.Default.Store,
                    onClick = { 
                        /* 导航到店铺设置 */
                        Log.d("设置导航", "点击了店铺信息设置项")
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(featureComingSoonText)
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 应用程序设置
            SettingsCategoryCard {
                // 语言设置
                SettingItem(
                    icon = Icons.Outlined.Language,
                    title = stringResource(id = R.string.language),
                    subtitle = if (currentLocale.language == "zh") stringResource(id = R.string.chinese) else stringResource(id = R.string.english),
                    onClick = {
                        Log.d("设置", "点击了语言设置")
                        showLanguageDialog = true
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                
                // 关于
                SettingsNavigationItem(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.about),
                    onClick = {
                        Log.d("设置", "点击了关于")
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(appVersionText)
                        }
                    }
                )
            }
            
            // 自动更新
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.auto_update),
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Switch(
                    checked = autoUpdate,
                    onCheckedChange = { autoUpdate = it }
                )
            }
        }
        
        // 语言选择对话框
        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { Text(stringResource(id = R.string.language)) },
                text = {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 不再在这里使用LocalContext.current
                                    viewModel.setAppLanguage(Locale.ENGLISH)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(id = R.string.english))
                            if (currentLocale.language == "en") {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        HorizontalDivider()
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 不再在这里使用LocalContext.current
                                    viewModel.setAppLanguage(Locale.SIMPLIFIED_CHINESE)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(id = R.string.chinese))
                            if (currentLocale.language == "zh") {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text(stringResource(id = R.string.cancel))
                    }
                }
            )
        }
        
        // API配置对话框
        if (showApiDialog) {
            AlertDialog(
                onDismissRequest = { showApiDialog = false },
                title = { Text(stringResource(R.string.api_configuration)) },
                text = {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = siteUrlInput,
                                onValueChange = { siteUrlInput = it },
                                label = { Text(stringResource(R.string.website_url)) },
                                placeholder = { Text(stringResource(R.string.website_url_placeholder)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                isError = !siteUrlInput.contains("http")
                            )
                            
                            IconButton(
                                onClick = { viewModel.handleQrCodeScan() }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = stringResource(R.string.scan_qr_code)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = consumerKeyInput,
                            onValueChange = { consumerKeyInput = it },
                            label = { Text(stringResource(R.string.api_key)) },
                            placeholder = { Text(stringResource(R.string.api_key_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = consumerKeyInput.contains("http"),
                            colors = OutlinedTextFieldDefaults.colors(
                                errorBorderColor = MaterialTheme.colorScheme.error,
                                errorLabelColor = MaterialTheme.colorScheme.error
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = consumerSecretInput,
                            onValueChange = { consumerSecretInput = it },
                            label = { Text(stringResource(R.string.api_secret)) },
                            placeholder = { Text(stringResource(R.string.api_secret_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = consumerSecretInput.contains("http"),
                            colors = OutlinedTextFieldDefaults.colors(
                                errorBorderColor = MaterialTheme.colorScheme.error,
                                errorLabelColor = MaterialTheme.colorScheme.error
                            )
                        )
                        
                        OutlinedTextField(
                            value = pollingIntervalInput,
                            onValueChange = { 
                                // 确保只输入数字
                                if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                    pollingIntervalInput = it
                                }
                            },
                            label = { Text(stringResource(R.string.polling_interval)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.plugin_woocommerce_food),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
                            Switch(
                                checked = useWooCommerceFoodInput,
                                onCheckedChange = { useWooCommerceFoodInput = it }
                            )
                        }
                        
                        if (isTestingConnection) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.testing_connection))
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                if (siteUrlInput.isNotEmpty() && consumerKeyInput.isNotEmpty() && consumerSecretInput.isNotEmpty()) {
                                    // 保存配置
                                    viewModel.updateSiteUrl(siteUrlInput)
                                    viewModel.updateConsumerKey(consumerKeyInput)
                                    viewModel.updateConsumerSecret(consumerSecretInput)
                                    viewModel.updatePollingInterval(pollingIntervalInput.toIntOrNull() ?: 30)
                                    viewModel.updateUseWooCommerceFood(useWooCommerceFoodInput)
                                    
                                    // 测试连接
                                    viewModel.testConnection()
                                    showApiDialog = false
                                } else {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(fillAllFieldsText)
                                    }
                                }
                            },
                            enabled = !isTestingConnection
                        ) {
                            Text(stringResource(R.string.save_and_test))
                        }
                        
                        TextButton(onClick = { showApiDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsCategoryCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
                content()
        }
    }
}

@Composable
fun SettingsNavigationItem(
    icon: ImageVector,
    title: String,
    subTitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            
            if (subTitle != null) {
            Text(
                    text = subTitle,
                    style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            }
        }
        
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "箭头",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            
            if (subtitle.isNotEmpty()) {
            Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            }
        }
        
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "箭头",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
} 