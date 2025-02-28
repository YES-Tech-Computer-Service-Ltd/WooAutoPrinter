package com.wooauto.presentation.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import com.wooauto.R
import com.wooauto.presentation.viewmodels.SettingsViewModel
import androidx.compose.material.icons.filled.Settings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun SettingsScreen(
    onNavigateToWebsiteSetup: () -> Unit,
    onNavigateToPrinterSetup: () -> Unit,
    onNavigateToSoundSetup: () -> Unit,
    onNavigateToLanguageSetup: () -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // API 设置
            ApiSettings(
                apiUrl = uiState.apiUrl,
                consumerKey = uiState.consumerKey,
                consumerSecret = uiState.consumerSecret,
                onApiUrlChange = viewModel::updateApiUrl,
                onConsumerKeyChange = viewModel::updateConsumerKey,
                onConsumerSecretChange = viewModel::updateConsumerSecret
            )

            // 打印机设置
            PrinterSettings(
                printerType = uiState.printerType,
                isPrinterConnected = uiState.isPrinterConnected,
                onPrinterTypeChange = viewModel::updatePrinterType
            )

            // 通知设置
            NotificationSettings(
                isEnabled = uiState.isNotificationEnabled,
                onEnabledChange = viewModel::updateNotificationEnabled
            )

            // 语言设置
            LanguageSection(
                currentLanguage = uiState.language,
                onLanguageChange = viewModel::updateLanguage
            )

            // 货币设置
            CurrencySettings(
                currency = uiState.currency,
                onCurrencyChange = viewModel::updateCurrency
            )

            // 关于
            SettingsCard(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.about),
                onClick = onNavigateToAbout
            )
        }
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
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
                    Icon(Icons.Default.Api, stringResource(R.string.website_setup))
                    Text(
                        text = stringResource(R.string.website_setup),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
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
                        label = { Text(stringResource(R.string.website_url)) },
                        leadingIcon = { Icon(Icons.Default.Link, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = consumerKey,
                        onValueChange = onConsumerKeyChange,
                        label = { Text(stringResource(R.string.api_key)) },
                        leadingIcon = { Icon(Icons.Default.Key, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = consumerSecret,
                        onValueChange = onConsumerSecretChange,
                        label = { Text(stringResource(R.string.api_secret)) },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { showSecret = !showSecret }) {
                                Icon(
                                    if (showSecret) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
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
    val printerTypes = listOf(
        "USB" to stringResource(R.string.bluetooth_printer),
        "Bluetooth" to stringResource(R.string.bluetooth_printer),
        "Network" to stringResource(R.string.network_printer)
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Print, stringResource(R.string.printer_setup))
                Text(
                    text = stringResource(R.string.printer_setup),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = printerTypes.find { it.first == printerType }?.second ?: printerTypes[0].second,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.printer_model)) },
                    leadingIcon = { 
                        Icon(
                            when (printerType) {
                                "USB" -> Icons.Default.Usb
                                "Bluetooth" -> Icons.Default.Bluetooth
                                "Network" -> Icons.Default.Wifi
                                else -> Icons.Default.Print
                            },
                            contentDescription = null
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
                    printerTypes.forEach { (type, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
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
                                    contentDescription = null
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
                        contentDescription = stringResource(R.string.printer_status),
                        tint = if (isPrinterConnected) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(
                            if (isPrinterConnected) R.string.connection_successful 
                            else R.string.connection_failed
                        ),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                FilledTonalButton(
                    onClick = { /* 连接/断开打印机 */ }
                ) {
                    Text(
                        if (isPrinterConnected) 
                            stringResource(R.string.cancel) 
                        else 
                            stringResource(R.string.confirm)
                    )
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
                Icon(Icons.Default.Notifications, stringResource(R.string.sound_setup))
                Text(
                    text = stringResource(R.string.sound_setup),
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
private fun LanguageSection(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val languages = listOf(
        "en" to stringResource(R.string.english),
        "zh" to stringResource(R.string.chinese)
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.language),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = languages.find { it.first == currentLanguage }?.second 
                        ?: languages.first().second,
                    onValueChange = {},
                    readOnly = true,
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
    currency: String,
    onCurrencyChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

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
                Icon(Icons.Default.MonetizationOn, null)
                Text(
                    text = stringResource(R.string.currency),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = currency,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    listOf(
                        "CNY" to "人民币",
                        "USD" to "美元",
                        "EUR" to "欧元",
                        "GBP" to "英镑",
                        "JPY" to "日元"
                    ).forEach { (code, name) ->
                        DropdownMenuItem(
                            text = { Text("$name ($code)") },
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