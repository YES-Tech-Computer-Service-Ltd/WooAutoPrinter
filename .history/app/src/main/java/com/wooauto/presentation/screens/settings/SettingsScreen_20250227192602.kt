package com.wooauto.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wooauto.presentation.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API 设置
            item {
                ApiSettings(
                    apiUrl = uiState.apiUrl,
                    consumerKey = uiState.consumerKey,
                    consumerSecret = uiState.consumerSecret,
                    onApiUrlChange = { viewModel.setApiUrl(it) },
                    onConsumerKeyChange = { viewModel.setConsumerKey(it) },
                    onConsumerSecretChange = { viewModel.setConsumerSecret(it) }
                )
            }

            // 打印机设置
            item {
                PrinterSettings(
                    printerType = uiState.printerType,
                    isPrinterConnected = uiState.isPrinterConnected,
                    onPrinterTypeChange = { viewModel.setPrinterType(it) }
                )
            }

            // 通知设置
            item {
                NotificationSettings(
                    isEnabled = uiState.isNotificationEnabled,
                    onEnabledChange = { viewModel.setNotificationEnabled(it) }
                )
            }

            // 语言设置
            item {
                LanguageSettings(
                    currentLanguage = uiState.language,
                    onLanguageChange = { viewModel.setLanguage(it) }
                )
            }

            // 货币设置
            item {
                CurrencySettings(
                    currentCurrency = uiState.currency,
                    onCurrencyChange = { viewModel.setCurrency(it) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiSettings(
    apiUrl: String,
    consumerKey: String,
    consumerSecret: String,
    onApiUrlChange: (String) -> Unit,
    onConsumerKeyChange: (String) -> Unit,
    onConsumerSecretChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "API 设置",
                style = MaterialTheme.typography.titleMedium
            )
            
            TextField(
                value = apiUrl,
                onValueChange = onApiUrlChange,
                label = { Text("API URL") },
                modifier = Modifier.fillMaxWidth()
            )
            
            TextField(
                value = consumerKey,
                onValueChange = onConsumerKeyChange,
                label = { Text("Consumer Key") },
                modifier = Modifier.fillMaxWidth()
            )
            
            TextField(
                value = consumerSecret,
                onValueChange = onConsumerSecretChange,
                label = { Text("Consumer Secret") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrinterSettings(
    printerType: String,
    isPrinterConnected: Boolean,
    onPrinterTypeChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val printerTypes = listOf("USB", "Bluetooth", "Network")

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "打印机设置",
                style = MaterialTheme.typography.titleMedium
            )
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                TextField(
                    value = printerType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("打印机类型") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    printerTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                onPrinterTypeChange(type)
                                expanded = false
                            }
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "打印机状态: ${if (isPrinterConnected) "已连接" else "未连接"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = { /* 连接/断开打印机 */ }
                ) {
                    Text(if (isPrinterConnected) "断开连接" else "连接")
                }
            }
        }
    }
}

@Composable
private fun NotificationSettings(
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "新订单通知",
                style = MaterialTheme.typography.titleMedium
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSettings(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val languages = mapOf(
        "zh" to "中文",
        "en" to "English"
    )

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "语言设置",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                TextField(
                    value = languages[currentLanguage] ?: currentLanguage,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("选择语言") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    languages.forEach { (code, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                onLanguageChange(code)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencySettings(
    currentCurrency: String,
    onCurrencyChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currencies = mapOf(
        "CNY" to "人民币 (¥)",
        "USD" to "美元 ($)",
        "EUR" to "欧元 (€)",
        "GBP" to "英镑 (£)"
    )

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "货币设置",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                TextField(
                    value = currencies[currentCurrency] ?: currentCurrency,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("选择货币") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    currencies.forEach { (code, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                onCurrencyChange(code)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
} 