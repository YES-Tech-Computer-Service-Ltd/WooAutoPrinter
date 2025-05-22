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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.domain.printer.PrinterStatus
import com.example.wooauto.domain.printer.PrinterDevice
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.compose.ui.platform.LocalContext
import com.example.wooauto.MainActivity
import androidx.compose.ui.res.stringResource
import com.example.wooauto.R
import com.example.wooauto.presentation.navigation.Screen
import com.example.wooauto.presentation.screens.settings.SettingsViewModel
import com.example.wooauto.util.hasBluetoothPermissions
import com.example.wooauto.util.isBluetoothEnabled

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterDetailsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navController: NavController,
    // printerId is no longer needed here as this screen is only for new device selection
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val availablePrinters by viewModel.availablePrinters.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    
    // State for managing Bluetooth permissions and status
    var hasPermissions by remember { mutableStateOf(context.hasBluetoothPermissions()) }
    var bluetoothEnabled by remember { mutableStateOf(isBluetoothEnabled(context)) }

    val enableBluetoothMessage = stringResource(R.string.enable_bluetooth_to_scan)
    val grantPermissionMessage = stringResource(R.string.grant_bluetooth_permission_to_scan)
    val deviceSelectedMessage = stringResource(R.string.device_selected_proceed_to_config)

    // Effects to re-check permissions and Bluetooth status
    LaunchedEffect(Unit) {
        // Initial checks
        hasPermissions = context.hasBluetoothPermissions()
        bluetoothEnabled = isBluetoothEnabled(context)
        // Optionally, you could start scanning automatically if conditions are met,
        // or wait for user interaction.
        if(hasPermissions && bluetoothEnabled && availablePrinters.isEmpty()) {
            viewModel.startScan(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_bluetooth_device)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasPermissions) {
                PermissionMissingView(message = grantPermissionMessage) {
                    // Logic to request permissions. For simplicity, we'll assume this is handled by system dialogs for now.
                    // Or, you can navigate to app settings or use a permission request launcher.
                    // After permission grant, you'd update `hasPermissions` state.
                     coroutineScope.launch {
                        snackbarHostState.showSnackbar("请在系统设置中授予蓝牙权限")
                    }
                }
            } else if (!bluetoothEnabled) {
                BluetoothDisabledView(message = enableBluetoothMessage) {
                    // Logic to request Bluetooth enable. For simplicity, assume system dialogs or guide user.
                    // After enabling, update `bluetoothEnabled` state.
                     coroutineScope.launch {
                        snackbarHostState.showSnackbar("请打开蓝牙后重试")
                    }
                }
            } else {
                // Device Scanning and Selection UI
                DeviceSelectionContent(
                    availablePrinters = availablePrinters,
                    isScanning = isScanning,
                    onScanClicked = { viewModel.startScan(context) },
                    onDeviceSelected = { device ->
                        Log.d("PrinterDetails", "Device selected: ${device.name} - ${device.address}")
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(deviceSelectedMessage)
                        }
                        // Navigate to PrinterConfigurationSetupScreen with device details
                        // The printerId will be null, indicating a new printer setup
                        navController.navigate(
                            Screen.PrinterConfigurationSetup.route + 
                            "/null?name=${device.name}&address=${device.address}"
                        )
                    },
                    onStopScan = { viewModel.stopScan() }
                )
            }
        }
    }
}

@Composable
private fun PermissionMissingView(message: String, onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text(stringResource(R.string.grant_permission))
        }
    }
}

@Composable
private fun BluetoothDisabledView(message: String, onEnableBluetooth: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onEnableBluetooth) {
            Text(stringResource(R.string.enable_bluetooth))
        }
    }
}

@RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
@Composable
private fun DeviceSelectionContent(
    availablePrinters: List<PrinterDevice>,
    isScanning: Boolean,
    onScanClicked: () -> Unit,
    onDeviceSelected: (PrinterDevice) -> Unit,
    onStopScan: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.available_devices),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (isScanning) {
                Button(onClick = onStopScan) {
                    Icon(Icons.Default.BluetoothSearching, contentDescription = stringResource(R.string.stop_scan), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.padding(start = 4.dp))
                    Text(stringResource(R.string.stop_scan))
                }
            } else {
                Button(onClick = onScanClicked) {
                    Icon(Icons.Default.BluetoothSearching, contentDescription = stringResource(R.string.start_scan), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.padding(start = 4.dp))
                    Text(stringResource(R.string.start_scan))
                }
            }
        }

        if (isScanning && availablePrinters.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                 CircularProgressIndicator()
                 Spacer(modifier = Modifier.padding(start = 8.dp))
                 Text(stringResource(R.string.scanning_for_devices))
            }
        }

        if (!isScanning && availablePrinters.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.no_devices_found_ensure_printer_on),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else if (availablePrinters.isNotEmpty()){
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(availablePrinters) { device ->
                    DeviceItem(device = device, onDeviceSelected = onDeviceSelected)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(device: PrinterDevice, onDeviceSelected: (PrinterDevice) -> Unit) {
    // Try to get paired status, default to false if permission missing or other error
    val isPaired = remember(device.address) {
        try {
            val btAdapter = BluetoothAdapter.getDefaultAdapter()
            if (btAdapter?.isEnabled == true) {
                // This check might require BLUETOOTH_CONNECT permission on API 31+
                // For simplicity, we are not handling permission request here directly, but it should be considered.
                btAdapter.bondedDevices?.any { it.address == device.address } ?: false
            } else {
                false
            }
        } catch (e: SecurityException) {
            Log.w("DeviceItem", "SecurityException checking paired status for ${device.address}: ${e.message}")
            false // Assume not paired if permission is missing
        } catch (e: Exception) {
            Log.e("DeviceItem", "Error checking paired status for ${device.address}", e)
            false
        }
    }

    ListItem(
        headlineContent = { Text(device.name ?: stringResource(R.string.unknown_device)) },
        supportingContent = { Text(device.address) },
        leadingContent = {
            Icon(
                if (isPaired) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = if (isPaired) stringResource(R.string.paired_device) else stringResource(R.string.unpaired_device),
                tint = if (isPaired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
             Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.select_this_device), tint = MaterialTheme.colorScheme.secondary)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDeviceSelected(device) }
            .padding(vertical = 8.dp)
    )
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