package com.example.wooauto.ui.settings.printer

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wooauto.R
import com.example.wooauto.ui.components.EmptyState
import com.example.wooauto.ui.settings.viewmodel.PrinterSetupViewModel
import com.example.wooauto.ui.settings.viewmodel.PrinterTestingState
import com.example.wooauto.ui.settings.viewmodel.ScanningState
import com.example.wooauto.utils.PrintService
import kotlinx.coroutines.launch
import java.util.*

data class PrinterFormState(
    val name: String = "",
    val type: PrintService.PrinterType = PrintService.PrinterType.NETWORK,
    val address: String = "",
    val port: Int = 9100,
    val model: String = "",
    val paperSize: PrintService.PaperSize = PrintService.PaperSize.SIZE_80MM
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSetupScreen(
    onBackClick: () -> Unit,
    viewModel: PrinterSetupViewModel = viewModel()
) {
    val printers by viewModel.printers.collectAsState()
    val defaultPrinterId by viewModel.defaultPrinterId.collectAsState()
    val backupPrinterId by viewModel.backupPrinterId.collectAsState()
    val scanningState by viewModel.scanningState.collectAsState()
    val testingState by viewModel.testingState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Dialog state
    var showAddPrinterDialog by remember { mutableStateOf(false) }
    var showEditPrinterDialog by remember { mutableStateOf(false) }
    var selectedPrinterForEdit by remember { mutableStateOf<PrintService.PrinterInfo?>(null) }

    // Show snackbar for testing result
    LaunchedEffect(testingState) {
        if (testingState is PrinterTestingState.Success) {
            snackbarHostState.showSnackbar("Printer test successful!")
        } else if (testingState is PrinterTestingState.Error) {
            val error = (testingState as PrinterTestingState.Error).message
            snackbarHostState.showSnackbar("Printer test failed: $error")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.printer_setup)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Add Printer Button
                Button(
                    onClick = { showAddPrinterDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.add_printer))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Printers List
                if (printers.isEmpty()) {
                    EmptyState(
                        message = "No printers added yet. Click the button above to add a printer.",
                        icon = R.drawable.ic_print
                    )
                } else {
                    Text(
                        text = "Configured Printers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(printers) { printer ->
                            PrinterItem(
                                printer = printer,
                                isDefault = printer.id == defaultPrinterId,
                                isBackup = printer.id == backupPrinterId,
                                onEditClick = {
                                    selectedPrinterForEdit = printer
                                    showEditPrinterDialog = true
                                },
                                onDeleteClick = {
                                    scope.launch {
                                        viewModel.deletePrinter(printer.id)
                                    }
                                },
                                onSetAsDefaultClick = {
                                    viewModel.setDefaultPrinter(printer.id)
                                },
                                onSetAsBackupClick = {
                                    viewModel.setBackupPrinter(printer.id)
                                },
                                onTestClick = {
                                    viewModel.testPrinter(printer.id)
                                },
                                onAutoPrintChanged = { enabled ->
                                    viewModel.updatePrinterAutoPrint(printer.id, enabled)
                                }
                            )
                        }
                    }
                }
            }

            // Scanning indicator
            if (scanningState is ScanningState.Scanning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Scanning for printers...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }

    // Add Printer Dialog
    if (showAddPrinterDialog) {
        AddPrinterDialog(
            onDismiss = { showAddPrinterDialog = false },
            onAddPrinter = { name, type, address, port, model, paperSize ->
                val printerInfo = PrintService.PrinterInfo(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    type = type,
                    address = address,
                    port = port,
                    model = model,
                    paperSize = paperSize,
                    autoPrint = false,
                    isDefault = false,
                    isBackup = false
                )
                viewModel.addPrinter(printerInfo)
                showAddPrinterDialog = false
            },
            onScanPrinters = {
                scope.launch {
                    viewModel.scanForPrinters(it)
                }
            },
            scanningState = scanningState
        )
    }

    // Edit Printer Dialog
    if (showEditPrinterDialog && selectedPrinterForEdit != null) {
        EditPrinterDialog(
            printer = selectedPrinterForEdit!!,
            onDismiss = {
                showEditPrinterDialog = false
                selectedPrinterForEdit = null
            },
            onUpdatePrinter = { id, name, address, port, model, paperSize ->
                viewModel.updatePrinter(id, name, address, port, model, paperSize)
                showEditPrinterDialog = false
                selectedPrinterForEdit = null
            }
        )
    }
}

@Composable
fun PrinterItem(
    printer: PrintService.PrinterInfo,
    isDefault: Boolean,
    isBackup: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSetAsDefaultClick: () -> Unit,
    onSetAsBackupClick: () -> Unit,
    onTestClick: () -> Unit,
    onAutoPrintChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Printer name and type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = printer.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = when (printer.type) {
                            PrintService.PrinterType.BLUETOOTH -> "Bluetooth Printer"
                            PrintService.PrinterType.NETWORK -> "Network Printer"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Default/Backup badge
                if (isDefault) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                    ) {
                        Text(
                            text = "Default",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (isBackup) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                        )
                    ) {
                        Text(
                            text = "Backup",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Printer details
            Text(
                text = "Address: ${printer.address}" + if (printer.type == PrintService.PrinterType.NETWORK) ":${printer.port}" else "",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Model: ${printer.model}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Paper Size: ${
                    when (printer.paperSize) {
                        PrintService.PaperSize.SIZE_57MM -> "57mm"
                        PrintService.PaperSize.SIZE_80MM -> "80mm"
                        PrintService.PaperSize.SIZE_LETTER -> "Letter"
                    }
                }",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Auto-print toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Auto-Print",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.weight(1f))

                Switch(
                    checked = printer.autoPrint,
                    onCheckedChange = onAutoPrintChanged
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Test Button
                Button(
                    onClick = onTestClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Test")
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Edit Button
                Button(
                    onClick = onEditClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Edit")
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Delete Button
                Button(
                    onClick = onDeleteClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Delete")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Set as default/backup buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Set as Default Button
                Button(
                    onClick = onSetAsDefaultClick,
                    enabled = !isDefault,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Set as Default")
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Set as Backup Button
                Button(
                    onClick = onSetAsBackupClick,
                    enabled = !isBackup && !isDefault,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Set as Backup")
                }
            }
        }
    }
}

@Composable
fun AddPrinterDialog(
    onDismiss: () -> Unit,
    onAddPrinter: (name: String, type: PrintService.PrinterType, address: String, port: Int, model: String, paperSize: PrintService.PaperSize) -> Unit,
    onScanPrinters: (PrintService.PrinterType) -> Unit,
    scanningState: ScanningState
) {
    var printerName by remember { mutableStateOf("") }
    var printerType by remember { mutableStateOf(PrintService.PrinterType.BLUETOOTH) }
    var printerAddress by remember { mutableStateOf("") }
    var printerPort by remember { mutableStateOf("9100") }
    var printerModel by remember { mutableStateOf("Generic ESC/POS") }
    var paperSize by remember { mutableStateOf(PrintService.PaperSize.SIZE_80MM) }

    // Create list of scanned printers
    val scannedPrinters = when (scanningState) {
        is ScanningState.Found -> scanningState.printers
        else -> emptyList()
    }

    // Dropdown for scanned printers
    var printerDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Printer") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Printer Type Radio Buttons
                Text(
                    text = "Printer Type",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = printerType == PrintService.PrinterType.BLUETOOTH,
                                onClick = { printerType = PrintService.PrinterType.BLUETOOTH }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = printerType == PrintService.PrinterType.BLUETOOTH,
                            onClick = { printerType = PrintService.PrinterType.BLUETOOTH }
                        )
                        Text(
                            text = stringResource(R.string.bluetooth_printer),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = printerType == PrintService.PrinterType.NETWORK,
                                onClick = { printerType = PrintService.PrinterType.NETWORK }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = printerType == PrintService.PrinterType.NETWORK,
                            onClick = { printerType = PrintService.PrinterType.NETWORK }
                        )
                        Text(
                            text = stringResource(R.string.network_printer),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                // Printer Name
                OutlinedTextField(
                    value = printerName,
                    onValueChange = { printerName = it },
                    label = { Text(stringResource(R.string.printer_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Printer Address
                OutlinedTextField(
                    value = printerAddress,
                    onValueChange = { printerAddress = it },
                    label = { Text(stringResource(R.string.printer_address)) },
                    placeholder = {
                        Text(
                            text = when (printerType) {
                                PrintService.PrinterType.BLUETOOTH -> "00:11:22:33:44:55"
                                PrintService.PrinterType.NETWORK -> "192.168.1.100"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Port (only for network printers)
                if (printerType == PrintService.PrinterType.NETWORK) {
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = printerPort,
                        onValueChange = { printerPort = it },
                        label = { Text(stringResource(R.string.printer_port)) },
                        placeholder = { Text("9100") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Printer Model
                OutlinedTextField(
                    value = printerModel,
                    onValueChange = { printerModel = it },
                    label = { Text(stringResource(R.string.printer_model)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Paper Size Radio Buttons
                Text(
                    text = "Paper Size",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = paperSize == PrintService.PaperSize.SIZE_57MM,
                                onClick = { paperSize = PrintService.PaperSize.SIZE_57MM }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = paperSize == PrintService.PaperSize.SIZE_57MM,
                            onClick = { paperSize = PrintService.PaperSize.SIZE_57MM }
                        )
                        Text(
                            text = stringResource(R.string.size_57mm),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = paperSize == PrintService.PaperSize.SIZE_80MM,
                                onClick = { paperSize = PrintService.PaperSize.SIZE_80MM }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = paperSize == PrintService.PaperSize.SIZE_80MM,
                            onClick = { paperSize = PrintService.PaperSize.SIZE_80MM }
                        )
                        Text(
                            text = stringResource(R.string.size_80mm),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = paperSize == PrintService.PaperSize.SIZE_LETTER,
                                onClick = { paperSize = PrintService.PaperSize.SIZE_LETTER }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = paperSize == PrintService.PaperSize.SIZE_LETTER,
                            onClick = { paperSize = PrintService.PaperSize.SIZE_LETTER }
                        )
                        Text(
                            text = stringResource(R.string.size_letter),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scan for printers button
                Button(
                    onClick = { onScanPrinters(printerType) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = scanningState !is ScanningState.Scanning
                ) {
                    if (scanningState is ScanningState.Scanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.scan_printers))
                }

                // Show scanned printers
                if (scannedPrinters.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Found Printers",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Dropdown for scanned printers
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = printerAddress,
                            onValueChange = {},
                            label = { Text("Select a printer") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { printerDropdownExpanded = true }) {
                                    Icon(
                                        painter = painterResource(
                                            id = R.drawable.ic_dropdown
                                        ),
                                        contentDescription = "Dropdown"
                                    )
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = printerDropdownExpanded,
                            onDismissRequest = { printerDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            scannedPrinters.forEach { address ->
                                DropdownMenuItem(
                                    text = { Text(address) },
                                    onClick = {
                                        printerAddress = address
                                        printerDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAddPrinter(
                        printerName,
                        printerType,
                        printerAddress.toIntOrNull() ?: 9100,
                        printerModel,
                        paperSize
                    )
                },
                enabled = printerName.isNotBlank() && printerAddress.isNotBlank() && printerModel.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditPrinterDialog(
    printer: PrintService.PrinterInfo,
    onDismiss: () -> Unit,
    onUpdatePrinter: (String, String, String, Int, String, PrintService.PaperSize) -> Unit
) {
    var formState by remember { mutableStateOf(
        PrinterFormState(
            name = printer.name,
            type = printer.type,
            address = printer.address,
            port = printer.port,
            model = printer.model,
            paperSize = printer.paperSize
        )
    ) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Printer") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Printer Type (read-only)
                Text(
                    text = "Printer Type: " + when (printer.type) {
                        PrintService.PrinterType.BLUETOOTH -> "Bluetooth Printer"
                        PrintService.PrinterType.NETWORK -> "Network Printer"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Printer Name
                OutlinedTextField(
                    value = formState.name,
                    onValueChange = { formState = formState.copy(name = it) },
                    label = { Text(stringResource(R.string.printer_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Printer Address
                OutlinedTextField(
                    value = formState.address,
                    onValueChange = { formState = formState.copy(address = it) },
                    label = { Text(stringResource(R.string.printer_address)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Port (only for network printers)
                if (printer.type == PrintService.PrinterType.NETWORK) {
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = formState.port.toString(),
                        onValueChange = { formState = formState.copy(port = it.toIntOrNull() ?: 9100) },
                        label = { Text(stringResource(R.string.printer_port)) },
                        placeholder = { Text("9100") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Printer Model
                OutlinedTextField(
                    value = formState.model,
                    onValueChange = { formState = formState.copy(model = it) },
                    label = { Text(stringResource(R.string.printer_model)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Paper Size Radio Buttons
                Text(
                    text = "Paper Size",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = formState.paperSize == PrintService.PaperSize.SIZE_57MM,
                                onClick = { formState = formState.copy(paperSize = PrintService.PaperSize.SIZE_57MM) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = formState.paperSize == PrintService.PaperSize.SIZE_57MM,
                            onClick = { formState = formState.copy(paperSize = PrintService.PaperSize.SIZE_57MM) }
                        )
                        Text(
                            text = stringResource(R.string.size_57mm),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = formState.paperSize == PrintService.PaperSize.SIZE_80MM,
                                onClick = { formState = formState.copy(paperSize = PrintService.PaperSize.SIZE_80MM) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = formState.paperSize == PrintService.PaperSize.SIZE_80MM,
                            onClick = { formState = formState.copy(paperSize = PrintService.PaperSize.SIZE_80MM) }
                        )
                        Text(
                            text = stringResource(R.string.size_80mm),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = formState.paperSize == PrintService.PaperSize.SIZE_LETTER,
                                onClick = { formState = formState.copy(paperSize = PrintService.PaperSize.SIZE_LETTER) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = formState.paperSize == PrintService.PaperSize.SIZE_LETTER,
                            onClick = { formState = formState.copy(paperSize = PrintService.PaperSize.SIZE_LETTER) }
                        )
                        Text(
                            text = stringResource(R.string.size_letter),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onUpdatePrinter(
                        formState.name,
                        formState.address,
                        formState.port.toString(),
                        formState.model,
                        formState.paperSize
                    )
                },
                enabled = formState.name.isNotBlank() && formState.address.isNotBlank() && formState.model.isNotBlank()
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Cancel")
            }
        }
    )
}