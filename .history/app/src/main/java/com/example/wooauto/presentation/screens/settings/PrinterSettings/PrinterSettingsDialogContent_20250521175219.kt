package com.example.wooauto.presentation.screens.settings.PrinterSettings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wooauto.R
import com.example.wooauto.presentation.screens.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSettingsDialogContent(
    viewModel: SettingsViewModel = hiltViewModel(),
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (printerConfigs.isEmpty()) {
                Text(text = stringResource(R.string.no_printers))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    // 在这里实现添加打印机的逻辑，或者创建一个更详细的组件
                }) {
                    Text(text = stringResource(R.string.add_printer))
                }
            } else {
                Text(text = "您已配置了 ${printerConfigs.size} 台打印机")
                // 这里您可以添加更多显示打印机列表和详细设置的代码
                // 但目前我们只是提供一个最简单的实现来解决编译错误
            }
        }
    }
} 