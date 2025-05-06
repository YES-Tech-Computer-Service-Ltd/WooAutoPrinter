package com.example.wooauto.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.R
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
    
    // 预先获取需要在非Composable上下文中使用的字符串资源
    val settingsSavedText = stringResource(R.string.settings_saved)
    
    // 自动化任务状态
    var automaticOrderProcessing by remember { mutableStateOf(true) }
    var automaticPrinting by remember { mutableStateOf(false) }
    var inventoryAlerts by remember { mutableStateOf(true) }
    var dailyBackup by remember { mutableStateOf(false) }
    
    // 默认打印模板选择
    var selectedTemplate by remember { mutableStateOf(TemplateType.FULL_DETAILS) }
    
    // 加载保存的设置
    LaunchedEffect(Unit) {
        // 从ViewModel加载设置
        viewModel.getDefaultTemplateType()?.let {
            selectedTemplate = it
        }
        
        viewModel.getAutomationSettings().let { settings ->
            automaticOrderProcessing = settings.automaticOrderProcessing
            automaticPrinting = settings.automaticPrinting
            // 可以添加其他设置的加载
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = 0.dp,
                    bottom = 0.dp,
                    start = 0.dp,
                    end = 0.dp
                )
        ) {
            // 外层Column，包含统一的水平内边距
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 0.dp)
            ) {
                // 增加顶部间距
                Spacer(modifier = Modifier.height(16.dp))
                
                // 顶部标题行，使用与其他页面一致的样式
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 返回按钮
                    IconButton(
                        onClick = { navController.navigateUp() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // 标题
                    Text(
                        text = stringResource(R.string.automation_tasks),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // 内容区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(vertical = 8.dp)
                ) {
                    // 自动接单设置
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 5.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.order_processing),
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
                                        text = stringResource(R.string.automatic_order_processing),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = stringResource(R.string.automatic_order_processing_desc),
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
                                        text = stringResource(R.string.automatic_printing),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = stringResource(R.string.automatic_printing_desc),
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
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 5.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.print_templates),
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
                                        Text(stringResource(R.string.full_details_template))
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
                                        Text(stringResource(R.string.delivery_template))
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
                                        Text(stringResource(R.string.kitchen_template))
                                    }
                                }
                            }
                        }
                    }
                    
                    // 其他自动化设置卡片
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 5.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.other_automation),
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
                                        text = stringResource(R.string.inventory_alerts),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = stringResource(R.string.inventory_alerts_desc),
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
                                        text = stringResource(R.string.daily_backup),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = stringResource(R.string.daily_backup_desc),
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
                                snackbarHostState.showSnackbar(settingsSavedText)
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
                        Text(stringResource(R.string.save_settings))
                    }
                }
            }
        }
    }
} 