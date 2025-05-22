package com.example.wooauto.presentation.screens.settings.PrinterSettings

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterConfigScreen(
    navController: NavController,
    printerId: String,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 从ViewModel加载当前的打印机配置
    LaunchedEffect(printerId) {
        viewModel.loadPrinterConfigById(printerId)
    }

    val printerConfigState = viewModel.currentEditPrinterConfig.collectAsState()
    val originalConfig = printerConfigState.value

    // 如果没有加载到配置，显示加载中或错误信息
    if (originalConfig == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.printer_configuration)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text(stringResource(id = R.string.loading_printer_config)) // 需要添加此字符串资源
            }
        }
        return
    }

    // 使用可变状态来跟踪配置更改
    var paperWidth by remember(originalConfig.paperWidth) { mutableStateOf(originalConfig.paperWidth) }
    var isDefault by remember(originalConfig.isDefault) { mutableStateOf(originalConfig.isDefault) }
    var isAutoPrint by remember(originalConfig.isAutoPrint) { mutableStateOf(originalConfig.isAutoPrint) }
    var printCopies by remember(originalConfig.printCopies) { mutableStateOf(originalConfig.printCopies) }
    var autoCut by remember(originalConfig.autoCut) { mutableStateOf(originalConfig.autoCut) }


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.printer_configuration)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val updatedConfig = originalConfig.copy(
                            paperWidth = paperWidth,
                            isDefault = isDefault,
                            isAutoPrint = isAutoPrint,
                            printCopies = printCopies,
                            autoCut = autoCut
                        )
                        viewModel.savePrinterConfig(updatedConfig)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = navController.context.getString(R.string.printer_config_saved_successfully) // 需要添加此字符串资源
                            )
                        }
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save))
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 纸张宽度
            item {
                ConfigSectionTitle(title = stringResource(R.string.paper_width))
                PaperWidthSelector(
                    selectedWidth = paperWidth,
                    onWidthSelected = { paperWidth = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 默认打印机
            item {
                SwitchSettingItem(
                    title = stringResource(R.string.set_as_default_printer),
                    checked = isDefault,
                    onCheckedChange = { isDefault = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // 自动打印
            item {
                 SwitchSettingItem(
                    title = stringResource(R.string.auto_print_new_orders),
                    checked = isAutoPrint,
                    onCheckedChange = { isAutoPrint = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 打印份数
            item {
                ConfigSectionTitle(title = stringResource(R.string.print_copies))
                PrintCopiesSelector(
                    copies = printCopies,
                    onCopiesChanged = { printCopies = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // 自动切纸
            item {
                SwitchSettingItem(
                    title = stringResource(R.string.auto_cut_paper),
                    checked = autoCut,
                    onCheckedChange = { autoCut = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun ConfigSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperWidthSelector(selectedWidth: Int, onWidthSelected: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selectedWidth == PrinterConfig.PAPER_WIDTH_58MM,
            onClick = { onWidthSelected(PrinterConfig.PAPER_WIDTH_58MM) },
            label = { Text(stringResource(R.string.paper_width_58mm)) }
        )
        FilterChip(
            selected = selectedWidth == PrinterConfig.PAPER_WIDTH_80MM,
            onClick = { onWidthSelected(PrinterConfig.PAPER_WIDTH_80MM) },
            label = { Text(stringResource(R.string.paper_width_80mm)) }
        )
    }
}

@Composable
fun SwitchSettingItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintCopiesSelector(copies: Int, onCopiesChanged: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { if (copies > 1) onCopiesChanged(copies - 1) }, enabled = copies > 1) {
            Text("-")
        }
        Text(text = copies.toString(), modifier = Modifier.padding(horizontal = 16.dp))
        OutlinedButton(onClick = { if (copies < 5) onCopiesChanged(copies + 1) }, enabled = copies < 5) { // 假设最多5份
            Text("+")
        }
    }
}

// Helper extension functions in PrinterConfig.kt or a new utility file
fun PrinterConfig.FontSize.toStringResource(): Int {
    return when (this) {
        PrinterConfig.FontSize.SMALL -> R.string.font_size_small
        PrinterConfig.FontSize.MEDIUM -> R.string.font_size_medium
        PrinterConfig.FontSize.LARGE -> R.string.font_size_large
    }
}

fun PrinterConfig.PrintDensity.toStringResource(): Int {
    return when (this) {
        PrinterConfig.PrintDensity.LIGHT -> R.string.print_density_light
        PrinterConfig.PrintDensity.NORMAL -> R.string.print_density_normal
        PrinterConfig.PrintDensity.DARK -> R.string.print_density_dark
    }
}

fun PrinterConfig.PrintSpeed.toStringResource(): Int {
    return when (this) {
        PrinterConfig.PrintSpeed.SLOW -> R.string.print_speed_slow
        PrinterConfig.PrintSpeed.NORMAL -> R.string.print_speed_normal
        PrinterConfig.PrintSpeed.FAST -> R.string.print_speed_fast
    }
} 