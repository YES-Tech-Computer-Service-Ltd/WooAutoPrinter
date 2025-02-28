package com.example.wooauto.presentation.screens.settings

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.wooauto.utils.LocaleHelper
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.material3.HorizontalDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navController: NavController
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
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
    
    // 自动化任务状态
    var automaticOrderProcessing by remember { mutableStateOf(true) }
    var automaticPrinting by remember { mutableStateOf(false) }
    var inventoryAlerts by remember { mutableStateOf(true) }
    var dailyBackup by remember { mutableStateOf(false) }
    
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
    
    // 当配置发生变化时更新输入值
    LaunchedEffect(siteUrl, consumerKey, consumerSecret, pollingInterval, useWooCommerceFood) {
        siteUrlInput = siteUrl
        consumerKeyInput = consumerKey
        consumerSecretInput = consumerSecret
        pollingIntervalInput = pollingInterval.toString()
        useWooCommerceFoodInput = useWooCommerceFood
    }
    
    // 连接测试结果处理
    LaunchedEffect(connectionTestResult) {
        connectionTestResult?.let {
            when (it) {
                is SettingsViewModel.ConnectionTestResult.Success -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("连接测试成功！")
                    }
                }
                is SettingsViewModel.ConnectionTestResult.Error -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("连接测试失败: ${it.message}")
                    }
                }
            }
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
                text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
            // API配置卡片
            SettingsCategoryCard {
                SettingsNavigationItem(
                    title = "WooCommerce API 配置",
                    subTitle = if (siteUrl.isNotEmpty() && consumerKey.isNotEmpty() && consumerSecret.isNotEmpty()) {
                        "已配置"
                    } else {
                        "未配置"
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
                title = "自动化任务",
                subTitle = "管理自动化任务",
                icon = Icons.Filled.AutoAwesome,
                onClick = {
                    Log.d("设置导航", "点击了自动化任务项")
                    showAutomationDialog = true
                }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
            // 其他设置卡片
            SettingsCategoryCard {
                SettingsNavigationItem(
                    title = "声音设置",
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    onClick = {
                        Log.d("设置导航", "点击了声音设置项")
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("声音设置功能即将推出")
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                
                // 打印设置
                SettingsNavigationItem(
                    title = "打印设置",
                    icon = Icons.Filled.Print,
                    onClick = {
                        Log.d("设置导航", "点击了打印设置项")
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("打印设置功能即将推出")
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 应用程序设置
            SettingsCategoryCard {
                // 语言设置
                SettingsNavigationItem(
                    icon = Icons.Outlined.Language,
                    title = "语言设置",
                    subTitle = when(currentLocale) {
                        Locale.ENGLISH -> "English"
                        Locale.SIMPLIFIED_CHINESE -> "简体中文"
                        else -> "系统默认"
                    },
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
                    title = "关于",
                    onClick = {
                        Log.d("设置", "点击了关于")
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("WooAuto - 版本 1.0.0")
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
                    text = "自动更新",
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
                title = { Text("选择语言") },
                text = {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAppLanguage(Locale.ENGLISH)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "English",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (currentLocale == Locale.ENGLISH) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选择",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        HorizontalDivider()
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAppLanguage(Locale.SIMPLIFIED_CHINESE)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "简体中文",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (currentLocale == Locale.SIMPLIFIED_CHINESE) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选择",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
        
        // 自动化任务对话框
        if (showAutomationDialog) {
            AlertDialog(
                onDismissRequest = { showAutomationDialog = false },
                title = { Text("自动化任务") },
                text = {
                    Column {
                        // 自动接单
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "自动接单",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "新订单自动接收处理",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = automaticOrderProcessing,
                                onCheckedChange = { automaticOrderProcessing = it }
                            )
                        }
                        
                        HorizontalDivider()
                        
                        // 订单打印
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "订单打印",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "新订单自动打印小票",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = automaticPrinting,
                                onCheckedChange = { automaticPrinting = it }
                            )
                        }
                        
                        HorizontalDivider()
                        
                        // 库存提醒
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "库存提醒",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "库存不足自动提醒",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = inventoryAlerts,
                                onCheckedChange = { inventoryAlerts = it }
                            )
                        }
                        
                        HorizontalDivider()
                        
                        // 定时备份
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "定时备份",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "每日自动备份数据",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = dailyBackup,
                                onCheckedChange = { dailyBackup = it }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAutomationDialog = false }) {
                        Text("确定")
                    }
                }
            )
        }
        
        // API配置对话框
        if (showApiDialog) {
            AlertDialog(
                onDismissRequest = { showApiDialog = false },
                title = { Text("WooCommerce API 配置") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = siteUrlInput,
                            onValueChange = { siteUrlInput = it },
                            label = { Text("站点URL") },
                            placeholder = { Text("https://your-site.com") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                        
                        OutlinedTextField(
                            value = consumerKeyInput,
                            onValueChange = { consumerKeyInput = it },
                            label = { Text("Consumer Key") },
                            placeholder = { Text("如：ck_1234...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("WooCommerce消费者密钥，通常以 ck_ 开头") },
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
                            label = { Text("Consumer Secret") },
                            placeholder = { Text("如：cs_5678...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("WooCommerce消费者密钥密文，通常以 cs_ 开头") },
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
                            label = { Text("轮询间隔 (秒)") },
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
                                text = "使用 WooCommerce Food 插件",
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
                                Text("测试连接中...")
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
                                        snackbarHostState.showSnackbar("请填写所有必填字段")
                                    }
                                }
                            },
                            enabled = !isTestingConnection
                        ) {
                            Text("保存并测试")
                        }
                        
                        TextButton(onClick = { showApiDialog = false }) {
                            Text("取消")
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