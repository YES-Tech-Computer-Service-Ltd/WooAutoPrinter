package com.example.wooauto.presentation.screens.settings.PrinterSettings

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wooauto.R
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.printer.PrinterStatus
import com.example.wooauto.domain.printer.PrinterDevice
import com.example.wooauto.domain.printer.PrinterBrand
import com.example.wooauto.data.printer.BluetoothPrinterManager
import kotlinx.coroutines.launch
import com.example.wooauto.presentation.screens.settings.SettingsViewModel
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.navigation.NavController
import com.example.wooauto.MainActivity
import java.util.UUID
import androidx.compose.ui.platform.LocalContext

@Composable
private fun PrinterList(
    printerConfigs: List<PrinterConfig>,
    currentPrinterConfig: PrinterConfig?,
    printerStatus: PrinterStatus,
    isPrinting: Boolean,
    onConnect: (PrinterConfig) -> Unit,
    onEdit: (PrinterConfig) -> Unit,
    onDelete: (PrinterConfig) -> Unit,
    onTestPrint: (PrinterConfig) -> Unit,
    onSetDefault: (PrinterConfig, Boolean) -> Unit
) {
    LazyColumn {
        items(printerConfigs) { printer ->
            PrinterConfigItem(
                printerConfig = printer,
                isConnected = printerStatus == PrinterStatus.CONNECTED && currentPrinterConfig?.id == printer.id,
                isPrinting = isPrinting && currentPrinterConfig?.id == printer.id,
                onConnect = { onConnect(printer) },
                onEdit = { onEdit(printer) },
                onDelete = { onDelete(printer) },
                onTestPrint = { onTestPrint(printer) },
                onSetDefault = { isDefault -> onSetDefault(printer, isDefault) }
            )
            
            if (printer != printerConfigs.last()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PrinterSettingsContent(
    modifier: Modifier = Modifier,
    printerConfigs: List<PrinterConfig>,
    currentPrinterConfig: PrinterConfig?,
    printerStatus: PrinterStatus,
    isPrinting: Boolean,
    isScanning: Boolean,
    availablePrinters: List<PrinterDevice>,
    connectionErrorMessage: String?,
    isConnecting: Boolean,
    showDeleteDialog: Boolean,
    printerToDelete: PrinterConfig?,
    showScanDialog: Boolean,
    onConnect: (PrinterConfig) -> Unit,
    onEdit: (PrinterConfig) -> Unit,
    onDelete: (PrinterConfig) -> Unit,
    onTestPrint: (PrinterConfig) -> Unit,
    onSetDefault: (PrinterConfig, Boolean) -> Unit,
    onClearError: () -> Unit,
    onAddPrinter: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onDeviceSelected: (PrinterDevice) -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissScanDialog: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 显示错误消息（如果有）
        if (connectionErrorMessage != null) {
            ErrorMessageCard(
                errorMessage = connectionErrorMessage,
                onDismiss = onClearError
            )
        }
        
        if (printerConfigs.isEmpty()) {
            // 当没有打印机时，显示新的提示卡片
            NoPrintersInfoCard(onAddPrinter = onAddPrinter)
        } else {
            // 显示打印机列表
            PrinterList(
                printerConfigs = printerConfigs,
                currentPrinterConfig = currentPrinterConfig,
                printerStatus = printerStatus,
                isPrinting = isPrinting,
                onConnect = onConnect,
                onEdit = onEdit,
                onDelete = onDelete,
                onTestPrint = onTestPrint,
                onSetDefault = onSetDefault
            )
            
            // 添加打印机卡片 (即使有打印机也显示，以便添加更多)
            AddPrinterCard(onClick = onAddPrinter)
        }
        
        // 删除确认对话框
        if (showDeleteDialog) {
            DeletePrinterDialog(
                printerName = printerToDelete?.getDisplayName() ?: "",
                onDismiss = onDismissDeleteDialog,
                onConfirm = onConfirmDelete
            )
        }
        
        // 蓝牙设备扫描对话框
        if (showScanDialog) {
            BluetoothScanDialog(
                isScanning = isScanning,
                availablePrinters = availablePrinters,
                onDismiss = onDismissScanDialog,
                onScan = onScan,
                onStopScan = onStopScan,
                onDeviceSelected = onDeviceSelected
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSettingsDialogContent(
    viewModel: SettingsViewModel = hiltViewModel(),
    onClose: () -> Unit,
    onEditPrinter: ((String) -> Unit)? = null
) {
    val printerConfigs by viewModel.printerConfigs.collectAsState()
    val currentPrinterConfig by viewModel.currentPrinterConfig.collectAsState()
    val printerStatus by viewModel.printerStatus.collectAsState()
    val isPrinting by viewModel.isPrinting.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val availablePrinters by viewModel.availablePrinters.collectAsState()
    val connectionErrorMessage by viewModel.connectionErrorMessage.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var printerToDelete by remember { mutableStateOf<PrinterConfig?>(null) }
    var showScanDialog by remember { mutableStateOf(false) }
    
    val printerConnectedSuccessMsg = stringResource(id = R.string.printer_connected_success)
    val printerDisconnectedMsg = stringResource(id = R.string.printer_disconnected)
    val printerConnectionFailedMsg = stringResource(id = R.string.printer_connection_failed)
    val connectingPrinterMsg = stringResource(id = R.string.connecting_printer)
    val testPrintSuccessMsg = stringResource(id = R.string.test_print_success)
    val testPrintFailedMsg = stringResource(id = R.string.test_print_failed)
    val printerErrorMsg = stringResource(id = R.string.printer_error)
    val setAsDefaultSuccessMsg = stringResource(id = R.string.set_as_default_success)
    val printerDeletedMsg = stringResource(id = R.string.printer_deleted)
    val closeButtonDesc = stringResource(R.string.close)
    val printerSettingsTitle = stringResource(id = R.string.printer_settings_title)
    
    LaunchedEffect(key1 = Unit) {
        viewModel.loadPrinterConfigs()
    }
    
    LaunchedEffect(printerStatus, connectionErrorMessage, isConnecting) {
        if (printerStatus == PrinterStatus.CONNECTED && showScanDialog) {
            showScanDialog = false
            snackbarHostState.showSnackbar(printerConnectedSuccessMsg)
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(printerSettingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = closeButtonDesc)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { paddingValues ->
        PrinterSettingsContent(
            modifier = Modifier.padding(paddingValues),
            printerConfigs = printerConfigs,
            currentPrinterConfig = currentPrinterConfig,
            printerStatus = printerStatus,
            isPrinting = isPrinting,
            isScanning = isScanning,
            availablePrinters = availablePrinters,
            connectionErrorMessage = connectionErrorMessage,
            isConnecting = isConnecting,
            showDeleteDialog = showDeleteDialog,
            printerToDelete = printerToDelete,
            showScanDialog = showScanDialog,
            onConnect = { printer ->
                coroutineScope.launch {
                    if (printerStatus == PrinterStatus.CONNECTED && currentPrinterConfig?.id == printer.id) {
                        viewModel.disconnectPrinter(printer)
                        snackbarHostState.showSnackbar(printerDisconnectedMsg)
                    } else {
                        val connected = viewModel.connectPrinter(printer)
                        if (connected) {
                            snackbarHostState.showSnackbar(printerConnectedSuccessMsg)
                        } else {
                            snackbarHostState.showSnackbar(printerConnectionFailedMsg)
                        }
                    }
                }
            },
            onEdit = { printer ->
                Log.d("PrinterSettings", "Edit printer: ${printer.id}")
                onEditPrinter?.invoke(printer.id)
            },
            onDelete = { printer ->
                printerToDelete = printer
                showDeleteDialog = true
            },
            onTestPrint = { printer ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(connectingPrinterMsg)
                    try {
                        val success = viewModel.testPrint(printer)
                        if (success) {
                            snackbarHostState.showSnackbar(testPrintSuccessMsg)
                        } else {
                            val errorMsgDisplay = viewModel.connectionErrorMessage.value ?: testPrintFailedMsg
                            snackbarHostState.showSnackbar(errorMsgDisplay)
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("$printerErrorMsg ${e.message ?: ""}")
                    }
                }
            },
            onSetDefault = { printer, isDefault ->
                if (isDefault) {
                    val updatedConfig = printer.copy(isDefault = true)
                    viewModel.savePrinterConfig(updatedConfig)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(setAsDefaultSuccessMsg)
                    }
                }
            },
            onClearError = { viewModel.clearConnectionError() },
            onAddPrinter = { showScanDialog = true },
            onScan = { viewModel.scanPrinters() },
            onStopScan = {
                if (viewModel.getPrinterManager() is BluetoothPrinterManager) {
                    (viewModel.getPrinterManager() as BluetoothPrinterManager).stopDiscovery()
                }
                viewModel.stopScanning()
            },
            onDeviceSelected = { device ->
                if (!isConnecting) {
                    coroutineScope.launch {
                        val success = viewModel.connectPrinter(device)
                        if (success) {
                            showScanDialog = false
                            snackbarHostState.showSnackbar(printerConnectedSuccessMsg)
                        } else {
                            snackbarHostState.showSnackbar(printerConnectionFailedMsg)
                        }
                    }
                }
            },
            onDismissDeleteDialog = { showDeleteDialog = false },
            onConfirmDelete = {
                coroutineScope.launch {
                    printerToDelete?.let {
                        viewModel.deletePrinterConfig(it.id)
                        showDeleteDialog = false
                        printerToDelete = null
                        snackbarHostState.showSnackbar(printerDeletedMsg)
                    }
                }
            },
            onDismissScanDialog = { showScanDialog = false }
        )
    }
}

@Composable
private fun ErrorMessageCard(
    errorMessage: String,
    onDismiss: () -> Unit
) {
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
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(id = R.string.close),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun NoPrintersInfoCard(onAddPrinter: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 8.dp) // 给卡片一些水平边距
            .clickable { onAddPrinter() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AddCircleOutline,
                contentDescription = stringResource(R.string.add_printer_icon_desc),
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.no_printers_configured_prompt),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.tap_here_to_add_new_printer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddPrinterCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(id = R.string.add_printer),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f) // Added weight here to fill available space
            ) {
                Text(
                    text = stringResource(id = R.string.add_printer),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(id = R.string.add_printer_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null, // Decorative
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeletePrinterDialog(
    printerName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.delete_printer)) },
        text = { Text(stringResource(id = R.string.delete_printer_confirm, printerName)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(id = R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
private fun BluetoothScanDialog(
    isScanning: Boolean,
    availablePrinters: List<PrinterDevice>,
    onDismiss: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onDeviceSelected: (PrinterDevice) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.bluetooth_devices)) },
        text = { 
            Column {
                if (isScanning) {
                    ScanningIndicator()
                } else {
                    ScanHeader(
                        availablePrinters = availablePrinters,
                        onScan = onScan
                    )
                }
                
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
                            BluetoothDeviceItem(
                                device = device,
                                onClick = { onDeviceSelected(device) }
                            )
                            
                            if (device != availablePrinters.last()) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.close))
            }
        },
        dismissButton = {
            if (isScanning) {
                TextButton(onClick = onStopScan) {
                    Text(stringResource(id = R.string.stop_scanning))
                }
            }
        }
    )
}

@Composable
private fun ScanningIndicator() {
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
}

@Composable
private fun ScanHeader(
    availablePrinters: List<PrinterDevice>,
    onScan: () -> Unit
) {
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
        
        IconButton(onClick = onScan) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(id = R.string.rescan)
            )
        }
    }
}

@Composable
private fun BluetoothDeviceItem(
    device: PrinterDevice,
    onClick: () -> Unit
) {
    val isPaired = isPairedDevice(device)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
}

@Composable
private fun PrinterConfigItem(
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
                    DefaultPrinterIndicator()
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
                ConnectionStatusIndicator(isPrinting = isPrinting, isConnected = isConnected)
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 连接/断开按钮
                ConnectButton(isPrinting = isPrinting, isConnected = isConnected, onClick = onConnect)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 打印机地址和其他信息
            PrinterInfo(printerConfig = printerConfig)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 操作按钮
            PrinterActions(
                isConnected = isConnected,
                isPrinting = isPrinting,
                onEdit = onEdit,
                onTestPrint = onTestPrint,
                onDelete = onDelete
            )
            
            // 设为默认打印机开关
            if (!printerConfig.isDefault) {
                Spacer(modifier = Modifier.height(8.dp))
                DefaultPrinterSwitch(
                    isDefault = printerConfig.isDefault,
                    onSetDefault = onSetDefault
                )
            }
            
            // 自动打印开关
            if (printerConfig.isDefault) {
                Spacer(modifier = Modifier.height(8.dp))
                AutoPrintSwitch(
                    printerConfig = printerConfig,
                    onSetDefault = onSetDefault
                )
            }
        }
    }
}

@Composable
private fun DefaultPrinterIndicator() {
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

@Composable
private fun ConnectionStatusIndicator(isPrinting: Boolean, isConnected: Boolean) {
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
}

@Composable
private fun ConnectButton(isPrinting: Boolean, isConnected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
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

@Composable
private fun PrinterInfo(printerConfig: PrinterConfig) {
    Text(
        text = stringResource(id = R.string.printer_address_label, printerConfig.address),
        style = MaterialTheme.typography.bodyMedium
    )
    
    if (printerConfig.brand != PrinterBrand.UNKNOWN) {
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
}

@Composable
private fun PrinterActions(
    isConnected: Boolean,
    isPrinting: Boolean,
    onEdit: () -> Unit,
    onTestPrint: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 编辑按钮
        IconButton(onClick = onEdit) {
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
        TextButton(onClick = onDelete) {
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
}

@Composable
private fun DefaultPrinterSwitch(isDefault: Boolean, onSetDefault: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSetDefault(!isDefault) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.set_as_default),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        
        Switch(
            checked = isDefault,
            onCheckedChange = { onSetDefault(!isDefault) }
        )
    }
}

@Composable
private fun AutoPrintSwitch(printerConfig: PrinterConfig, onSetDefault: (Boolean) -> Unit) {
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
            text = stringResource(id = R.string.auto_print_new_orders),
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

/**
 * 检查设备是否已配对 (不需要Composable上下文的版本)
 */
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
private fun isPairedDevice(device: PrinterDevice): Boolean {
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return false
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            //todo
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

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterConfigurationSetupScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
    printerId: String?, // Null for new printer, non-null for editing
    initialName: String? = null, // For new printer from device selection
    initialAddress: String? = null // For new printer from device selection
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val isNewPrinterSetup = printerId == null
    
    // Load existing config if editing, or create a new one
    val (initialPrinterConfig, isTrulyNewPrinter) = remember(printerId, initialName, initialAddress) {
        if (printerId != null) {
            viewModel.getPrinterConfig(printerId)?.let { Pair(it, false) }
                ?: Pair(PrinterConfig(id = UUID.randomUUID().toString(), name = "", address = "", type = PrinterConfig.PRINTER_TYPE_BLUETOOTH), true)
        } else {
            Pair(PrinterConfig(
                id = UUID.randomUUID().toString(),
                name = initialName ?: "",
                address = initialAddress ?: "",
                type = PrinterConfig.PRINTER_TYPE_BLUETOOTH
            ), true)
        }
    }

    var name by remember { mutableStateOf(initialPrinterConfig.name) }
    var address by remember { mutableStateOf(initialPrinterConfig.address) }
    // Type is not editable for now, defaults to Bluetooth
    val type by remember { mutableStateOf(initialPrinterConfig.type) }
    var paperWidth by remember { mutableStateOf(initialPrinterConfig.paperWidth.toString()) }
    var isDefault by remember { mutableStateOf(initialPrinterConfig.isDefault) }
    var autoCut by remember { mutableStateOf(initialPrinterConfig.autoCut) }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isTrulyNewPrinter) stringResource(id = R.string.setup_printer) else stringResource(id = R.string.edit_printer)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.back))
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
        val missingFieldsMessage = stringResource(id = R.string.please_enter_name_address)
        val configSavedMessage = stringResource(id = R.string.printer_config_saved)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Name Field (Editable only if it's a truly new printer being set up, or existing printer name is blank)
            // For printers selected from Bluetooth scan, the name is pre-filled and usually not changed by user here.
            // If editing an existing printer, the name field is also shown.
             OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.printer_name)) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = !isTrulyNewPrinter && initialPrinterConfig.name.isNotBlank() && printerId == null // Read-only if pre-filled from scan for a new printer.
            )

            // Address Field (Usually not editable by user directly, pre-filled from scan or existing config)
            OutlinedTextField(
                value = address,
                onValueChange = { /* Address is typically not changed by user manually */ },
                label = { Text(stringResource(R.string.printer_address)) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true // Address is generally not user-editable
            )
            
            HorizontalDivider()

            // Paper Width Setting
            Text(
                text = stringResource(id = R.string.paper_width_setting),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            var paperWidthExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = when(paperWidth) {
                        "80" -> stringResource(id = R.string.paper_width_80mm)
                        else -> stringResource(id = R.string.paper_width_58mm) // Default to 58mm
                    },
                    onValueChange = { },
                    label = { Text(stringResource(id = R.string.paper_width)) },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(id = R.string.select_paper_width),
                            modifier = Modifier.clickable { paperWidthExpanded = true }
                        )
                    }
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { paperWidthExpanded = true }
                )
                DropdownMenu(
                    expanded = paperWidthExpanded,
                    onDismissRequest = { paperWidthExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.paper_width_58mm)) },
                        onClick = {
                            paperWidth = "58"
                            paperWidthExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.paper_width_80mm)) },
                        onClick = {
                            paperWidth = "80"
                            paperWidthExpanded = false
                        }
                    )
                }
            }

            HorizontalDivider()

            // Printer Options
            Text(
                text = stringResource(id = R.string.printer_options),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = isDefault,
                    onCheckedChange = { isDefault = it }
                )
                Text(
                    text = stringResource(id = R.string.set_as_default),
                    modifier = Modifier.clickable { isDefault = !isDefault }
                )
            }
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

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (name.isBlank() || address.isBlank()) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(missingFieldsMessage)
                        }
                        return@Button
                    }
                    
                    val updatedConfig = initialPrinterConfig.copy(
                        // id is preserved from initialPrinterConfig (either existing or newly generated UUID)
                        name = name,
                        address = address, // address is from initial, not user input here
                        type = type, // type is from initial
                        paperWidth = paperWidth.toIntOrNull() ?: PrinterConfig.PAPER_WIDTH_57MM,
                        isDefault = isDefault,
                        autoCut = autoCut
                        // brand is not set here, will be determined by PrinterManager upon connection
                    )
                    
                    viewModel.savePrinterConfig(updatedConfig)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(configSavedMessage)
                    }
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, contentDescription = stringResource(id = R.string.save))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(id = R.string.save_printer_config))
            }
        }
    }
} 