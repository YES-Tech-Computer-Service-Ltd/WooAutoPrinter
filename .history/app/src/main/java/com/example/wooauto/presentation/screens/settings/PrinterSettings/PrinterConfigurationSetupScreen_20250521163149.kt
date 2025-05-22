package com.example.wooauto.presentation.screens.settings.PrinterSettings

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.R
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.presentation.screens.settings.SettingsViewModel
import kotlinx.coroutines.launch
import java.util.UUID

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

    // val isNewPrinterSetup = printerId == null // This can be simplified
    
    // Load existing config if editing, or create a new one for a newly selected device, or a truly new one
    val (initialPrinterConfig, isTrulyNewPrinterEvent) = remember(printerId, initialName, initialAddress) {
        if (printerId != null) { // Editing existing
            viewModel.getPrinterConfig(printerId)?.let { Pair(it, false) }
                // Should not happen if printerId is valid, but as a fallback:
                ?: Pair(PrinterConfig(id = printerId, name = "", address = "", type = PrinterConfig.PRINTER_TYPE_BLUETOOTH), true)
        } else { // New printer setup
            Pair(PrinterConfig(
                id = UUID.randomUUID().toString(), // Always generate a new ID for a new configuration
                name = initialName ?: "",
                address = initialAddress ?: "", // This will be from selected device
                type = PrinterConfig.PRINTER_TYPE_BLUETOOTH // Default type
            ), true)
        }
    }

    var name by remember(initialPrinterConfig.name) { mutableStateOf(initialPrinterConfig.name) }
    // Address is sourced from initialAddress (for new from scan) or existing config, generally not user-editable here.
    val address by remember(initialPrinterConfig.address) { mutableStateOf(initialPrinterConfig.address) }
    // Type is not editable for now, defaults to Bluetooth
    val type by remember { mutableStateOf(initialPrinterConfig.type) } // Should be from initialPrinterConfig.type
    var paperWidth by remember(initialPrinterConfig.paperWidth) { mutableStateOf(initialPrinterConfig.paperWidth.toString()) }
    var isDefault by remember(initialPrinterConfig.isDefault) { mutableStateOf(initialPrinterConfig.isDefault) }
    var autoCut by remember(initialPrinterConfig.autoCut) { mutableStateOf(initialPrinterConfig.autoCut) }

    // Determine the title based on whether it's an edit or a new setup
    val screenTitle = if (printerId != null && !isTrulyNewPrinterEvent) { // Editing existing printer
        stringResource(id = R.string.edit_printer)
    } else { // Setting up a new printer (either from scan or "+" button directly if that flow existed)
        stringResource(id = R.string.setup_printer)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
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
            // Name Field: Editable. For new printers from scan, pre-filled but user can change.
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.printer_name)) },
                modifier = Modifier.fillMaxWidth()
            )

            // Address Field: Read-only. Displayed for confirmation.
            OutlinedTextField(
                value = address, // This comes from initialAddress or existing config
                onValueChange = { /* Not editable by user */ },
                label = { Text(stringResource(R.string.printer_address)) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true
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
                    if (name.isBlank() || address.isBlank()) { // address check is more for safety, should always be there
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(missingFieldsMessage)
                        }
                        return@Button
                    }
                    
                    // Use initialPrinterConfig.id for existing printers, or the newly generated UUID for new ones.
                    // The `initialPrinterConfig` already holds the correct ID.
                    val configToSave = initialPrinterConfig.copy(
                        name = name,
                        // address is taken from `val address` which is `initialPrinterConfig.address`
                        // type is taken from `val type` which is `initialPrinterConfig.type`
                        paperWidth = paperWidth.toIntOrNull() ?: PrinterConfig.PAPER_WIDTH_57MM,
                        isDefault = isDefault,
                        autoCut = autoCut
                        // brand is not set here, it's determined by PrinterManager
                    )
                    
                    viewModel.savePrinterConfig(configToSave)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(configSavedMessage)
                    }
                    navController.popBackStack() // Go back to PrinterSettingsScreen
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