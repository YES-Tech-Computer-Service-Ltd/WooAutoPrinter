package com.example.wooauto.presentation.screens.settings.PrinterSettings

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
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
fun PrinterDetailsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navController: NavController,
    printerId: String?
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 判断是添加新打印机还是编辑现有打印机
    val isNewPrinter = printerId == null || printerId == "new"
    
    // 获取打印机配置
    val printerConfig by remember {
        mutableStateOf(
            if (isNewPrinter) {
                // 如果是新打印机，优先使用临时配置
                viewModel.getTempPrinterConfig() ?: PrinterConfig(
                    id = UUID.randomUUID().toString(),
                    name = "",
                    address = "",
                    type = PrinterConfig.PRINTER_TYPE_BLUETOOTH
                )
            } else {
                viewModel.getPrinterConfig(printerId!!) ?: PrinterConfig(
                    id = UUID.randomUUID().toString(),
                    name = "",
                    address = "",
                    type = PrinterConfig.PRINTER_TYPE_BLUETOOTH
                )
            }
        )
    }
    
    // 表单状态
    var name by remember { mutableStateOf(printerConfig.name) }
    var address by remember { mutableStateOf(printerConfig.address) }
    val type by remember { mutableStateOf(printerConfig.type) }
    var paperWidth by remember { mutableStateOf(printerConfig.paperWidth.toString()) }
    var isDefault by remember { mutableStateOf(printerConfig.isDefault) }
    val autoCut by remember { mutableStateOf(printerConfig.autoCut) }
    
    // 当离开页面时清除临时配置
    LaunchedEffect(Unit) {
        return@LaunchedEffect
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isNewPrinter) {
                            stringResource(id = R.string.setup_printer)
                        } else {
                            stringResource(id = R.string.edit_printer)
                        }
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        // 如果是新打印机并且返回，清除临时配置
                        if (isNewPrinter) {
                            viewModel.clearTempPrinterConfig()
                        }
                        navController.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
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
        // 在lambda外部获取字符串资源
        val missingFieldsMessage = stringResource(id = R.string.please_enter_name_address)
        val configSavedMessage = stringResource(id = R.string.printer_config_saved)
        
        // 显示配置表单
        ConfigurationScreen(
            paperWidth = paperWidth,
            onPaperWidthChange = { paperWidth = it },
            isDefault = isDefault,
            onIsDefaultChange = { isDefault = it },
            onSave = {
                // 验证必填字段
                if (name.isBlank() || address.isBlank()) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(missingFieldsMessage)
                    }
                    return@ConfigurationScreen
                }
                
                // 保存打印机配置
                val updatedConfig = printerConfig.copy(
                    name = name,
                    address = address,
                    type = type,
                    paperWidth = paperWidth.toIntOrNull() ?: PrinterConfig.PAPER_WIDTH_57MM,
                    isDefault = isDefault,
                    brand = printerConfig.brand,
                    autoCut = autoCut
                )
                
                viewModel.savePrinterConfig(updatedConfig)
                
                // 如果是新打印机，清除临时配置
                if (isNewPrinter) {
                    viewModel.clearTempPrinterConfig()
                }
                
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(configSavedMessage)
                }
                
                // 返回上一页
                navController.popBackStack()
            },
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
            paddingValues = paddingValues
        )
    }
} 