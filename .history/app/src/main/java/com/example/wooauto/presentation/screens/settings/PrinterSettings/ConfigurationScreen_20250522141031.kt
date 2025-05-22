package com.example.wooauto.presentation.screens.settings.PrinterSettings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wooauto.R
import com.example.wooauto.presentation.screens.settings.SettingsViewModel

@Composable
fun ConfigurationScreen(
    paperWidth: String,
    onPaperWidthChange: (String) -> Unit,
    isDefault: Boolean,
    onIsDefaultChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    paddingValues: PaddingValues,
    name: String = "",
    onNameChange: (String) -> Unit = {},
    address: String = "",
    onAddressChange: (String) -> Unit = {}
) {
    val connectionErrorMessage by viewModel.connectionErrorMessage.collectAsState()
    
    // 添加自动切纸设置状态
    var autoCut by remember { mutableStateOf(viewModel.currentPrinterConfig.value?.autoCut ?: false) }
    
    LaunchedEffect(connectionErrorMessage) {
        connectionErrorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Text(
            text = stringResource(id = R.string.printer_configuration),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        HorizontalDivider()
        
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
            
            var expanded by remember { mutableStateOf(false) }
            
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
                            modifier = Modifier.clickable { expanded = true }
                        )
                    }
                )
                
                // 点击整个文本框时展开下拉菜单
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expanded = true },
                )
                
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.paper_width_58mm)) },
                        onClick = {
                            onPaperWidthChange("58")
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.paper_width_80mm)) },
                        onClick = {
                            onPaperWidthChange("80")
                            expanded = false
                        }
                    )
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
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
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