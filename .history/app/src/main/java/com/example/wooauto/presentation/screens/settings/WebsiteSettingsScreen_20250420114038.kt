package com.example.wooauto.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wooauto.R
import com.example.wooauto.presentation.components.ApiConfigForm
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

@Composable
fun WebsiteSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navController: NavController = rememberNavController()
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
    var useWooCommerceFoodInput by remember { mutableStateOf(useWooCommerceFood) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // 当ViewModel中的值变化时更新输入框
    LaunchedEffect(siteUrl, consumerKey, consumerSecret, pollingInterval, useWooCommerceFood) {
        siteUrlInput = siteUrl
        consumerKeyInput = consumerKey
        consumerSecretInput = consumerSecret
        pollingIntervalInput = pollingInterval.toString()
        useWooCommerceFoodInput = useWooCommerceFood
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 使用通用API配置表单
            ApiConfigForm(
                siteUrl = siteUrlInput,
                consumerKey = consumerKeyInput,
                consumerSecret = consumerSecretInput,
                pollingInterval = pollingIntervalInput,
                useWooCommerceFood = useWooCommerceFoodInput,
                onSiteUrlChange = { siteUrlInput = it },
                onConsumerKeyChange = { consumerKeyInput = it },
                onConsumerSecretChange = { consumerSecretInput = it },
                onPollingIntervalChange = { pollingIntervalInput = it },
                onUseWooCommerceFoodChange = { useWooCommerceFoodInput = it },
                onQrCodeScan = { viewModel.handleQrCodeScan() },
                isTestingConnection = isTestingConnection
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 保存和测试连接按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        if (siteUrlInput.isNotEmpty() && consumerKeyInput.isNotEmpty() && consumerSecretInput.isNotEmpty()) {
                            // 保存配置
                            viewModel.updateSiteUrl(siteUrlInput)
                            viewModel.updateConsumerKey(consumerKeyInput)
                            viewModel.updateConsumerSecret(consumerSecretInput)
                            viewModel.updatePollingInterval(pollingIntervalInput.toIntOrNull() ?: 30)
                            viewModel.updateUseWooCommerceFood(useWooCommerceFoodInput)
                            
                            // 显示保存成功
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("设置已保存")
                            }
                            
                            // 返回上一页
                            navController.popBackStack()
                        } else {
                            // 显示错误提示
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("请填写所有必填字段")
                            }
                        }
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
} 