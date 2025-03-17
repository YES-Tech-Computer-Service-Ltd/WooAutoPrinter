package com.example.wooauto.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.domain.templates.TemplateType
import kotlinx.coroutines.launch

/**
 * 自动化设置屏幕
 * 允许用户配置自动处理订单、自动打印等功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navController: NavController
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
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
    
    // 加载保存的设置
    LaunchedEffect(Unit) {
        // 从ViewModel加载设置
        automaticOrderProcessing = viewModel.getAutomaticOrderProcessingEnabled() ?: true
        automaticPrinting = viewModel.getAutomaticPrintingEnabled() ?: false
        // 其他设置也可以类似加载
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自动化任务设置") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            // 自动接单设置
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "订单处理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 自动接单
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
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
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // 订单打印
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
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
                }
            }
            
            // 当自动打印开启时，显示模板选择
            if (automaticPrinting) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "打印模板",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 完整订单模板
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedTemplate = TemplateType.FULL_DETAILS },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTemplate == TemplateType.FULL_DETAILS,
                                onClick = { selectedTemplate = TemplateType.FULL_DETAILS }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("完整订单详情")
                                Text(
                                    text = "包含所有订单信息",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // 配送信息模板
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedTemplate = TemplateType.DELIVERY },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTemplate == TemplateType.DELIVERY,
                                onClick = { selectedTemplate = TemplateType.DELIVERY }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("配送信息")
                                Text(
                                    text = "突出显示配送信息和菜品",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // 厨房订单模板
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedTemplate = TemplateType.KITCHEN },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTemplate == TemplateType.KITCHEN,
                                onClick = { selectedTemplate = TemplateType.KITCHEN }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("厨房订单")
                                Text(
                                    text = "仅包含菜品和下单时间",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // 其他自动化设置卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "其他自动化",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 库存提醒
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
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
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // 定时备份
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
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
            }
            
            // 保存设置按钮
            Button(
                onClick = {
                    // 保存设置
                    viewModel.saveAutomationSettings(
                        automaticOrderProcessing = automaticOrderProcessing,
                        automaticPrinting = automaticPrinting,
                        inventoryAlerts = inventoryAlerts,
                        dailyBackup = dailyBackup,
                        defaultTemplateType = selectedTemplate
                    )
                    
                    // 显示保存确认
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("自动化设置已保存")
                    }
                    
                    // 通知服务重启轮询，确保设置立即生效
                    viewModel.notifyServiceToRestartPolling()
                    
                    // 返回上一页
                    navController.popBackStack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                Text("保存设置")
            }
        }
    }
} 