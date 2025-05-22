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
    
    // 添加确认对话框状态
    var showConfirmDialog by remember { mutableStateOf(false) }
    
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
                    // 选择设备后更新配置
                    name = device.name
                    address = device.address
                    hasSelectedDevice = true
                    
                    // 添加日志跟踪状态
                    android.util.Log.d("PrinterDetailsScreen", "设备已选择: ${device.name}, ${device.address}")
                    android.util.Log.d("PrinterDetailsScreen", "状态: isNewPrinter=$isNewPrinter, hasSelectedDevice=$hasSelectedDevice")
                    
                    // 通知用户状态变化
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("设备已选择，正在加载配置页面...")
                    }
                }
            )
        } else {
            // 在lambda外部获取字符串资源
            val missingFieldsMessage = stringResource(id = R.string.please_enter_name_address)
            val configSavedMessage = stringResource(id = R.string.printer_config_saved)
            
            // 打印调试信息
            android.util.Log.d("PrinterDetailsScreen", "显示配置页面: name=$name, address=$address")
            android.util.Log.d("PrinterDetailsScreen", "状态: isNewPrinter=$isNewPrinter, hasSelectedDevice=$hasSelectedDevice")
            
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
                    // 显示确认对话框而不是直接保存
                    showConfirmDialog = true
                },
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                paddingValues = paddingValues
            )
            
            // 显示确认对话框
            if (showConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmDialog = false },
                    title = { Text(stringResource(id = R.string.save_printer_config)) },
                    text = { 
                        Text("确认保存以下打印机配置？\n\n" + 
                             "名称: $name\n" +
                             "地址: $address\n" +
                             "纸张宽度: ${
                                if (paperWidth == "80") "80mm"
                                else "58mm"
                             }\n" +
                             "默认打印机: ${if (isDefault) "是" else "否"}")
                    },
                    confirmButton = {
                        Button(onClick = {
                            // 验证必填字段
                            if (name.isBlank() || address.isBlank()) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(missingFieldsMessage)
                                }
                                return@Button
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
                            
                            showConfirmDialog = false
                            // 返回上一页
                            navController.popBackStack()
                        }) {
                            Text(stringResource(id = R.string.confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmDialog = false }) {
                            Text(stringResource(id = R.string.cancel))
                        }
                    }
                )
            }
        }
    }
} 