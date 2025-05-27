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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wooauto.R
import com.example.wooauto.domain.templates.TemplateType
import com.example.wooauto.presentation.screens.templatePreview.TemplateConfigViewModel
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
    
    // 记录当前选中的模板ID（用于支持自定义模板）
    var selectedTemplateId by remember { mutableStateOf<String?>(null) }
    
    // 添加TemplateConfigViewModel来获取所有模板
    val templateConfigViewModel: TemplateConfigViewModel = hiltViewModel()
    val allConfigs by templateConfigViewModel.allConfigs.collectAsState()
    val isLoadingTemplates by templateConfigViewModel.isLoading.collectAsState()
    
    // 准备显示的模板选项（包含模板ID信息）
    val availableTemplates = remember(allConfigs) {
        val defaultTemplates = listOf(
            Triple("full_details", TemplateType.FULL_DETAILS, 
                LocalContext.current.getString(R.string.auto_print_template_full_details)),
            Triple("delivery", TemplateType.DELIVERY, 
                LocalContext.current.getString(R.string.auto_print_template_delivery)), 
            Triple("kitchen", TemplateType.KITCHEN, 
                LocalContext.current.getString(R.string.auto_print_template_kitchen))
        )
        
        val customTemplates = allConfigs
            .filter { it.templateId.startsWith("custom_") }
            .map { config ->
                Triple(config.templateId, TemplateType.FULL_DETAILS, config.templateName)
            }
        
        defaultTemplates + customTemplates
    }
    
    LaunchedEffect(automaticPrintingState) {
        automaticPrinting = automaticPrintingState
    }
    LaunchedEffect(selectedTemplateState) {
        selectedTemplate = selectedTemplateState
        // 根据TemplateType确定对应的默认模板ID（向后兼容）
        selectedTemplateId = when (selectedTemplateState) {
            TemplateType.FULL_DETAILS -> "full_details"
            TemplateType.DELIVERY -> "delivery"
            TemplateType.KITCHEN -> "kitchen"
        }
    }
    
    // 加载所有模板配置和当前选中的自动打印模板
    LaunchedEffect(Unit) {
        templateConfigViewModel.loadAllConfigs()
        // 尝试加载保存的自动打印模板ID
        try {
            val savedTemplateId = viewModel.settingsRepository.getDefaultAutoPrintTemplateId()
            if (savedTemplateId != null) {
                selectedTemplateId = savedTemplateId
                // 如果是自定义模板，将selectedTemplate设为FULL_DETAILS
                if (savedTemplateId.startsWith("custom_")) {
                    selectedTemplate = TemplateType.FULL_DETAILS
                } else {
                    // 根据模板ID设置对应的TemplateType
                    selectedTemplate = when (savedTemplateId) {
                        "full_details" -> TemplateType.FULL_DETAILS
                        "delivery" -> TemplateType.DELIVERY
                        "kitchen" -> TemplateType.KITCHEN
                        else -> TemplateType.FULL_DETAILS
                    }
                }
            }
        } catch (e: Exception) {
            // 如果加载失败，使用默认值
        }
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
                        
                        if (isLoadingTemplates) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            availableTemplates.forEach { (templateId, templateType, templateName) ->
                                TemplateTypeRow(
                                    templateId = templateId,
                                    templateType = templateType,
                                    templateName = templateName,
                                    currentSelectedType = selectedTemplate,
                                    currentSelectedId = selectedTemplateId
                                ) { id, type -> 
                                    selectedTemplateId = id
                                    selectedTemplate = type
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                Button(
                    onClick = {
                        viewModel.updateAutomaticPrinting(automaticPrinting)
                        if(automaticPrinting && selectedTemplateId != null) { 
                            // 使用新的方法同时保存模板ID和类型
                            viewModel.updateDefaultAutoPrintTemplate(selectedTemplateId!!, selectedTemplate)
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
    templateId: String,
    templateType: TemplateType,
    templateName: String,
    currentSelectedType: TemplateType,
    currentSelectedId: String?,
    onSelected: (String, TemplateType) -> Unit
) {
    // 判断是否选中：对于自定义模板比较ID，对于默认模板比较类型
    val isSelected = if (templateId.startsWith("custom_")) {
        currentSelectedId == templateId
    } else {
        currentSelectedType == templateType && currentSelectedId == templateId
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelected(templateId, templateType) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = { onSelected(templateId, templateType) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(templateName)
    }
} 