package com.example.wooauto.presentation.screens.settings.PrinterSettings

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.printer.PrinterStatus
import com.example.wooauto.domain.printer.PrinterDevice
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.compose.ui.platform.LocalContext
import com.example.wooauto.MainActivity
import androidx.compose.ui.res.stringResource
import com.example.wooauto.R
import com.example.wooauto.presentation.screens.settings.SettingsViewModel

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterDetailsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navController: NavController,
    printerId: String?
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val availablePrinters by viewModel.availablePrinters.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    
    // 判断是添加新打印机还是编辑现有打印机
    val isNewPrinter = printerId == null || printerId == "new"
    
    // 添加一个状态，用于跟踪是否已经选择了设备
    var hasSelectedDevice by remember { mutableStateOf(!isNewPrinter) }
    
    val printerConfig by remember {
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
    val type by remember { mutableStateOf(printerConfig.type) }
    var paperWidth by remember { mutableStateOf(printerConfig.paperWidth.toString()) }
    var isDefault by remember { mutableStateOf(printerConfig.isDefault) }
    val autoCut by remember { mutableStateOf(printerConfig.autoCut) }
    
    // 页面加载时扫描打印机列表
    LaunchedEffect(key1 = Unit) {
        if (isNewPrinter) {
            viewModel.scanPrinters()
        }
        Log.d("PrinterDetails", "LaunchedEffect: isNewPrinter=$isNewPrinter, hasSelectedDevice=$hasSelectedDevice")
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isNewPrinter) {
                            if (hasSelectedDevice) stringResource(id = R.string.setup_printer) else stringResource(id = R.string.add_printer)
                        } else stringResource(id = R.string.edit_printer)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (isNewPrinter && hasSelectedDevice) {
                            // 如果是在新建打印机的设置页面，返回到设备选择页面
                            hasSelectedDevice = false
                            Log.d("PrinterDetails", "返回到设备选择页面, hasSelectedDevice=false")
                        } else {
                            Log.d("PrinterDetails", "返回上一页")
                            navController.popBackStack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Log.d("PrinterDetails", "Scaffold content: isNewPrinter=$isNewPrinter, hasSelectedDevice=$hasSelectedDevice")
        if (isNewPrinter && !hasSelectedDevice) {
            // 新建打印机时显示设备列表
            DeviceSelectionScreen(
                paddingValues = paddingValues,
                availablePrinters = availablePrinters,
                isScanning = isScanning,
                onScanClick = { viewModel.scanPrinters() },
                onDeviceSelected = { device ->
                    // 选择设备后更新配置
                    Log.d("PrinterDetails", "设备已选择: ${device.name}, ${device.address}")
                    name = device.name
                    address = device.address
                    hasSelectedDevice = true
                    Log.d("PrinterDetails", "hasSelectedDevice设置为true")
                }
            )
        } else {
            // 在lambda外部获取字符串资源
            val missingFieldsMessage = stringResource(id = R.string.please_enter_name_address)
            val configSavedMessage = stringResource(id = R.string.printer_config_saved)
            
            Log.d("PrinterDetails", "显示配置屏幕: name=$name, address=$address")
            // 编辑打印机或已选择设备时显示配置表单
            ConfigurationScreen(
                paperWidth = paperWidth,
                onPaperWidthChange = { paperWidth = it },
                isDefault = isDefault,
                onIsDefaultChange = { isDefault = it },
                onSave = {
                    Log.d("PrinterDetails", "onSave被调用: name='$name', address='$address'")
                    // 验证必填字段
                    if (name.isBlank() || address.isBlank()) {
                        Log.d("PrinterDetails", "名称或地址为空，显示提示信息")
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(missingFieldsMessage)
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
                        brand = printerConfig.brand,
                        autoCut = autoCut
                    )
                    
                    Log.d("PrinterDetails", "保存打印机配置: ${updatedConfig.name}")
                    viewModel.savePrinterConfig(updatedConfig)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(configSavedMessage)
                    }
                    
                    // 返回上一页
                    Log.d("PrinterDetails", "保存完成，返回上一页")
                    navController.popBackStack()
                },
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                paddingValues = paddingValues
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
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
            .padding(paddingValues)
            .padding(16.dp)
    ) {
        // 头部说明
        Text(
            text = stringResource(id = R.string.select_bluetooth_printer),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(id = R.string.select_printer_instruction),
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
                contentDescription = stringResource(id = R.string.refresh)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isScanning) stringResource(id = R.string.scanning_devices) else stringResource(id = R.string.refresh_device_list))
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
                    Text(stringResource(id = R.string.scanning_bluetooth))
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
                        imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.no_device_found),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.ensure_bluetooth_paired),
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
                                            text = stringResource(id = R.string.paired),
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
                                contentDescription = stringResource(id = R.string.select),
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
            text = stringResource(id = R.string.bluetooth_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 检查设备是否已配对
 */
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
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
fun ConfigurationScreen(
    paperWidth: String,
    onPaperWidthChange: (String) -> Unit,
    isDefault: Boolean,
    onIsDefaultChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    paddingValues: PaddingValues
) {
    Log.d("ConfigurationScreen", "配置屏幕开始渲染")
    val connectionErrorMessage by viewModel.connectionErrorMessage.collectAsState()
    
    // 添加自动切纸设置状态
    var autoCut by remember { mutableStateOf(viewModel.currentPrinterConfig.value?.autoCut ?: false) }
    
    LaunchedEffect(connectionErrorMessage) {
        connectionErrorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Text(
            text = stringResource(id = R.string.printer_configuration),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        HorizontalDivider()
        
        // 纸张宽度设置
        Column {
            Text(
                text = stringResource(id = R.string.paper_width_setting),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            var expanded by remember { mutableStateOf(false) }
            
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = when(paperWidth) {
                        "80" -> stringResource(id = R.string.paper_width_80mm)
                        else -> stringResource(id = R.string.paper_width_58mm)
                    },
                    onValueChange = { },
                    label = { Text(stringResource(id = R.string.paper_width)) },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(id = R.string.select_paper_width),
                            modifier = Modifier.clickable { expanded = true }
                        )
                    }
                )
                
                // 点击整个文本框时展开下拉菜单
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expanded = true },
                )
                
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.paper_width_58mm)) },
                        onClick = {
                            onPaperWidthChange("58")
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.paper_width_80mm)) },
                        onClick = {
                            onPaperWidthChange("80")
                            expanded = false
                        }
                    )
                }
            }
        }
        
        HorizontalDivider()
        
        // 打印机选项
        Column {
            Text(
                text = stringResource(id = R.string.printer_options),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = isDefault,
                    onCheckedChange = onIsDefaultChange
                )
                Text(
                    text = stringResource(id = R.string.set_as_default),
                    modifier = Modifier.clickable { onIsDefaultChange(!isDefault) }
                )
            }
            
            // 添加自动切纸选项
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = autoCut,
                    onCheckedChange = { autoCut = it }
                )
                Text(
                    text = stringResource(id = R.string.auto_cut_paper),
                    modifier = Modifier.clickable { autoCut = !autoCut }
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 保存按钮
        Button(
            onClick = {
                // 保存时更新autoCut属性
                Log.d("ConfigurationScreen", "点击保存按钮")
                viewModel.updateAutoCut(autoCut)
                onSave()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = stringResource(id = R.string.save)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(id = R.string.save_printer_config))
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
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
        Text(text = stringResource(id = R.string.request_bluetooth_location_permission))
    }
} 