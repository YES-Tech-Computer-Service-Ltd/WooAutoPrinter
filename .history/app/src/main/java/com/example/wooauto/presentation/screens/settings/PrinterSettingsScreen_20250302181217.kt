package com.example.wooauto.presentation.screens.settings

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.printer.PrinterStatus
import com.example.wooauto.presentation.navigation.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navController: NavController
) {
    val printerConfigs by viewModel.printerConfigs.collectAsState()
    val currentPrinterConfig by viewModel.currentPrinterConfig.collectAsState()
    val printerStatus by viewModel.printerStatus.collectAsState()
    val isPrinting by viewModel.isPrinting.collectAsState()
    
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var printerToDelete by remember { mutableStateOf<PrinterConfig?>(null) }

    // 页面加载时刷新打印机列表
    LaunchedEffect(key1 = Unit) {
        viewModel.loadPrinterConfigs()
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // 导航到添加新打印机页面
                    navController.navigate(Screen.PrinterDetails.printerDetailsRoute("new"))
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加打印机")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "打印机设置",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (printerConfigs.isEmpty()) {
                // 显示空状态
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Print,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "暂无打印机",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "点击下方按钮添加打印机",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                // 显示打印机列表
                LazyColumn {
                    items(printerConfigs) { printer ->
                        PrinterConfigItem(
                            printerConfig = printer,
                            isConnected = printerStatus == PrinterStatus.CONNECTED && currentPrinterConfig?.id == printer.id,
                            isPrinting = isPrinting && currentPrinterConfig?.id == printer.id,
                            onConnect = {
                                coroutineScope.launch {
                                    if (printerStatus == PrinterStatus.CONNECTED && currentPrinterConfig?.id == printer.id) {
                                        // 断开连接
                                        viewModel.disconnectPrinter(printer)
                                        snackbarHostState.showSnackbar("已断开打印机连接")
                                    } else {
                                        // 连接打印机
                                        val connected = viewModel.connectPrinter(printer)
                                        if (connected) {
                                            snackbarHostState.showSnackbar("打印机连接成功")
                                        } else {
                                            snackbarHostState.showSnackbar("打印机连接失败")
                                        }
                                    }
                                }
                            },
                            onEdit = {
                                // 导航到打印机详情页面
                                navController.navigate(Screen.printerDetailsRoute(printer.id))
                            },
                            onDelete = {
                                printerToDelete = printer
                                showDeleteDialog = true
                            },
                            onTestPrint = {
                                coroutineScope.launch {
                                    // 测试打印
                                    viewModel.testPrint(printer)
                                }
                            },
                            onSetDefault = { isDefault ->
                                if (isDefault) {
                                    // 设置为默认打印机
                                    val updatedConfig = printer.copy(isDefault = true)
                                    viewModel.savePrinterConfig(updatedConfig)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("已设置为默认打印机")
                                    }
                                }
                            }
                        )
                        
                        // 在每个项目之间添加分隔线
                        if (printer != printerConfigs.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
            
            // 删除确认对话框
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("删除打印机") },
                    text = { Text("确定要删除打印机 "${printerToDelete?.getDisplayName()}" 吗？此操作无法撤销。") },
                    confirmButton = {
                        Button(
                            onClick = {
                                printerToDelete?.let { printer ->
                                    viewModel.deletePrinterConfig(printer.id)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("打印机已删除")
                                    }
                                }
                                showDeleteDialog = false
                            }
                        ) {
                            Text("删除")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDeleteDialog = false }
                        ) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PrinterConfigItem(
    printerConfig: PrinterConfig,
    isConnected: Boolean,
    isPrinting: Boolean,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTestPrint: () -> Unit,
    onSetDefault: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 打印机名称和状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 默认打印机指示器
                if (printerConfig.isDefault) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "默认",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                // 打印机名称
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = printerConfig.getDisplayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = if (printerConfig.isDefault) "默认打印机" else printerConfig.type,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                // 连接状态指示器
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isPrinting -> MaterialTheme.colorScheme.tertiary
                                isConnected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape
                        )
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 连接/断开按钮
                TextButton(
                    onClick = onConnect
                ) {
                    if (isPrinting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (isConnected) "断开" else "连接",
                            color = if (isConnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 打印机地址和其他信息
            Text(
                text = "地址: ${printerConfig.address}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = "纸张宽度: ${printerConfig.paperWidth}mm",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 编辑按钮
                TextButton(
                    onClick = onEdit
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑")
                }
                
                // 测试打印按钮
                TextButton(
                    onClick = onTestPrint,
                    enabled = isConnected && !isPrinting
                ) {
                    Icon(
                        imageVector = Icons.Filled.Print,
                        contentDescription = "测试打印",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("测试打印")
                }
                
                // 删除按钮
                TextButton(
                    onClick = onDelete
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "删除",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // 设为默认打印机开关
            if (!printerConfig.isDefault) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSetDefault(true)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "设为默认打印机",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Switch(
                        checked = printerConfig.isDefault,
                        onCheckedChange = { onSetDefault(it) }
                    )
                }
            }
            
            // 自动打印开关
            if (printerConfig.isDefault) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val updatedConfig = printerConfig.copy(isAutoPrint = !printerConfig.isAutoPrint)
                            onSetDefault(true) // 调用更新回调
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "自动打印新订单",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Switch(
                        checked = printerConfig.isAutoPrint,
                        onCheckedChange = {
                            val updatedConfig = printerConfig.copy(isAutoPrint = it)
                            onSetDefault(true) // 调用更新回调
                        }
                    )
                }
            }
        }
    }
} 