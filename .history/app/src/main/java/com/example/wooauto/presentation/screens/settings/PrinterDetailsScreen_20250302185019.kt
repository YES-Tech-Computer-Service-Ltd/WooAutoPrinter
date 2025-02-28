package com.example.wooauto.presentation.screens.settings

import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.printer.PrinterDevice
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterDetailsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navController: NavController,
    printerId: String?
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    
    val availablePrinters by viewModel.availablePrinters.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    
    // 判断是添加新打印机还是编辑现有打印机
    val isNewPrinter = printerId == null || printerId == "new"
    var printerConfig by remember {
        mutableStateOf(
            if (isNewPrinter) {
                PrinterConfig(
                    id = UUID.randomUUID().toString(),
                    name = "",
                    address = "",
                    type = PrinterConfig.PRINTER_TYPE_BLUETOOTH
                )
            } else {
                viewModel.getPrinterConfig(printerId!!) ?: PrinterConfig(
                    id = UUID.randomUUID().toString(),
                    name = "",
                    address = "",
                    type = PrinterConfig.PRINTER_TYPE_BLUETOOTH
                )
            }
        )
    }
    
    // 表单状态
    var name by remember { mutableStateOf(printerConfig.name) }
    var address by remember { mutableStateOf(printerConfig.address) }
    var type by remember { mutableStateOf(printerConfig.type) }
    var paperWidth by remember { mutableStateOf(printerConfig.paperWidth.toString()) }
    var isDefault by remember { mutableStateOf(printerConfig.isDefault) }
    var isAutoPrint by remember { mutableStateOf(printerConfig.isAutoPrint) }
    var printCopies by remember { mutableStateOf(printerConfig.printCopies.toString()) }
    
    // 打印设置
    var printStoreInfo by remember { mutableStateOf(printerConfig.printStoreInfo) }
    var printCustomerInfo by remember { mutableStateOf(printerConfig.printCustomerInfo) }
    var printItemDetails by remember { mutableStateOf(printerConfig.printItemDetails) }
    var printOrderNotes by remember { mutableStateOf(printerConfig.printOrderNotes) }
    var printFooter by remember { mutableStateOf(printerConfig.printFooter) }
    
    var showScanDialog by remember { mutableStateOf(false) }
    
    // 页面加载时扫描打印机列表
    LaunchedEffect(key1 = Unit) {
        if (isNewPrinter) {
            viewModel.scanPrinters()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewPrinter) "添加打印机" else "编辑打印机") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
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
            FloatingActionButton(
                onClick = {
                    // 验证必填字段
                    if (name.isBlank() || address.isBlank()) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("请填写打印机名称和地址")
                        }
                        return@FloatingActionButton
                    }
                    
                    // 保存打印机配置
                    val updatedConfig = printerConfig.copy(
                        name = name,
                        address = address,
                        type = type,
                        paperWidth = paperWidth.toIntOrNull() ?: PrinterConfig.PAPER_WIDTH_57MM,
                        isDefault = isDefault,
                        isAutoPrint = isAutoPrint,
                        printCopies = printCopies.toIntOrNull() ?: 1,
                        printStoreInfo = printStoreInfo,
                        printCustomerInfo = printCustomerInfo,
                        printItemDetails = printItemDetails,
                        printOrderNotes = printOrderNotes,
                        printFooter = printFooter
                    )
                    
                    viewModel.savePrinterConfig(updatedConfig)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("打印机配置已保存")
                    }
                    
                    // 返回上一页
                    navController.popBackStack()
                }
            ) {
                Icon(Icons.Default.Save, contentDescription = "保存")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            // 打印机基本信息
            Text(
                text = "基本信息",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 打印机名称
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("打印机名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 打印机地址
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("打印机地址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (type == PrinterConfig.PRINTER_TYPE_BLUETOOTH) {
                        IconButton(
                            onClick = { showScanDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.BluetoothSearching,
                                contentDescription = "扫描蓝牙设备"
                            )
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 打印机类型
            Text(
                text = "打印机类型",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = type == PrinterConfig.PRINTER_TYPE_BLUETOOTH,
                    onClick = { type = PrinterConfig.PRINTER_TYPE_BLUETOOTH }
                )
                Text(
                    text = "蓝牙打印机",
                    modifier = Modifier
                        .clickable { type = PrinterConfig.PRINTER_TYPE_BLUETOOTH }
                        .padding(start = 4.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                RadioButton(
                    selected = type == PrinterConfig.PRINTER_TYPE_WIFI,
                    onClick = { type = PrinterConfig.PRINTER_TYPE_WIFI }
                )
                Text(
                    text = "网络打印机",
                    modifier = Modifier
                        .clickable { type = PrinterConfig.PRINTER_TYPE_WIFI }
                        .padding(start = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 纸张宽度
            Text(
                text = "纸张宽度",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = paperWidth.toIntOrNull() == PrinterConfig.PAPER_WIDTH_57MM,
                    onClick = { paperWidth = PrinterConfig.PAPER_WIDTH_57MM.toString() }
                )
                Text(
                    text = "57mm",
                    modifier = Modifier
                        .clickable { paperWidth = PrinterConfig.PAPER_WIDTH_57MM.toString() }
                        .padding(start = 4.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                RadioButton(
                    selected = paperWidth.toIntOrNull() == PrinterConfig.PAPER_WIDTH_80MM,
                    onClick = { paperWidth = PrinterConfig.PAPER_WIDTH_80MM.toString() }
                )
                Text(
                    text = "80mm",
                    modifier = Modifier
                        .clickable { paperWidth = PrinterConfig.PAPER_WIDTH_80MM.toString() }
                        .padding(start = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 打印份数
            OutlinedTextField(
                value = printCopies,
                onValueChange = { 
                    if (it.isEmpty() || it.toIntOrNull() != null) {
                        printCopies = it 
                    }
                },
                label = { Text("打印份数") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 打印内容设置
            Text(
                text = "打印内容设置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 打印店铺信息
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "打印店铺信息",
                    modifier = Modifier.weight(1f)
                )
                
                Switch(
                    checked = printStoreInfo,
                    onCheckedChange = { printStoreInfo = it }
                )
            }
            
            // 打印客户信息
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "打印客户信息",
                    modifier = Modifier.weight(1f)
                )
                
                Switch(
                    checked = printCustomerInfo,
                    onCheckedChange = { printCustomerInfo = it }
                )
            }
            
            // 打印订单详情
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "打印商品详情",
                    modifier = Modifier.weight(1f)
                )
                
                Switch(
                    checked = printItemDetails,
                    onCheckedChange = { printItemDetails = it }
                )
            }
            
            // 打印订单备注
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "打印订单备注",
                    modifier = Modifier.weight(1f)
                )
                
                Switch(
                    checked = printOrderNotes,
                    onCheckedChange = { printOrderNotes = it }
                )
            }
            
            // 打印页脚信息
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "打印页脚信息",
                    modifier = Modifier.weight(1f)
                )
                
                Switch(
                    checked = printFooter,
                    onCheckedChange = { printFooter = it }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 其他设置
            Text(
                text = "其他设置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 是否设为默认打印机
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "设为默认打印机",
                    modifier = Modifier.weight(1f)
                )
                
                Switch(
                    checked = isDefault,
                    onCheckedChange = { isDefault = it }
                )
            }
            
            // 自动打印新订单
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "自动打印新订单",
                    modifier = Modifier.weight(1f)
                )
                
                Switch(
                    checked = isAutoPrint,
                    onCheckedChange = { isAutoPrint = it }
                )
            }
            
            // 添加额外的空间确保浮动按钮不会遮挡内容
            Spacer(modifier = Modifier.height(80.dp))
        }
        
        // 扫描蓝牙设备对话框
        if (showScanDialog) {
            AlertDialog(
                onDismissRequest = { showScanDialog = false },
                title = { Text("选择蓝牙打印机") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        if (isScanning) {
                            // 显示加载指示器
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator()
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Text("正在扫描蓝牙设备...")
                                }
                            }
                        } else if (availablePrinters.isEmpty()) {
                            // 显示空状态
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("未找到蓝牙设备")
                                    Text(
                                        "请确保蓝牙已开启并已与打印机配对",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Button(
                                        onClick = { viewModel.scanPrinters() }
                                    ) {
                                        Text("重新扫描")
                                    }
                                }
                            }
                        } else {
                            Text(
                                "请选择一个已配对的蓝牙打印机:",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // 设备列表
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                            ) {
                                items(availablePrinters) { printer ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                name = printer.name
                                                address = printer.address
                                                showScanDialog = false
                                            }
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = printer.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            
                                            Text(
                                                text = printer.address,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "选择",
                                            tint = if (printer.address == address) 
                                                MaterialTheme.colorScheme.primary
                                            else 
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.0f)
                                        )
                                    }
                                    
                                    if (printer != availablePrinters.last()) {
                                        Divider(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.scanPrinters() }
                    ) {
                        Text(if (isScanning) "扫描中..." else "刷新")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showScanDialog = false }
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }
} 