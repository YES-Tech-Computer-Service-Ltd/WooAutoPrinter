package com.example.wooauto.presentation.screens.settings.PrinterSettings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.wooauto.R
import com.example.wooauto.presentation.screens.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationScreen(
    paperWidth: String,
    onPaperWidthChange: (String) -> Unit,
    isDefault: Boolean,
    onIsDefaultChange: (Boolean) -> Unit,
    isAutoPrint: Boolean = false,
    onIsAutoPrintChange: (Boolean) -> Unit = {},
    onSave: () -> Unit,
    onClose: () -> Unit,
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    name: String = "",
    onNameChange: (String) -> Unit = {},
    address: String = "",
    onAddressChange: (String) -> Unit = {}
) {
    val connectionErrorMessage by viewModel.connectionErrorMessage.collectAsState()
    
    LaunchedEffect(connectionErrorMessage) {
        connectionErrorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }
    
    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // 顶部标题栏
                TopAppBar(
                    title = { Text(stringResource(id = R.string.printer_configuration)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(id = R.string.close))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    )
                )
                
                // 内容区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 打印机基本信息
                    Column {
                        Text(
                            text = stringResource(id = R.string.printer_basic_info),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 打印机名称输入框
                        OutlinedTextField(
                            value = name,
                            onValueChange = onNameChange,
                            label = { Text(stringResource(id = R.string.printer_name)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 打印机地址输入框
                        OutlinedTextField(
                            value = address,
                            onValueChange = onAddressChange,
                            label = { Text(stringResource(id = R.string.printer_address)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false // 设备地址通常不需要手动编辑
                        )
                    }
                    
                    HorizontalDivider()
                    
                    // 纸张宽度设置
                    Column {
                        Text(
                            text = stringResource(id = R.string.paper_width_setting),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val paperWidthOptions = remember {
                            listOf(
                                "58" to R.string.paper_width_58mm,
                                "80" to R.string.paper_width_80mm
                            )
                        }
                        
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            paperWidthOptions.forEach { (width, textResId) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPaperWidthChange(width) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = paperWidth == width,
                                        onClick = { onPaperWidthChange(width) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(id = textResId),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                    
                    HorizontalDivider()
                    
                    // 打印机选项
                    Column {
                        Text(
                            text = stringResource(id = R.string.printer_options),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = isDefault,
                                onCheckedChange = onIsDefaultChange
                            )
                            Text(
                                text = stringResource(id = R.string.set_as_default_printer),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.clickable { onIsDefaultChange(!isDefault) }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = isAutoPrint,
                                onCheckedChange = onIsAutoPrintChange
                            )
                            Text(
                                text = stringResource(id = R.string.enable_auto_print),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.clickable { onIsAutoPrintChange(!isAutoPrint) }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 保存按钮
                    Button(
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.save))
                    }
                }
            }
        }
    }
} 