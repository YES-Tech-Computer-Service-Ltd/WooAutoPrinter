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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.MainActivity
import com.example.wooauto.R
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.printer.PrinterDevice
import com.example.wooauto.domain.printer.PrinterStatus
import com.example.wooauto.presentation.screens.settings.SettingsViewModel
import kotlinx.coroutines.launch
import java.util.UUID

// 定义设置步骤枚举
private enum class SetupStep {
    SELECT_DEVICE,
    CONFIGURE
}

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
    val connectionErrorMessage by viewModel.connectionErrorMessage.collectAsState()
    
    // 判断是添加新打印机还是编辑现有打印机
    val isNewPrinter = printerId == null || printerId == "new"
    
    // 当前设置步骤
    var currentStep by remember { mutableStateOf(if (isNewPrinter) SetupStep.SELECT_DEVICE else SetupStep.CONFIGURE) }
    
    // 选中的设备
    var selectedDevice by remember { mutableStateOf<PrinterDevice?>(null) }
    
    // 打印机配置
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
    
    // 页面加载时自动扫描打印机列表（如果是添加新打印机或进入设备选择步骤时）
    LaunchedEffect(currentStep) {
        if (currentStep == SetupStep.SELECT_DEVICE) {
            viewModel.scanPrinters()
        }
    }
    
    // 监听错误信息
    LaunchedEffect(connectionErrorMessage) {
        connectionErrorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        when {
                            !isNewPrinter -> stringResource(id = R.string.edit_printer)
                            currentStep == SetupStep.SELECT_DEVICE -> stringResource(id = R.string.select_bluetooth_printer)
                            else -> stringResource(id = R.string.setup_printer)
                        }
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        when {
                            isNewPrinter && currentStep == SetupStep.CONFIGURE -> {
                                // 返回到设备选择
                                currentStep = SetupStep.SELECT_DEVICE
                            }
                            else -> {
                                navController.popBackStack()
                            }
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
        when (currentStep) {
            SetupStep.SELECT_DEVICE -> {
                // 显示设备选择UI
                DeviceSelectionContent(
                    paddingValues = paddingValues,
                    availablePrinters = availablePrinters,
                    isScanning = isScanning,
                    onScanClick = { viewModel.scanPrinters() },
                    onDeviceSelected = { device ->
                        selectedDevice = device
                        name = device.name
                        address = device.address
                        currentStep = SetupStep.CONFIGURE
                    }
                )
            }
            SetupStep.CONFIGURE -> {
                // 在lambda外部获取字符串资源
                val missingFieldsMessage = stringResource(id = R.string.please_enter_name_address)
                val configSavedMessage = stringResource(id = R.string.printer_config_saved)
                
                // 显示配置UI
                ConfigurationScreen(
                    paperWidth = paperWidth,
                    onPaperWidthChange = { paperWidth = it },
                    isDefault = isDefault,
                    onIsDefaultChange = { isDefault = it },
                    onSave = {
                        // 验证必填字段
                        if (name.isBlank() || address.isBlank()) {
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
                        
                        viewModel.savePrinterConfig(updatedConfig)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(configSavedMessage)
                        }
                        
                        // 返回上一页
                        navController.popBackStack()
                    },
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                    paddingValues = paddingValues
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun DeviceSelectionContent(
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
        
        // 扫描状态和刷新按钮
        if (isScanning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(id = R.string.scanning_bluetooth),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onScanClick,
                    enabled = !isScanning
                ) {
                    Text(stringResource(id = R.string.stop_scanning))
                }
            }
        } else {
            Button(
                onClick = onScanClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(id = R.string.refresh)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(id = R.string.rescan))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // 主要内容区域：扫描中、空状态或设备列表
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isScanning) {
                // 全屏扫描指示器
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.scanning_for_bluetooth_devices),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else if (availablePrinters.isEmpty()) {
                // 空状态
                EmptyDeviceState()
            } else {
                // 设备列表
                LazyColumn(modifier = Modifier.fillMaxSize()) {
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
        }
        
        // 底部提示
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.bluetooth_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun EmptyDeviceState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.no_device_found_scan_tip),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.ensure_bluetooth_paired),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            RequestPermissionButton()
        }
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