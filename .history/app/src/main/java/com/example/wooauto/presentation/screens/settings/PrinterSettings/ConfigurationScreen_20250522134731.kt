package com.example.wooauto.presentation.screens.settings.PrinterSettings

import android.util.Log
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wooauto.R
import com.example.wooauto.presentation.screens.settings.SettingsViewModel

private const val TAG = "ConfigurationScreen"

@OptIn(ExperimentalMaterial3Api::class)
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
    Log.d(TAG, "ConfigurationScreen 开始构建: name=$name, address=$address")
    
    val connectionErrorMessage by viewModel.connectionErrorMessage.collectAsState()
    
    // 添加自动切纸设置状态
    var autoCut by remember { mutableStateOf(viewModel.currentPrinterConfig.value?.autoCut ?: false) }
    
    // 添加调试信息
    val context = LocalContext.current
    
    // 显示提示信息确认组件已加载
    LaunchedEffect(key1 = Unit) {
        Log.d(TAG, "ConfigurationScreen LaunchedEffect 触发")
        try {
            snackbarHostState.showSnackbar("配置界面已加载，请设置打印机参数")
            Log.d(TAG, "Snackbar 显示成功")
        } catch (e: Exception) {
            Log.e(TAG, "Snackbar 显示失败: ${e.message}")
        }
        
        // 打印调试信息
        Log.d(TAG, "组件初始化参数: name=$name, address=$address, paperWidth=$paperWidth, isDefault=$isDefault")
    }
    
    LaunchedEffect(connectionErrorMessage) {
        Log.d(TAG, "连接错误消息变化: $connectionErrorMessage")
        connectionErrorMessage?.let {
            try {
                snackbarHostState.showSnackbar(it)
                Log.d(TAG, "错误消息 Snackbar 显示成功")
            } catch (e: Exception) {
                Log.e(TAG, "错误消息 Snackbar 显示失败: ${e.message}")
            }
        }
    }
    
    Log.d(TAG, "开始构建UI组件")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Log.d(TAG, "Column 构建中")
        
        // 标题 - 添加更明显的样式
        Text(
            text = stringResource(id = R.string.printer_configuration),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        HorizontalDivider(
            thickness = 2.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        
        // 打印机基本信息
        Column {
            Log.d(TAG, "基本信息部分构建中")
            Text(
                text = stringResource(id = R.string.printer_basic_info),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 打印机名称输入框 - 添加更明显的颜色
            OutlinedTextField(
                value = name,
                onValueChange = { 
                    Log.d(TAG, "名称更改为: $it")
                    onNameChange(it)
                },
                label = { Text(stringResource(id = R.string.printer_name)) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 打印机地址输入框
            OutlinedTextField(
                value = address,
                onValueChange = { onAddressChange(it) },
                label = { Text(stringResource(id = R.string.printer_address)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = false, // 设备地址通常不需要手动编辑
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    disabledBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    disabledLabelColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
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
                    text = stringResource(id = R.string.set_as_default),
                    modifier = Modifier.clickable { onIsDefaultChange(!isDefault) }
                )
            }
            
            // 添加自动切纸选项
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
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 保存按钮
        Button(
            onClick = {
                Log.d(TAG, "保存按钮点击")
                // 保存时更新autoCut属性
                Log.d(TAG, "更新autoCut: $autoCut")
                viewModel.updateAutoCut(autoCut)
                Log.d(TAG, "调用onSave回调")
                onSave()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = stringResource(id = R.string.save)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(id = R.string.save_printer_config))
        }
    }
    Log.d(TAG, "ConfigurationScreen 构建完成")
} 