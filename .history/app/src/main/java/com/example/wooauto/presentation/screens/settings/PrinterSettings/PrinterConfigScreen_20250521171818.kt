package com.example.wooauto.presentation.screens.settings.PrinterSettings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.wooauto.R
import com.example.wooauto.domain.models.PrinterConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterConfigScreen(
    printerConfig: PrinterConfig,
    onSave: (PrinterConfig) -> Unit,
    onBack: () -> Unit
) {
    var paperWidth by remember { mutableStateOf(printerConfig.paperWidth) }
    var isDefault by remember { mutableStateOf(printerConfig.isDefault) }
    var isAutoPrint by remember { mutableStateOf(printerConfig.isAutoPrint) }
    var printCopies by remember { mutableStateOf(printerConfig.printCopies) }
    var fontSize by remember { mutableStateOf(printerConfig.fontSize) }
    var printDensity by remember { mutableStateOf(printerConfig.printDensity) }
    var printSpeed by remember { mutableStateOf(printerConfig.printSpeed) }
    var autoCut by remember { mutableStateOf(printerConfig.autoCut) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.printer_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onSave(
                                printerConfig.copy(
                                    paperWidth = paperWidth,
                                    isDefault = isDefault,
                                    isAutoPrint = isAutoPrint,
                                    printCopies = printCopies,
                                    fontSize = fontSize,
                                    printDensity = printDensity,
                                    printSpeed = printSpeed,
                                    autoCut = autoCut
                                )
                            )
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 纸张宽度选择
            Text(
                text = stringResource(id = R.string.paper_width),
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = paperWidth == PrinterConfig.PAPER_WIDTH_57MM,
                    onClick = { paperWidth = PrinterConfig.PAPER_WIDTH_57MM },
                    label = { Text("57mm") }
                )
                FilterChip(
                    selected = paperWidth == PrinterConfig.PAPER_WIDTH_80MM,
                    onClick = { paperWidth = PrinterConfig.PAPER_WIDTH_80MM },
                    label = { Text("80mm") }
                )
            }

            // 默认打印机设置
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.set_as_default),
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = isDefault,
                    onCheckedChange = { isDefault = it }
                )
            }

            // 自动打印设置
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.auto_print_new_orders),
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = isAutoPrint,
                    onCheckedChange = { isAutoPrint = it }
                )
            }

            // 打印份数
            Text(
                text = stringResource(id = R.string.print_copies),
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = printCopies.toFloat(),
                onValueChange = { printCopies = it.toInt() },
                valueRange = 1f..5f,
                steps = 3
            )
            Text(
                text = "$printCopies ${stringResource(id = R.string.copies)}",
                style = MaterialTheme.typography.bodyMedium
            )

            // 字体大小
            Text(
                text = stringResource(id = R.string.font_size),
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = fontSize == PrinterConfig.FONT_SIZE_SMALL,
                    onClick = { fontSize = PrinterConfig.FONT_SIZE_SMALL },
                    label = { Text(stringResource(id = R.string.small)) }
                )
                FilterChip(
                    selected = fontSize == PrinterConfig.FONT_SIZE_NORMAL,
                    onClick = { fontSize = PrinterConfig.FONT_SIZE_NORMAL },
                    label = { Text(stringResource(id = R.string.normal)) }
                )
                FilterChip(
                    selected = fontSize == PrinterConfig.FONT_SIZE_LARGE,
                    onClick = { fontSize = PrinterConfig.FONT_SIZE_LARGE },
                    label = { Text(stringResource(id = R.string.large)) }
                )
            }

            // 打印浓度
            Text(
                text = stringResource(id = R.string.print_density),
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = printDensity == PrinterConfig.PRINT_DENSITY_LIGHT,
                    onClick = { printDensity = PrinterConfig.PRINT_DENSITY_LIGHT },
                    label = { Text(stringResource(id = R.string.light)) }
                )
                FilterChip(
                    selected = printDensity == PrinterConfig.PRINT_DENSITY_NORMAL,
                    onClick = { printDensity = PrinterConfig.PRINT_DENSITY_NORMAL },
                    label = { Text(stringResource(id = R.string.normal)) }
                )
                FilterChip(
                    selected = printDensity == PrinterConfig.PRINT_DENSITY_DARK,
                    onClick = { printDensity = PrinterConfig.PRINT_DENSITY_DARK },
                    label = { Text(stringResource(id = R.string.dark)) }
                )
            }

            // 打印速度
            Text(
                text = stringResource(id = R.string.print_speed),
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = printSpeed == PrinterConfig.PRINT_SPEED_SLOW,
                    onClick = { printSpeed = PrinterConfig.PRINT_SPEED_SLOW },
                    label = { Text(stringResource(id = R.string.slow)) }
                )
                FilterChip(
                    selected = printSpeed == PrinterConfig.PRINT_SPEED_NORMAL,
                    onClick = { printSpeed = PrinterConfig.PRINT_SPEED_NORMAL },
                    label = { Text(stringResource(id = R.string.normal)) }
                )
                FilterChip(
                    selected = printSpeed == PrinterConfig.PRINT_SPEED_FAST,
                    onClick = { printSpeed = PrinterConfig.PRINT_SPEED_FAST },
                    label = { Text(stringResource(id = R.string.fast)) }
                )
            }

            // 自动切纸
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.auto_cut),
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = autoCut,
                    onCheckedChange = { autoCut = it }
                )
            }
        }
    }
} 