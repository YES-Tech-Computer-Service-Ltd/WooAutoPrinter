package com.wooauto.presentation.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    var isExpanded by remember { mutableStateOf(false) }
    var showSecret by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Api, "API 设置")
                    Text(
                        text = "API 设置",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        "展开/收起"
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = apiUrl,
                        onValueChange = onApiUrlChange,
                        label = { Text("API URL") },
                        leadingIcon = { Icon(Icons.Default.Link, "URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = consumerKey,
                        onValueChange = onConsumerKeyChange,
                        label = { Text("Consumer Key") },
                        leadingIcon = { Icon(Icons.Default.Key, "Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = consumerSecret,
                        onValueChange = onConsumerSecretChange,
                        label = { Text("Consumer Secret") },
                        leadingIcon = { Icon(Icons.Default.Lock, "Secret") },
                        trailingIcon = {
                            IconButton(onClick = { showSecret = !showSecret }) {
                                Icon(
                                    if (showSecret) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    "显示/隐藏密钥"
                                )
                            }
                        },
                        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
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

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Print, "打印机设置")
                Text(
                    text = "打印机设置",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = printerType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("打印机类型") },
                    leadingIcon = { 
                        Icon(
                            when (printerType) {
                                "USB" -> Icons.Default.Usb
                                "Bluetooth" -> Icons.Default.Bluetooth
                                "Network" -> Icons.Default.Wifi
                                else -> Icons.Default.Print
                            },
                            "打印机类型"
                        )
                    },
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
                            },
                            leadingIcon = {
                                Icon(
                                    when (type) {
                                        "USB" -> Icons.Default.Usb
                                        "Bluetooth" -> Icons.Default.Bluetooth
                                        "Network" -> Icons.Default.Wifi
                                        else -> Icons.Default.Print
                                    },
                                    type
                                )
                            }
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isPrinterConnected) Icons.Default.CheckCircle else Icons.Default.Error,
                        "打印机状态",
                        tint = if (isPrinterConnected) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = if (isPrinterConnected) "已连接" else "未连接",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                FilledTonalButton(
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Notifications, "通知设置")
                Text(
                    text = "新订单通知",
                    style = MaterialTheme.typography.titleLarge
                )
            }
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

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Language, "语言设置")
                Text(
                    text = "语言设置",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = languages[currentLanguage] ?: currentLanguage,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("选择语言") },
                    leadingIcon = { Icon(Icons.Default.Language, "语言") },
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

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MonetizationOn, "货币设置")
                Text(
                    text = "货币设置",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = currencies[currentCurrency] ?: currentCurrency,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("选择货币") },
                    leadingIcon = { Icon(Icons.Default.MonetizationOn, "货币") },
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