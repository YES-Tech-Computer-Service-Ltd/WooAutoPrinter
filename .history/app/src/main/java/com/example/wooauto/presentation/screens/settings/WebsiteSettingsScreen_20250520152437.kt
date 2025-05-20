package com.example.wooauto.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.util.Log
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsiteSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navController: NavController
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val siteUrl by viewModel.siteUrl.collectAsState()
    val consumerKey by viewModel.consumerKey.collectAsState()
    val consumerSecret by viewModel.consumerSecret.collectAsState()
    val pollingInterval by viewModel.pollingInterval.collectAsState()
    val useWooCommerceFood by viewModel.useWooCommerceFood.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val connectionTestResult by viewModel.connectionTestResult.collectAsState()
    val scrollState = rememberScrollState()
    
    var siteUrlInput by remember { mutableStateOf(siteUrl) }
    var consumerKeyInput by remember { mutableStateOf(consumerKey) }
    var consumerSecretInput by remember { mutableStateOf(consumerSecret) }
    var pollingIntervalInput by remember { mutableStateOf(pollingInterval.toString()) }
    
    // 添加二维码扫描器
    val barcodeLauncher = rememberLauncherForActivityResult(
        contract = ScanContract(),
        onResult = { result ->
            Log.d("QRScan", "扫描返回结果: ${result.contents ?: "没有内容"}")
            if (result.contents != null) {
                viewModel.handleQrCodeResult(result.contents)
                coroutineScope.launch {
                    delay(100) // 短暂延迟确保viewModel已处理数据
                    siteUrlInput = viewModel.siteUrl.value
                    consumerKeyInput = viewModel.consumerKey.value
                    consumerSecretInput = viewModel.consumerSecret.value
                    snackbarHostState.showSnackbar("API信息已从二维码更新")
                }
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("扫描已取消")
                }
            }
        }
    )

    // 监听二维码扫描事件
    LaunchedEffect(viewModel) {
        Log.d("QRScan", "开始监听扫描事件")
        viewModel.scanQrCodeEvent.collect {
            Log.d("QRScan", "收到扫描事件，准备启动扫描器")
            val options = ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(context.getString(R.string.scan_woocommerce_site_url_qr))
                .setCameraId(0)
                .setBeepEnabled(true)
                .setBarcodeImageEnabled(true)
                .setOrientationLocked(false)
            barcodeLauncher.launch(options)
        }
    }
    
    // 当ViewModel中的值变化时更新输入框
    LaunchedEffect(siteUrl, consumerKey, consumerSecret, pollingInterval) {
        siteUrlInput = siteUrl
        consumerKeyInput = consumerKey
        consumerSecretInput = consumerSecret
        pollingIntervalInput = pollingInterval.toString()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) { // Scaffold for Snackbar
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { paddingValues ->
            Column( modifier = Modifier.padding(paddingValues).fillMaxSize()) { // Main content column
                // 顶部标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 返回按钮
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // 标题
                    Text(
                        text = stringResource(id = R.string.website_setup),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // 内容区域 - 使用Card包裹
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp), // 给Card本身添加padding
                    shape = RoundedCornerShape(16.dp), // 圆角
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp) // 添加阴影
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp) // Card内部内容的padding
                            .verticalScroll(scrollState)
                    ) {
                        // 站点URL输入框 和 扫描按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = siteUrlInput,
                                onValueChange = { siteUrlInput = it },
                                label = { Text(stringResource(R.string.website_url)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            IconButton(onClick = { viewModel.handleQrCodeScan() }) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = stringResource(R.string.scan_qr_code)
                                )
                            }
                        }
                        
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
            }
        }
    }
} 