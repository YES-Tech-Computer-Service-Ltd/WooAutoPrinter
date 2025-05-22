package com.example.wooauto.presentation.screens.settings.PrinterSettings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.R
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.presentation.screens.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSettingsDialogContent(
    viewModel: SettingsViewModel = hiltViewModel(),
    navController: NavController? = null, // 可选的NavController，用于导航
    onClose: () -> Unit
) {
    val printerConfigs by viewModel.printerConfigs.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.printer_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                // 在这里处理添加打印机的逻辑
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_printer))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (printerConfigs.isEmpty()) Arrangement.Center else Arrangement.Top
        ) {
            if (printerConfigs.isEmpty()) {
                Text(text = stringResource(R.string.no_printers))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    // 添加打印机逻辑
                }) {
                    Text(text = stringResource(R.string.add_printer))
                }
            } else {
                // 显示打印机列表
                printerConfigs.forEach { printerConfig ->
                    PrinterListItem(
                        printerConfig = printerConfig,
                        onEdit = {
                            // 尝试导航到PrinterConfigScreen
                            navController?.navigate("printer_config_screen/${printerConfig.id}")
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun PrinterListItem(
    printerConfig: PrinterConfig,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = printerConfig.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = printerConfig.address,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (printerConfig.isDefault) {
                    Text(
                        text = stringResource(R.string.default_printer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit)
                )
            }
        }
    }
} 