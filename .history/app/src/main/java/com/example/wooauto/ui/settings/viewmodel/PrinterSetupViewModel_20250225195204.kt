package com.example.wooauto.ui.settings.viewmodel

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.utils.PreferencesManager
import com.example.wooauto.utils.PrintService
import com.example.wooauto.utils.PrintService.PrinterInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class PrinterSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "PrinterSetupViewModel"
    private val printService = PrintService(getApplication())
    private val preferencesManager = PreferencesManager(getApplication())

    // Printers state
    private val _printers = MutableStateFlow<List<PrinterInfo>>(emptyList())
    val printers: StateFlow<List<PrinterInfo>> = _printers.asStateFlow()

    // Default printer ID
    private val _defaultPrinterId = MutableStateFlow<String?>(null)
    val defaultPrinterId: StateFlow<String?> = _defaultPrinterId.asStateFlow()

    // Backup printer ID
    private val _backupPrinterId = MutableStateFlow<String?>(null)
    val backupPrinterId: StateFlow<String?> = _backupPrinterId.asStateFlow()

    // Scanning state
    private val _scanningState = MutableStateFlow<ScanningState>(ScanningState.Idle)
    val scanningState: StateFlow<ScanningState> = _scanningState.asStateFlow()

    // Testing state
    private val _testingState = MutableStateFlow<PrinterTestingState>(PrinterTestingState.Idle)
    val testingState: StateFlow<PrinterTestingState> = _testingState.asStateFlow()

    // UI State
    private val _uiState = MutableStateFlow<PrinterSetupUiState>(PrinterSetupUiState.Loading)
    val uiState: StateFlow<PrinterSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadPrinters()
        }
    }

    /**
     * Load saved printers
     */
    private suspend fun loadPrinters() {
        try {
            _uiState.value = PrinterSetupUiState.Loading
            val printersList = printService.getAllPrinters()
            _printers.value = printersList

            // Get default and backup printer IDs
            val defaultPrinter = printersList.find { it.isDefault }
            _defaultPrinterId.value = defaultPrinter?.id

            val backupPrinter = printersList.find { it.isBackup }
            _backupPrinterId.value = backupPrinter?.id

            _uiState.value = PrinterSetupUiState.Success(printersList)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading printers", e)
            _uiState.value = PrinterSetupUiState.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Add a new printer
     */
    fun addPrinter(printerInfo: PrinterInfo) {
        viewModelScope.launch {
            try {
                printService.addPrinter(printerInfo)
                loadPrinters()
            } catch (e: Exception) {
                _uiState.value = PrinterSetupUiState.Error(e.message ?: "Failed to add printer")
            }
        }
    }

    /**
     * Update an existing printer
     */
    fun updatePrinter(
        id: String,
        name: String,
        address: String,
        port: Int,
        model: String,
        paperSize: PrintService.PaperSize
    ) {
        viewModelScope.launch {
            try {
                val printer = PrintService.PrinterInfo(
                    id = id,
                    name = name,
                    type = PrintService.PrinterType.NETWORK, // 默认使用网络打印机
                    address = address,
                    port = port,
                    model = model,
                    paperSize = paperSize,
                    isDefault = false,
                    isBackup = false,
                    autoPrint = false
                )
                printService.addPrinter(printer)
                loadPrinters()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating printer", e)
            }
        }
    }

    /**
     * Delete a printer
     */
    suspend fun deletePrinter(id: String) {
        try {
            _uiState.value = PrinterSetupUiState.Loading
            val printers = _printers.value.toMutableList()
            printers.removeAll { it.id == id }
            _printers.value = printers
            _uiState.value = PrinterSetupUiState.Success(printers)
        } catch (e: Exception) {
            _uiState.value = PrinterSetupUiState.Error("Failed to delete printer")
        }
    }

    /**
     * Set a printer as the default
     */
    fun setDefaultPrinter(printerId: String) {
        viewModelScope.launch {
            try {
                printService.setDefaultPrinter(printerId)
                loadPrinters()
            } catch (e: Exception) {
                _uiState.value = PrinterSetupUiState.Error(e.message ?: "Failed to set default printer")
            }
        }
    }

    /**
     * Set a printer as the backup
     */
    fun setBackupPrinter(printerId: String) {
        viewModelScope.launch {
            try {
                printService.setBackupPrinter(printerId)
                loadPrinters()
            } catch (e: Exception) {
                _uiState.value = PrinterSetupUiState.Error(e.message ?: "Failed to set backup printer")
            }
        }
    }

    /**
     * Update a printer's auto-print setting
     */
    fun updatePrinterAutoPrint(id: String, autoPrint: Boolean) {
        viewModelScope.launch {
            try {
                // Find the existing printer
                val existingPrinter = _printers.value.find { it.id == id } ?: return@launch

                // Create updated printer info
                val updatedPrinter = existingPrinter.copy(
                    autoPrint = autoPrint
                )

                // Update printer
                printService.addPrinter(updatedPrinter) // Uses the same ID, so it will update

                // Reload printers
                loadPrinters()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating printer auto-print", e)
            }
        }
    }

    /**
     * Test a printer
     */
    fun testPrinter(printerInfo: PrinterInfo) {
        viewModelScope.launch {
            try {
                val success = printService.testPrinterConnection(printerInfo)
                if (!success) {
                    _uiState.value = PrinterSetupUiState.Error("Printer test failed")
                }
            } catch (e: Exception) {
                _uiState.value = PrinterSetupUiState.Error(e.message ?: "Failed to test printer")
            }
        }
    }

    /**
     * Scan for available printers
     */
    suspend fun scanForPrinters(type: PrintService.PrinterType) {
        _scanningState.value = ScanningState.Scanning

        try {
            // Scan based on type
            when (type) {
                PrintService.PrinterType.BLUETOOTH -> scanForBluetoothPrinters()
                PrintService.PrinterType.NETWORK -> scanForNetworkPrinters()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning for printers", e)
            _scanningState.value = ScanningState.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Scan for Bluetooth printers
     */
    private fun scanForBluetoothPrinters() {
        try {
            // Get Bluetooth adapter
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

            if (bluetoothAdapter == null) {
                _scanningState.value = ScanningState.Error("Bluetooth not supported on this device")
                return
            }

            if (!bluetoothAdapter.isEnabled) {
                _scanningState.value = ScanningState.Error("Bluetooth is disabled")
                return
            }

            // Get paired devices
            val pairedDevices = bluetoothAdapter.bondedDevices

            if (pairedDevices.isEmpty()) {
                _scanningState.value = ScanningState.Error("No paired Bluetooth devices found")
                return
            }

            // Filter for likely printer devices (this is just a simplistic approach)
            val printerAddresses = pairedDevices
                .filter { device ->
                    if (ActivityCompat.checkSelfPermission(
                            getApplication(),
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return@filter false
                    }
                    device.name.contains("print", ignoreCase = true) ||
                            device.name.contains("pos", ignoreCase = true) ||
                            device.name.contains("thermal", ignoreCase = true) ||
                            device.name.contains("epson", ignoreCase = true) ||
                            device.name.contains("star", ignoreCase = true) ||
                            device.name.contains("zebra", ignoreCase = true)
                }
                .map { it.address }

            // Update scanning state
            if (printerAddresses.isEmpty()) {
                _scanningState.value = ScanningState.Error("No printer-like Bluetooth devices found")
            } else {
                _scanningState.value = ScanningState.Found(printerAddresses)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning for Bluetooth printers", e)
            _scanningState.value = ScanningState.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Scan for network printers
     */
    private fun scanForNetworkPrinters() {
        // In a real app, this would use NSD (Network Service Discovery) to find printers
        // For simplicity, we'll return some common printer addresses
        val commonPrinterAddresses = listOf(
            "192.168.1.100",
            "192.168.1.101",
            "192.168.0.100"
        )

        _scanningState.value = ScanningState.Found(commonPrinterAddresses)
    }
}

// Scanning State
sealed class ScanningState {
    data object Idle : ScanningState()
    data object Scanning : ScanningState()
    data class Found(val printers: List<String>) : ScanningState()
    data class Error(val message: String) : ScanningState()
}

// Printer Testing State
sealed class PrinterTestingState {
    data object Idle : PrinterTestingState()
    data object Testing : PrinterTestingState()
    data object Success : PrinterTestingState()
    data class Error(val message: String) : PrinterTestingState()
}

sealed class PrinterSetupUiState {
    object Loading : PrinterSetupUiState()
    data class Success(val printers: List<PrinterInfo>) : PrinterSetupUiState()
    data class Error(val message: String) : PrinterSetupUiState()
}