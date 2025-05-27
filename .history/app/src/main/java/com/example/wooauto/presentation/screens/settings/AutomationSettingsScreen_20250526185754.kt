package com.example.wooauto.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wooauto.R
import com.example.wooauto.domain.templates.TemplateType
import kotlinx.coroutines.launch

/**
 * 自动化设置屏幕
 * 允许用户配置自动处理订单、自动打印等功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationSettingsDialogContent(
    viewModel: SettingsViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    val settingsSavedText = stringResource(R.string.settings_saved)
    
    val automaticPrintingState by viewModel.automaticPrinting.collectAsState()
    var automaticPrinting by remember { mutableStateOf(automaticPrintingState) }
    
    val selectedTemplateState by viewModel.defaultTemplateType.collectAsState()
    var selectedTemplate by remember { mutableStateOf(selectedTemplateState) }
    
    // 添加TemplateConfigViewModel来获取所有模板
    val templateConfigViewModel: TemplateConfigViewModel = hiltViewModel()
    val allConfigs by templateConfigViewModel.allConfigs.collectAsState()
    val isLoadingTemplates by templateConfigViewModel.isLoading.collectAsState()
    
    // 准备显示的模板选项
    val availableTemplates = remember(allConfigs) {
        val defaultTemplates = listOf(
            TemplateType.FULL_DETAILS to "完整订单详情",
            TemplateType.DELIVERY to "外卖单据", 
            TemplateType.KITCHEN to "厨房订单"
        )
        
        val customTemplates = allConfigs
            .filter { it.templateId.startsWith("custom_") }
            .map { config ->
                TemplateType.FULL_DETAILS to config.templateName // 自定义模板都使用FULL_DETAILS类型，但显示自定义名称
            }
        
        defaultTemplates + customTemplates
    }
    
    LaunchedEffect(automaticPrintingState) {
        automaticPrinting = automaticPrintingState
    }
    LaunchedEffect(selectedTemplateState) {
        selectedTemplate = selectedTemplateState
    }
    
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.auto_print_settings)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
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
                    
                    if (automaticPrinting) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = stringResource(R.string.print_templates),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                        )
                        
                        TemplateTypeRow(TemplateType.FULL_DETAILS, selectedTemplate) { selectedTemplate = it }
                        TemplateTypeRow(TemplateType.DELIVERY, selectedTemplate) { selectedTemplate = it }
                        TemplateTypeRow(TemplateType.KITCHEN, selectedTemplate) { selectedTemplate = it }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
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
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp)
                ) {
                    Text(stringResource(id = R.string.save_settings))
                }
            }
        }
    }
}

@Composable
private fun TemplateTypeRow(
    templateType: TemplateType,
    currentSelectedType: TemplateType,
    onSelected: (TemplateType) -> Unit
) {
    val textResId = when (templateType) {
        TemplateType.FULL_DETAILS -> R.string.full_details_template
        TemplateType.DELIVERY -> R.string.delivery_template
        TemplateType.KITCHEN -> R.string.kitchen_template
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelected(templateType) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = currentSelectedType == templateType,
            onClick = { onSelected(templateType) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(textResId))
    }
} 