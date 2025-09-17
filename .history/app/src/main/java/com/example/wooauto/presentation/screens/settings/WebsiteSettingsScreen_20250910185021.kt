package com.example.wooauto.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wooauto.R
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.util.Log
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsiteSettingsDialogContent(
    viewModel: SettingsViewModel = hiltViewModel(),
    onClose: () -> Unit
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
    var showTestDialog by remember { mutableStateOf(false) }
    
    var siteUrlInput by remember { mutableStateOf(siteUrl) }
    var siteUrlFixed by remember { mutableStateOf("") }
    var showUrlFixHint by remember { mutableStateOf(false) }
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
    
    // 由容器控制外部阴影和形状，这里内容全宽高，避免出现"卡片套卡片"白框
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(id = R.string.api_configuration)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface, // Match card background
                    )
                )
            }
        ) { paddingValuesInternal ->
            Column(
                modifier = Modifier
                    .padding(paddingValuesInternal)
                    .padding(16.dp) // Content padding
                    .fillMaxSize()
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
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.website_url_placeholder)) }
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
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.api_key_placeholder)) }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Consumer Secret输入框
                OutlinedTextField(
                    value = consumerSecretInput,
                    onValueChange = { consumerSecretInput = it },
                    label = { Text(stringResource(R.string.api_secret)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.api_secret_placeholder)) }
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.use_woocommerce_food), // Changed from plugin_woocommerce_food for consistency
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = useWooCommerceFood,
                        onCheckedChange = { checked ->
                            // 先同步当前输入到 ViewModel，避免保存旧值
                            viewModel.updateSiteUrl(com.example.wooauto.utils.UrlNormalizer.sanitizeSiteUrl(siteUrlInput))
                            viewModel.updateConsumerKey(consumerKeyInput)
                            viewModel.updateConsumerSecret(consumerSecretInput)
                            viewModel.updatePollingInterval(pollingIntervalInput.toIntOrNull() ?: 30)
                            // 再更新开关（内部会保存）
                            viewModel.updateUseWooCommerceFood(checked)
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 保存和测试连接按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 临时：打开测试弹窗
                    OutlinedButton(
                        onClick = { showTestDialog = true },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(id = R.string.open_test_dialog)) }

                    Button(
                        onClick = {
                            if (siteUrlInput.isBlank() || consumerKeyInput.isBlank() || consumerSecretInput.isBlank()){
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.fill_all_fields))
                                }
                                return@Button
                            }
                            val sanitized = com.example.wooauto.utils.UrlNormalizer.sanitizeSiteUrl(siteUrlInput)
                            if (sanitized != siteUrlInput) {
                                siteUrlFixed = sanitized
                                showUrlFixHint = true
                                return@Button
                            }
                            viewModel.updateSiteUrl(sanitized)
                            viewModel.updateConsumerKey(consumerKeyInput)
                            viewModel.updateConsumerSecret(consumerSecretInput)
                            viewModel.updatePollingInterval(pollingIntervalInput.toIntOrNull() ?: 30)
                            // Optionally close dialog on save, or let user test then close
                            // onClose() 
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.settings_saved))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                    
                    Button(
                        onClick = { 
                            if (siteUrlInput.isBlank() || consumerKeyInput.isBlank() || consumerSecretInput.isBlank()){
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.fill_all_fields))
                                }
                                return@Button
                            }
                            // 先校验 URL，若需要修正，先提示
                            val sanitized = com.example.wooauto.utils.UrlNormalizer.sanitizeSiteUrl(siteUrlInput)
                            if (sanitized != siteUrlInput) {
                                siteUrlFixed = sanitized
                                showUrlFixHint = true
                                return@Button
                            }
                            // 先同步输入，再进行连接测试
                            viewModel.updateSiteUrl(sanitized)
                            viewModel.updateConsumerKey(consumerKeyInput)
                            viewModel.updateConsumerSecret(consumerSecretInput)
                            viewModel.updatePollingInterval(pollingIntervalInput.toIntOrNull() ?: 30)
                            viewModel.testConnection()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isTestingConnection
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.test_connection))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 连接测试结果（移除左侧图标，仅文字与右侧关闭按钮）
                connectionTestResult?.let { result ->
                    val isOk = result is SettingsViewModel.ConnectionTestResult.Success
                    val backgroundColor = if (isOk) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    val textColor = if (isOk) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = backgroundColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isOk) stringResource(R.string.connection_successful) else (result as SettingsViewModel.ConnectionTestResult.Error).message,
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearConnectionTestResult() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.close),
                                    tint = textColor
                                )
                            }
                        }
                    }
                }

                // 临时测试弹窗
                if (showTestDialog) {
                    com.example.wooauto.presentation.components.SettingsModal(
                        onDismissRequest = { showTestDialog = false },
                        widthFraction = 0.5f,
                        heightFraction = 0.3f
                    ) {
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(text = stringResource(id = R.string.test_dialog_title), style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = stringResource(id = R.string.test_dialog_message))
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showTestDialog = false }) { Text(stringResource(id = R.string.cancel)) }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { showTestDialog = false }) { Text(stringResource(id = R.string.ok)) }
                            }
                        }
                    }
                }

                // 已移除 URL 自动修正提示
            }
        }
    }
}

// Original WebsiteSettingsScreen can be removed or commented out if no longer directly navigated to.
/*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsiteSettingsScreen( // This composable is likely no longer needed as a direct navigation target
    viewModel: SettingsViewModel = hiltViewModel(),
    navController: NavController
) {
    // ... existing code ...
    // The content is now in WebsiteSettingsDialogContent
    // This screen would need to be adapted if it's still used,
    // perhaps by calling WebsiteSettingsDialogContent within a Dialog here too,
    // or by being removed from navigation graph.
}
*/ 