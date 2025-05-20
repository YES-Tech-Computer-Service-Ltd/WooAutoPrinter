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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.wooauto.presentation.navigation.Screen
import com.example.wooauto.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintTemplatesScreen(navController: NavController) {
    // 模拟的模板数据
    val templates = listOf(
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
    
    // 记录选中的模板
    val selectedTemplate = remember { mutableStateOf(templates.first()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.printer_templates)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // 导航到自定义模板创建页面
                    navController.navigate(Screen.TemplatePreview.templatePreviewRoute("new"))
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Template"
                )
            }
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
                        // 跳转到模板预览页面
                        navController.navigate(Screen.TemplatePreview.templatePreviewRoute(template.id))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintTemplatesDialogContent(
    onClose: () -> Unit
) {
    // 模拟的模板数据
    val templates = listOf(
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
    
    // 记录选中的模板
    val selectedTemplate = remember { mutableStateOf(templates.first()) }
    
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
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        // 弹出式对话框中暂时不处理新增模板
                        // 可以将此处改为通知上层组件处理
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Template"
                    )
                }
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
                            // 在对话框中选择模板后的逻辑，可以传给父组件
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TemplateItem(
    template: PrintTemplate,
    isSelected: Boolean,
    onClick: () -> Unit
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
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit",
                modifier = Modifier.clickable {
                    // TODO: 编辑模板的逻辑
                },
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// 模板类型枚举
enum class TemplateType {
    FULL_DETAILS,  // 完整订单详情
    DELIVERY,      // 外卖信息
    KITCHEN        // 厨房订单信息
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