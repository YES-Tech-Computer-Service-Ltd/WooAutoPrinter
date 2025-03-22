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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.printer.PrinterBrand
import com.example.wooauto.domain.printer.PrinterStatus
import com.example.wooauto.domain.printer.PrinterDevice
import com.example.wooauto.presentation.navigation.Screen
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.compose.ui.platform.LocalContext
import com.example.wooauto.MainActivity
import androidx.compose.material3.Divider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.ScrollState

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
    
    // 添加一个状态，用于跟踪是否已经选择了设备
    var hasSelectedDevice by remember { mutableStateOf(!isNewPrinter) }
    
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
    
    // 页面加载时扫描打印机列表
    LaunchedEffect(key1 = Unit) {
        if (isNewPrinter) {
            viewModel.scanPrinters()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isNewPrinter) {
                            if (hasSelectedDevice) "设置打印机" else "添加打印机"
                        } else "编辑打印机"
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (isNewPrinter && hasSelectedDevice) {
                            // 如果是在新建打印机的设置页面，返回到设备选择页面
                            hasSelectedDevice = false
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (isNewPrinter && !hasSelectedDevice) {
            // 新建打印机时显示设备列表
            DeviceSelectionScreen(
                paddingValues = paddingValues,
                availablePrinters = availablePrinters,
                isScanning = isScanning,
                onScanClick = { viewModel.scanPrinters() },
                onDeviceSelected = { device ->
                    // 选择设备后更新配置
                    name = device.name
                    address = device.address
                    hasSelectedDevice = true
                }
            )
        } else {
            // 编辑打印机或已选择设备时显示配置表单
            ConfigurationScreen(
                paddingValues = paddingValues,
                scrollState = scrollState,
                printerConfig = printerConfig,
                name = name,
                onNameChange = { name = it },
                address = address,
                type = type,
                onTypeChange = { type = it },
                paperWidth = paperWidth,
                onPaperWidthChange = { paperWidth = it },
                isDefault = isDefault,
                onDefaultChange = { isDefault = it },
                isAutoPrint = isAutoPrint,
                onAutoPrintChange = { isAutoPrint = it },
                printCopies = printCopies,
                onPrintCopiesChange = { printCopies = it },
                printStoreInfo = printStoreInfo,
                onPrintStoreInfoChange = { printStoreInfo = it },
                printCustomerInfo = printCustomerInfo,
                onPrintCustomerInfoChange = { printCustomerInfo = it },
                printItemDetails = printItemDetails,
                onPrintItemDetailsChange = { printItemDetails = it },
                printOrderNotes = printOrderNotes,
                onPrintOrderNotesChange = { printOrderNotes = it },
                printFooter = printFooter,
                onPrintFooterChange = { printFooter = it },
                onTestPrint = {
                    coroutineScope.launch {
                        // 显示连接中提示
                        snackbarHostState.showSnackbar("正在连接打印机...")
                        
                        // 测试打印
                        try {
                            val testConfig = printerConfig.copy(
                                paperWidth = paperWidth.toIntOrNull() ?: PrinterConfig.PAPER_WIDTH_57MM
                            )
                            val success = viewModel.testPrint(testConfig)
                            if (success) {
                                snackbarHostState.showSnackbar("测试打印成功")
                            } else {
                                val errorMsg = viewModel.connectionErrorMessage.value ?: "测试打印失败，请检查打印机连接"
                                snackbarHostState.showSnackbar(errorMsg)
                            }
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("打印过程出现异常: ${e.message ?: "未知错误"}")
                        }
                    }
                },
                onSave = {
                    // 验证必填字段
                    if (name.isBlank() || address.isBlank()) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("请填写打印机名称和地址")
                        }
                        return@ConfigurationScreen
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
            )
        }
    }
}

@Composable
private fun DeviceSelectionScreen(
    paddingValues: PaddingValues,
    availablePrinters: List<PrinterDevice>,
    isScanning: Boolean,
    onScanClick: () -> Unit,
    onDeviceSelected: (PrinterDevice) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = paddingValues.calculateTopPadding(), 
                   bottom = paddingValues.calculateBottomPadding(),
                   start = paddingValues.calculateStartPadding(layoutDirection = LayoutDirection.Ltr),
                   end = paddingValues.calculateEndPadding(layoutDirection = LayoutDirection.Ltr))
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // 头部说明
        Text(
            text = "选择蓝牙打印机",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "请从下列设备中选择您的打印机，选择后可进行详细设置",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 刷新按钮
        Button(
            onClick = onScanClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "刷新"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isScanning) "扫描中..." else "刷新设备列表")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 设备列表或加载状态
        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "未找到蓝牙设备",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "请确保蓝牙已开启并已与打印机配对",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    RequestPermissionButton()
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(availablePrinters) { device ->
                    val isPaired = isPairedDevice(device)
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onDeviceSelected(device) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
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
                                            text = "(已配对)",
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
                            
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "选择",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        
        // 底部提示
        Text(
            text = "提示: 请确保打印机已开启并处于可发现状态。对于Android 7设备，可能需要先在系统蓝牙设置中配对打印机。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 检查设备是否已配对
 */
private fun isPairedDevice(device: PrinterDevice): Boolean {
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return false
    try {
        @Suppress("DEPRECATION")
        val pairedDevices = bluetoothAdapter.bondedDevices
        return pairedDevices?.any { it.address == device.address } ?: false
    } catch (e: Exception) {
        Log.e("PrinterDetailsScreen", "检查设备配对状态失败", e)
        return false
    }
}

@Composable
private fun ConfigurationScreen(
    paddingValues: PaddingValues,
    scrollState: ScrollState,
    printerConfig: PrinterConfig,
    name: String,
    onNameChange: (String) -> Unit,
    address: String,
    type: String,
    onTypeChange: (String) -> Unit,
    paperWidth: String,
    onPaperWidthChange: (String) -> Unit,
    isDefault: Boolean,
    onDefaultChange: (Boolean) -> Unit,
    isAutoPrint: Boolean,
    onAutoPrintChange: (Boolean) -> Unit,
    printCopies: String,
    onPrintCopiesChange: (String) -> Unit,
    printStoreInfo: Boolean,
    onPrintStoreInfoChange: (Boolean) -> Unit,
    printCustomerInfo: Boolean,
    onPrintCustomerInfoChange: (Boolean) -> Unit,
    printItemDetails: Boolean,
    onPrintItemDetailsChange: (Boolean) -> Unit,
    printOrderNotes: Boolean,
    onPrintOrderNotesChange: (Boolean) -> Unit,
    printFooter: Boolean,
    onPrintFooterChange: (Boolean) -> Unit,
    onTestPrint: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = paddingValues.calculateTopPadding(), 
                   bottom = paddingValues.calculateBottomPadding(),
                   start = paddingValues.calculateStartPadding(layoutDirection = LayoutDirection.Ltr),
                   end = paddingValues.calculateEndPadding(layoutDirection = LayoutDirection.Ltr))
            .padding(horizontal = 16.dp, vertical = 16.dp)
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
            onValueChange = onNameChange,
            label = { Text("打印机名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 打印机品牌信息（只读显示）
        OutlinedTextField(
            value = printerConfig.brand?.displayName ?: "未知品牌",
            onValueChange = { },
            label = { Text("打印机品牌") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            enabled = false,
            trailingIcon = {
                if (printerConfig.brand != null && printerConfig.brand != PrinterBrand.UNKNOWN) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "已识别品牌",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 命令语言信息
        OutlinedTextField(
            value = printerConfig.brand?.commandLanguage ?: "未知命令语言",
            onValueChange = { },
            label = { Text("命令语言") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            enabled = false
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 打印机地址（只读显示）
        OutlinedTextField(
            value = address,
            onValueChange = { },
            label = { Text("打印机地址") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            enabled = false
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
                onClick = { onTypeChange(PrinterConfig.PRINTER_TYPE_BLUETOOTH) }
            )
            Text(
                text = "蓝牙打印机",
                modifier = Modifier
                    .clickable { onTypeChange(PrinterConfig.PRINTER_TYPE_BLUETOOTH) }
                    .padding(start = 4.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            RadioButton(
                selected = type == PrinterConfig.PRINTER_TYPE_WIFI,
                onClick = { onTypeChange(PrinterConfig.PRINTER_TYPE_WIFI) }
            )
            Text(
                text = "网络打印机",
                modifier = Modifier
                    .clickable { onTypeChange(PrinterConfig.PRINTER_TYPE_WIFI) }
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
            // 58mm选项
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RadioButton(
                    selected = paperWidth.toIntOrNull() == PrinterConfig.PAPER_WIDTH_57MM,
                    onClick = { onPaperWidthChange(PrinterConfig.PAPER_WIDTH_57MM.toString()) }
                )
                Text(
                    text = "58mm",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .clickable { onPaperWidthChange(PrinterConfig.PAPER_WIDTH_57MM.toString()) }
                        .padding(horizontal = 4.dp)
                )
                Text(
                    text = "(有效宽度50mm)",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(32.dp))
            
            // 80mm选项
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RadioButton(
                    selected = paperWidth.toIntOrNull() == PrinterConfig.PAPER_WIDTH_80MM,
                    onClick = { onPaperWidthChange(PrinterConfig.PAPER_WIDTH_80MM.toString()) }
                )
                Text(
                    text = "80mm",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .clickable { onPaperWidthChange(PrinterConfig.PAPER_WIDTH_80MM.toString()) }
                        .padding(horizontal = 4.dp)
                )
                Text(
                    text = "(有效宽度72mm)",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        
        // 纸张宽度预览
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = "打印宽度预览",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 预览区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        // 根据选择的打印宽度显示不同的预览
                        val selectedWidth = paperWidth.toIntOrNull() ?: PrinterConfig.PAPER_WIDTH_57MM
                        val previewText = when (selectedWidth) {
                            PrinterConfig.PAPER_WIDTH_57MM -> "58mm打印纸(有效宽度50mm)\n一行可打印约28个英文字符\n或14个中文字符"
                            PrinterConfig.PAPER_WIDTH_80MM -> "80mm打印纸(有效宽度72mm)\n一行可打印约42个英文字符\n或21个中文字符"
                            else -> "默认58mm打印纸"
                        }
                        
                        // 打印机标题示例
                        Text(
                            text = "示例标题",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // 分隔线
                        Divider(
                            color = Color.Black,
                            thickness = 1.dp
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // 打印机内容示例
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                        
                        // 示例商品
                        if (selectedWidth == PrinterConfig.PAPER_WIDTH_57MM) {
                            Text(
                                text = "香辣鸡腿堡",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "  2 x ¥15.00",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "香辣鸡腿堡",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "2 x ¥15.00",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "注意: 实际打印效果可能与预览略有差异，建议进行测试打印",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 10.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 测试打印按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onTestPrint,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "测试打印")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 打印份数
        OutlinedTextField(
            value = printCopies,
            onValueChange = { 
                if (it.isEmpty() || it.toIntOrNull() != null) {
                    onPrintCopiesChange(it) 
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
                onCheckedChange = onPrintStoreInfoChange
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
                onCheckedChange = onPrintCustomerInfoChange
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
                onCheckedChange = onPrintItemDetailsChange
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
                onCheckedChange = onPrintOrderNotesChange
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
                onCheckedChange = onPrintFooterChange
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
                onCheckedChange = onDefaultChange
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
                onCheckedChange = onAutoPrintChange
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 保存按钮
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("保存打印机设置")
        }
        
        // 底部安全间距
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun RequestPermissionButton() {
    val context = LocalContext.current
    Button(
        onClick = {
            if (context is MainActivity) {
                context.requestAllPermissions()
            }
        },
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(text = "请求蓝牙和位置权限")
    }
} 