package com.example.wooauto.presentation.screens.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.example.wooauto.presentation.navigation.Screen
import com.example.wooauto.R
import com.example.wooauto.presentation.screens.templatePreview.TemplatePreviewDialogContent
import com.example.wooauto.presentation.screens.templatePreview.TemplateConfigViewModel
import com.example.wooauto.domain.templates.TemplateType
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintTemplatesScreen(navController: NavController) {
    // 直接使用对话框模式显示模板列表
    var showTemplatesDialog by remember { mutableStateOf(true) }
    
    if (showTemplatesDialog) {
        Dialog(
            onDismissRequest = { 
                showTemplatesDialog = false
                navController.navigateUp() 
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            PrintTemplatesDialogContent(
                onClose = { 
                    showTemplatesDialog = false
                    navController.navigateUp()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintTemplatesDialogContent(
    onClose: () -> Unit,
    viewModel: TemplateConfigViewModel = hiltViewModel()
) {
    // 从ViewModel获取所有模板配置
    val allConfigs by viewModel.allConfigs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // 将TemplateConfig转换为PrintTemplate用于显示
    val templates = remember(allConfigs) {
        val defaultTemplates = listOf(
            PrintTemplate(
                id = "full_details", 
                name = "Full Order Details", 
                description = "Complete order information including all customer and item details", 
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                isDefault = true,
                templateType = TemplateType.FULL_DETAILS
            ),
            PrintTemplate(
                id = "delivery", 
                name = "Delivery Receipt", 
                description = "Delivery information with customer address and order items", 
                icon = Icons.Default.Fastfood,
                isDefault = false,
                templateType = TemplateType.DELIVERY
            ),
            PrintTemplate(
                id = "kitchen", 
                name = "Kitchen Order", 
                description = "Simplified receipt for kitchen staff showing only items and time", 
                icon = Icons.Default.Restaurant,
                isDefault = false,
                templateType = TemplateType.KITCHEN
            )
        )
        
        // 添加自定义模板
        val customTemplates = allConfigs
            .filter { it.templateId.startsWith("custom_") }
            .map { config ->
                PrintTemplate(
                    id = config.templateId,
                    name = config.templateName,
                    description = "自定义模板 - ${config.getEnabledFieldCount()} 个已启用字段",
                    icon = Icons.Default.Description,
                    isDefault = false,
                    templateType = config.templateType
                )
            }
        
        defaultTemplates + customTemplates
    }
    
    // 加载所有模板配置
    LaunchedEffect(Unit) {
        viewModel.loadAllConfigs()
    }
    
    // 记录选中的模板
    val selectedTemplate = remember { mutableStateOf(templates.first()) }
    
    // 是否显示模板预览对话框
    var showTemplatePreviewDialog by remember { mutableStateOf(false) }
    // 当前预览的模板ID
    var previewTemplateId by remember { mutableStateOf("") }
    
    // 是否显示创建新模板对话框
    var showCreateTemplateDialog by remember { mutableStateOf(false) }
    // 新模板名称
    var newTemplateName by remember { mutableStateOf("") }
    
    // 删除确认对话框相关状态
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var templateToDelete by remember { mutableStateOf<PrintTemplate?>(null) }
    
    // 创建新模板对话框
    if (showCreateTemplateDialog) {
        AlertDialog(
            onDismissRequest = { 
                showCreateTemplateDialog = false
                newTemplateName = ""
            },
            title = { Text("创建新模板") },
            text = {
                Column {
                    Text(
                        text = "请为您的自定义模板命名：",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = newTemplateName,
                        onValueChange = { newTemplateName = it },
                        label = { Text("模板名称") },
                        placeholder = { Text("例如：我的自定义模板") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTemplateName.isNotBlank()) {
                            // 创建新模板，使用时间戳作为唯一ID
                            val newTemplateId = "custom_${System.currentTimeMillis()}"
                            previewTemplateId = newTemplateId
                            showCreateTemplateDialog = false
                            showTemplatePreviewDialog = true
                            // 保存模板名称，传递给预览页面
                        }
                    },
                    enabled = newTemplateName.isNotBlank()
                ) {
                    Text("创建")
                }
            },
                            dismissButton = {
                TextButton(
                    onClick = { 
                        showCreateTemplateDialog = false
                        newTemplateName = ""
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
    
    // 删除确认对话框
    if (showDeleteConfirmDialog && templateToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirmDialog = false
                templateToDelete = null
            },
            title = { Text("删除模板") },
            text = {
                Text(
                    text = "确定要删除模板 \"${templateToDelete?.name}\" 吗？\n\n此操作无法撤销。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        templateToDelete?.let { template ->
                            // 执行删除操作
                            viewModel.deleteCustomTemplate(template.id)
                            // 如果删除的是当前选中的模板，重置选择
                            if (selectedTemplate.value.id == template.id) {
                                selectedTemplate.value = templates.first()
                            }
                        }
                        showDeleteConfirmDialog = false
                        templateToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showDeleteConfirmDialog = false
                        templateToDelete = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
    
    if (showTemplatePreviewDialog) {
        Dialog(
            onDismissRequest = { showTemplatePreviewDialog = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            TemplatePreviewDialogContent(
                templateId = previewTemplateId,
                customTemplateName = if (previewTemplateId.startsWith("custom_")) newTemplateName.takeIf { it.isNotBlank() } else null,
                onClose = { 
                    // 只关闭模板预览对话框，不关闭模板列表对话框
                    showTemplatePreviewDialog = false
                    // 清空模板名称
                    newTemplateName = ""
                }
            )
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
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.printer_templates)) },
                    navigationIcon = {
                        IconButton(onClick = { onClose() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.close)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(templates) { template ->
                    TemplateItem(
                        template = template, 
                        isSelected = template.id == selectedTemplate.value.id,
                        onClick = {
                            selectedTemplate.value = template
                            // 显示模板预览对话框
                            previewTemplateId = template.id
                            showTemplatePreviewDialog = true
                        },
                        onEdit = {
                            // 编辑模板：打开模板预览/编辑页面
                            previewTemplateId = template.id
                            showTemplatePreviewDialog = true
                        },
                        onDelete = if (template.id.startsWith("custom_")) {
                            // 只有自定义模板才有删除功能
                            {
                                templateToDelete = template
                                showDeleteConfirmDialog = true
                            }
                        } else null // 默认模板没有删除功能
                    )
                }
                
                // 添加新模板按钮
                item {
                    AddTemplateButton(
                        onClick = {
                            showCreateTemplateDialog = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AddTemplateButton(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = "创建新模板",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun TemplateItem(
    template: PrintTemplate,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: (() -> Unit)? = null // 只有自定义模板才有删除功能
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = template.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            // 编辑按钮 - 修复点击事件冲突
            IconButton(
                onClick = {
                    // 阻止事件冒泡到Card，直接调用编辑操作
                    onEdit()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Template",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // 删除按钮 - 只为自定义模板显示
            if (onDelete != null) {
                IconButton(
                    onClick = {
                        // 阻止事件冒泡到Card，直接调用删除操作
                        onDelete()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete Custom Template",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// 打印模板数据类
data class PrintTemplate(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector = Icons.Default.Description,
    val isDefault: Boolean = false,
    val templateType: TemplateType = TemplateType.FULL_DETAILS
) 