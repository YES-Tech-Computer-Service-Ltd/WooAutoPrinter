package com.example.wooauto.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    val autoPrintSettingsText = "自动打印设置" // stringResource(R.string.auto_print_settings) // Temporarily hardcoded
    
    // 自动化任务状态 (只保留自动打印相关的)
    val automaticPrintingState by viewModel.automaticPrinting.collectAsState()
    var automaticPrinting by remember { mutableStateOf(automaticPrintingState) }
    
    // 默认打印模板选择
    val selectedTemplateState by viewModel.defaultTemplateType.collectAsState()
    var selectedTemplate by remember { mutableStateOf(selectedTemplateState) }
    
    // 从ViewModel加载初始状态，并监听变化
    LaunchedEffect(automaticPrintingState) {
        automaticPrinting = automaticPrintingState
    }
    LaunchedEffect(selectedTemplateState) {
        selectedTemplate = selectedTemplateState
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // 标题
                    Text(
                        text = autoPrintSettingsText,
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
                    // 自动打印设置卡片
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
                            // 订单打印 (自动打印)
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
                                    onCheckedChange = { 
                                        automaticPrinting = it
                                    }
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
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // 保存按钮
                Button(
                    onClick = {
                        viewModel.updateAutomaticPrinting(automaticPrinting)
                        if(automaticPrinting) {
                            viewModel.updateDefaultTemplateType(selectedTemplate)
                        }
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(settingsSavedText)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Text(stringResource(id = R.string.save_settings))
                }
            }
        }
    }
} 