package com.example.wooauto.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.fake.R

@Composable
fun WebsiteSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val siteUrl by viewModel.siteUrl.collectAsState()
    val consumerKey by viewModel.consumerKey.collectAsState()
    val consumerSecret by viewModel.consumerSecret.collectAsState()
    val pollingInterval by viewModel.pollingInterval.collectAsState()
    val useWooCommerceFood by viewModel.useWooCommerceFood.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val connectionTestResult by viewModel.connectionTestResult.collectAsState()
    
    var siteUrlInput by remember { mutableStateOf(siteUrl) }
    var consumerKeyInput by remember { mutableStateOf(consumerKey) }
    var consumerSecretInput by remember { mutableStateOf(consumerSecret) }
    var pollingIntervalInput by remember { mutableStateOf(pollingInterval.toString()) }
    
    // 当ViewModel中的值变化时更新输入框
    LaunchedEffect(siteUrl, consumerKey, consumerSecret, pollingInterval) {
        siteUrlInput = siteUrl
        consumerKeyInput = consumerKey
        consumerSecretInput = consumerSecret
        pollingIntervalInput = pollingInterval.toString()
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // 站点URL输入框
        OutlinedTextField(
            value = siteUrlInput,
            onValueChange = { siteUrlInput = it },
            label = { Text(stringResource(R.string.website_url)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Consumer Key输入框
        OutlinedTextField(
            value = consumerKeyInput,
            onValueChange = { consumerKeyInput = it },
            label = { Text(stringResource(R.string.api_key)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Consumer Secret输入框
        OutlinedTextField(
            value = consumerSecretInput,
            onValueChange = { consumerSecretInput = it },
            label = { Text(stringResource(R.string.api_secret)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 轮询间隔输入框
        OutlinedTextField(
            value = pollingIntervalInput,
            onValueChange = { 
                if (it.isEmpty() || it.toIntOrNull() != null) {
                    pollingIntervalInput = it
                }
            },
            label = { Text(stringResource(R.string.polling_interval)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // WooCommerce Food插件选择
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.plugin_woocommerce_food),
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = useWooCommerceFood,
                onCheckedChange = { viewModel.updateUseWooCommerceFood(it) }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 保存和测试连接按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    viewModel.updateSiteUrl(siteUrlInput)
                    viewModel.updateConsumerKey(consumerKeyInput)
                    viewModel.updateConsumerSecret(consumerSecretInput)
                    viewModel.updatePollingInterval(pollingIntervalInput.toIntOrNull() ?: 30)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.save))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Button(
                onClick = { viewModel.testConnection() },
                modifier = Modifier.weight(1f),
                enabled = !isTestingConnection
            ) {
                if (isTestingConnection) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.test_connection))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 连接测试结果
        connectionTestResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (result) {
                        is SettingsViewModel.ConnectionTestResult.Success -> MaterialTheme.colorScheme.primaryContainer
                        is SettingsViewModel.ConnectionTestResult.Error -> MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (result) {
                            is SettingsViewModel.ConnectionTestResult.Success -> Icons.Default.Check
                            is SettingsViewModel.ConnectionTestResult.Error -> Icons.Default.Close
                        },
                        contentDescription = null,
                        tint = when (result) {
                            is SettingsViewModel.ConnectionTestResult.Success -> MaterialTheme.colorScheme.primary
                            is SettingsViewModel.ConnectionTestResult.Error -> MaterialTheme.colorScheme.error
                        }
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Text(
                        text = when (result) {
                            is SettingsViewModel.ConnectionTestResult.Success -> stringResource(R.string.connection_successful)
                            is SettingsViewModel.ConnectionTestResult.Error -> result.message
                        },
                        color = when (result) {
                            is SettingsViewModel.ConnectionTestResult.Success -> MaterialTheme.colorScheme.primary
                            is SettingsViewModel.ConnectionTestResult.Error -> MaterialTheme.colorScheme.error
                        }
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    IconButton(onClick = { viewModel.clearConnectionTestResult() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭"
                        )
                    }
                }
            }
        }
    }
} 