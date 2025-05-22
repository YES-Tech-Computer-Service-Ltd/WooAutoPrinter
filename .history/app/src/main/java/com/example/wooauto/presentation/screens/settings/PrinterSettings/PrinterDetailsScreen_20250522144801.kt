package com.example.wooauto.presentation.screens.settings.PrinterSettings

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.R
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.printer.PrinterDevice
import com.example.wooauto.presentation.screens.settings.SettingsViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import kotlinx.coroutines.delay

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
    var showDeviceConfirmDialog by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<PrinterDevice?>(null) }
    
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
                            if (hasSelectedDevice) stringResource(id = R.string.setup_printer) else stringResource(id = R.string.add_printer)
                        } else stringResource(id = R.string.edit_printer)
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
        if (isNewPrinter && !hasSelectedDevice) {
            // 新建打印机时显示设备列表
            DeviceSelectionScreen(
                paddingValues = paddingValues,
                availablePrinters = availablePrinters,
                isScanning = isScanning,
                onScanClick = { viewModel.scanPrinters() },
                onDeviceSelected = { device ->
                    selectedDevice = device
                    showDeviceConfirmDialog = true
                }
            )
            
            if (showDeviceConfirmDialog && selectedDevice != null) {
                AlertDialog(
                    onDismissRequest = { showDeviceConfirmDialog = false },
                    title = { Text(stringResource(id = R.string.confirm_device_selection)) },
                    text = { Text(stringResource(id = R.string.device_selection_confirmation, selectedDevice?.name ?: "")) },
                    confirmButton = {
                        Button(
                            onClick = {
                                selectedDevice?.let { device ->
                                    name = device.name
                                    address = device.address
                                    hasSelectedDevice = true
                                    showDeviceConfirmDialog = false
                                    selectedDevice = null  // 清除选中的设备
                                }
                            }
                        ) {
                            Text(stringResource(id = R.string.confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeviceConfirmDialog = false }) {
                            Text(stringResource(id = R.string.cancel))
                        }
                    }
                )
            }
        } else {
            // 在lambda外部获取字符串资源
            val missingFieldsMessage = stringResource(id = R.string.please_enter_name_address)
            val configSavedMessage = stringResource(id = R.string.printer_config_saved)
            
            // 编辑打印机或已选择设备时显示配置表单
            ConfigurationScreen(
                paperWidth = paperWidth,
                onPaperWidthChange = { paperWidth = it },
                isDefault = isDefault,
                onIsDefaultChange = { isDefault = it },
                name = name,
                onNameChange = { name = it },
                address = address,
                onAddressChange = { address = it },
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
                        isDefault = isDefault
                    )
                    
                    viewModel.savePrinterConfig(updatedConfig)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(configSavedMessage)
                        // 确保在显示提示消息后再返回
                        delay(500)
                        navController.popBackStack()
                    }
                },
                onClose = {
                    navController.popBackStack()
                },
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                paddingValues = paddingValues
            )
        }
    }
} 