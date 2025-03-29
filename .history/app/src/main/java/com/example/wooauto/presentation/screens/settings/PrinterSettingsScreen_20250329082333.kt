package com.example.wooauto.presentation.screens.settings

import android.bluetooth.BluetoothAdapter
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.R
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.printer.PrinterStatus
import com.example.wooauto.domain.printer.PrinterDevice
import com.example.wooauto.domain.printer.PrinterBrand
import com.example.wooauto.data.printer.BluetoothPrinterManager
import com.example.wooauto.presentation.navigation.Screen
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext

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
    val isScanning by viewModel.isScanning.collectAsState()
    val availablePrinters by viewModel.availablePrinters.collectAsState()
    val connectionErrorMessage by viewModel.connectionErrorMessage.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    
    // 获取PrinterManager实例
    val printerManager = viewModel.getPrinterManager()
    
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var printerToDelete by remember { mutableStateOf<PrinterConfig?>(null) }
    
    // 添加蓝牙设备扫描对话框状态
    var showScanDialog by remember { mutableStateOf(false) }

    // 页面加载时刷新打印机列表
    LaunchedEffect(key1 = Unit) {
        viewModel.loadPrinterConfigs()
    }
    
    // 监听连接状态变化，自动关闭扫描对话框
    LaunchedEffect(printerStatus, connectionErrorMessage, isConnecting) {
        if (printerStatus == PrinterStatus.CONNECTED && showScanDialog) {
            // 连接成功时关闭对话框
            showScanDialog = false
            snackbarHostState.showSnackbar("打印机连接成功")
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.printer_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // 只保留添加新打印机按钮
            FloatingActionButton(
                onClick = {
                    // 导航到添加新打印机页面
                    navController.navigate(Screen.PrinterDetails.printerDetailsRoute("new"))
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.add_printer))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // 打印模板设置卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // 导航到打印模板选择页面
                            Log.d("PrinterSettingsScreen", "点击了打印模板卡片，导航到: ${Screen.PrintTemplates.route}")
                            navController.navigate(Screen.PrintTemplates.route)
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(id = R.string.printer_templates),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.printer_templates_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(id = R.string.edit),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // 显示错误消息（如果有）
            if (connectionErrorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Text(
                            text = connectionErrorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        
                        IconButton(
                            onClick = { viewModel.clearConnectionError() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(id = R.string.close),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            
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
                            text = stringResource(id = R.string.no_printers),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = stringResource(id = R.string.add_printer_hint),
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
                                        snackbarHostState.showSnackbar(stringResource(id = R.string.printer_disconnected))
                                    } else {
                                        // 连接打印机
                                        val connected = viewModel.connectPrinter(printer)
                                        if (connected) {
                                            snackbarHostState.showSnackbar(stringResource(id = R.string.printer_connected_success))
                                        } else {
                                            snackbarHostState.showSnackbar(stringResource(id = R.string.printer_connection_failed))
                                        }
                                    }
                                }
                            },
                            onEdit = {
                                // 导航到打印机详情页面
                                navController.navigate(Screen.PrinterDetails.printerDetailsRoute(printer.id))
                            },
                            onDelete = {
                                printerToDelete = printer
                                showDeleteDialog = true
                            },
                            onTestPrint = {
                                coroutineScope.launch {
                                    // 显示连接中提示
                                    snackbarHostState.showSnackbar(stringResource(id = R.string.connecting_printer))
                                    
                                    // 测试打印
                                    try {
                                        val success = viewModel.testPrint(printer)
                                        if (success) {
                                            snackbarHostState.showSnackbar(stringResource(id = R.string.test_print_success))
                                        } else {
                                            val errorMsg = viewModel.connectionErrorMessage.value ?: stringResource(id = R.string.test_print_failed)
                                            snackbarHostState.showSnackbar(errorMsg)
                                        }
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar(stringResource(id = R.string.printer_error, e.message ?: ""))
                                    }
                                }
                            },
                            onSetDefault = { isDefault ->
                                if (isDefault) {
                                    // 设置为默认打印机
                                    val updatedConfig = printer.copy(isDefault = true)
                                    viewModel.savePrinterConfig(updatedConfig)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(stringResource(id = R.string.set_as_default_success))
                                    }
                                }
                            }
                        )
                        
                        // 在每个项目之间添加分隔线
                        if (printer != printerConfigs.last()) {
                            Divider(
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
                    title = { Text(stringResource(id = R.string.delete_printer)) },
                    text = { Text(stringResource(id = R.string.delete_printer_confirm, printerToDelete?.getDisplayName() ?: "")) },
                    confirmButton = {
                        Button(
                            onClick = {
                                printerToDelete?.let { printer ->
                                    viewModel.deletePrinterConfig(printer.id)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(stringResource(id = R.string.printer_deleted))
                                    }
                                }
                                showDeleteDialog = false
                            }
                        ) {
                            Text(stringResource(id = R.string.delete))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDeleteDialog = false }
                        ) {
                            Text(stringResource(id = R.string.cancel))
                        }
                    }
                )
            }
            
            // 蓝牙设备扫描对话框
            if (showScanDialog) {
                AlertDialog(
                    onDismissRequest = { showScanDialog = false },
                    title = { Text(stringResource(id = R.string.bluetooth_devices)) },
                    text = { 
                        Column {
                            if (isScanning) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(id = R.string.scanning_bluetooth))
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (availablePrinters.isEmpty()) 
                                                 stringResource(id = R.string.no_bluetooth_devices) 
                                               else 
                                                 stringResource(id = R.string.select_bluetooth_device),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    
                                    IconButton(
                                        onClick = { viewModel.scanPrinters() }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = stringResource(id = R.string.rescan)
                                        )
                                    }
                                }
                            }
                            
                            // 提示说明 - 特别是对Android 7用户
                            Text(
                                text = stringResource(id = R.string.bluetooth_tip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            if (availablePrinters.isEmpty() && !isScanning) {
                                Text(
                                    text = stringResource(id = R.string.no_bluetooth_tip),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.height(300.dp)
                                ) {
                                    items(availablePrinters) { device ->
                                        val isPaired = isPairedDevice(device)
                                        
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    // 尝试连接此设备
                                                    if (!isConnecting) {
                                                        viewModel.connectPrinter(device)
                                                        // 如果连接成功会自动关闭对话框
                                                        if (device.status == PrinterStatus.CONNECTED) {
                                                            showScanDialog = false
                                                        }
                                                    }
                                                }
                                                .padding(vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 设备状态指示器
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .background(
                                                        when (device.status) {
                                                            PrinterStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                                                            PrinterStatus.ERROR -> MaterialTheme.colorScheme.error
                                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                                        },
                                                        shape = CircleShape
                                                    )
                                            )
                                            
                                            Spacer(modifier = Modifier.width(16.dp))
                                            
                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = device.name,
                                                        style = MaterialTheme.typography.titleMedium
                                                    )
                                                    if (isPaired) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = stringResource(id = R.string.device_paired),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = device.address,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            
                                            if (device.status == PrinterStatus.CONNECTED) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = stringResource(id = R.string.connected),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        
                                        if (device != availablePrinters.last()) {
                                            Divider(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { showScanDialog = false }
                        ) {
                            Text(stringResource(id = R.string.close))
                        }
                    },
                    dismissButton = {
                        if (isScanning) {
                            TextButton(
                                onClick = { 
                                    // 停止扫描
                                    if (printerManager is BluetoothPrinterManager) {
                                        printerManager.stopDiscovery()
                                    }
                                    viewModel.stopScanning()
                                }
                            ) {
                                Text(stringResource(id = R.string.stop_scanning))
                            }
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
                        text = stringResource(id = R.string.is_default_printer),
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
                            text = if (isConnected) stringResource(id = R.string.disconnect) else stringResource(id = R.string.connect),
                            color = if (isConnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 打印机地址和其他信息
            Text(
                text = stringResource(id = R.string.printer_address_label, printerConfig.address),
                style = MaterialTheme.typography.bodyMedium
            )
            
            if (printerConfig.brand != null && printerConfig.brand != PrinterBrand.UNKNOWN) {
                Text(
                    text = stringResource(id = R.string.printer_brand_label, printerConfig.brand.displayName, printerConfig.brand.commandLanguage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
            
            Text(
                text = stringResource(id = R.string.printer_paper_width, printerConfig.paperWidth),
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 编辑按钮
                IconButton(
                    onClick = onEdit
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(id = R.string.edit),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                // 测试打印按钮
                TextButton(
                    onClick = onTestPrint,
                    enabled = isConnected && !isPrinting
                ) {
                    Icon(
                        imageVector = Icons.Filled.Print,
                        contentDescription = stringResource(id = R.string.test_print),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(id = R.string.test_print_action))
                }
                
                // 删除按钮
                TextButton(
                    onClick = onDelete
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(id = R.string.delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(id = R.string.delete),
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
                            onSetDefault(!printerConfig.isDefault)
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
                        onCheckedChange = { onSetDefault(!printerConfig.isDefault) }
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
                            onSetDefault(updatedConfig.isDefault)
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
                            onSetDefault(updatedConfig.isDefault)
                        }
                    )
                }
            }
        }
    }
}

/**
 * 检查设备是否已配对 (不需要Composable上下文的版本)
 */
private fun isPairedDevice(device: PrinterDevice): Boolean {
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return false
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12及以上需要特殊权限处理，但这里我们直接尝试读取已配对设备
            // 如果失败会抛出异常，由catch块处理
        }
        
        @Suppress("DEPRECATION")
        val pairedDevices = bluetoothAdapter.bondedDevices
        return pairedDevices?.any { it.address == device.address } ?: false
    } catch (e: Exception) {
        Log.e("PrinterSettingsScreen", "检查设备配对状态失败", e)
        return false
    }
} 