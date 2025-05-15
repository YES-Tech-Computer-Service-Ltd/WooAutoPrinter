package com.example.wooauto.presentation.screens.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.wooauto.R
import com.example.wooauto.utils.LocaleHelper
import com.example.wooauto.presentation.navigation.Screen
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.material3.HorizontalDivider
import kotlinx.coroutines.delay
import com.example.wooauto.presentation.theme.WooAutoTheme
import com.example.wooauto.utils.LocaleManager
import com.example.wooauto.domain.templates.TemplateType
import androidx.compose.material3.TextField
import androidx.compose.material3.RadioButton
import kotlinx.coroutines.runBlocking
import androidx.compose.material3.IconButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.example.wooauto.presentation.components.WooTopBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.DisposableEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 获取当前设置
    val siteUrl by viewModel.siteUrl.collectAsState()
    val consumerKey by viewModel.consumerKey.collectAsState()
    val consumerSecret by viewModel.consumerSecret.collectAsState()
    
    // 添加更多初始状态
    var autoUpdate by remember { mutableStateOf(false) }
    
    // 预先获取需要用到的字符串资源
    val featureComingSoonText = stringResource(R.string.feature_coming_soon)
    val appVersionText = stringResource(R.string.app_version)
    val fillAllFieldsText = stringResource(R.string.fill_all_fields)
    
    val currentLocale by viewModel.currentLocale.collectAsState(initial = Locale.getDefault())

    // 各种对话框状态
    var showApiDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showTestResultDialog by remember { mutableStateOf(false) }
    
    // 注册广播接收器来监听API设置打开请求
    DisposableEffect(context) {
        val intentFilter = IntentFilter("com.example.wooauto.ACTION_OPEN_API_SETTINGS")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.wooauto.ACTION_OPEN_API_SETTINGS") {
                    Log.d("SettingsScreen", "收到打开API设置对话框的广播")
                    showApiDialog = true
                }
            }
        }
        
        // 根据API级别使用相应的注册方法
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                receiver,
                intentFilter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        
        // 当组件被销毁时注销广播接收器
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e("SettingsScreen", "注销广播接收器失败: ${e.message}")
            }
        }
    }
    
    // 测试订单的结果
    val testOrderResult by remember { mutableStateOf<String?>(null) }
    val isTestingApi by remember { mutableStateOf(false) }
    
    // 编辑用的临时字段
    var tempSiteUrl by remember { mutableStateOf(siteUrl) }
    var tempConsumerKey by remember { mutableStateOf(consumerKey) }
    var tempConsumerSecret by remember { mutableStateOf(consumerSecret) }
    var pollingIntervalInput by remember { mutableStateOf("30") }
    var useWooCommerceFoodInput by remember { mutableStateOf(false) }
    
    // 添加二维码扫描器
    val barcodeLauncher = rememberLauncherForActivityResult(
        contract = ScanContract(),
        onResult = { result ->
            Log.d("QRScan", "扫描返回结果: ${result.contents ?: "没有内容"}")
            if (result.contents != null) {
                // 处理扫描结果
                viewModel.handleQrCodeResult(result.contents)
                
                // 如果API对话框处于打开状态，更新临时字段值
                if (showApiDialog) {
                    // 延迟一下等待viewModel处理完成
                    coroutineScope.launch {
                        delay(100) // 短暂延迟确保viewModel已处理数据
                        tempSiteUrl = viewModel.siteUrl.value
                        tempConsumerKey = viewModel.consumerKey.value
                        tempConsumerSecret = viewModel.consumerSecret.value
                        
                        // 显示提示
                        snackbarHostState.showSnackbar("API信息已从二维码更新")
                    }
                }
            } else {
                // 用户取消了扫描
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
            // 配置扫描选项
            val options = ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(context.getString(R.string.scan_woocommerce_site_url_qr))
                .setCameraId(0) // 后置摄像头
                .setBeepEnabled(true)
                .setBarcodeImageEnabled(true)
                .setOrientationLocked(false)
            
            // 启动扫描器
            barcodeLauncher.launch(options)
        }
    }
    
    // 显示测试结果对话框
    if (showTestResultDialog && testOrderResult != null) {
        AlertDialog(
            onDismissRequest = { showTestResultDialog = false },
            title = { Text("API测试结果") },
            text = {
                Column {
                    Text(testOrderResult!!)
                }
            },
            confirmButton = {
                TextButton(onClick = { showTestResultDialog = false }) {
                    Text("确定")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            WooTopBar(
                title = stringResource(id = R.string.settings),
                showSearch = false,
                isRefreshing = false,
                onRefresh = { /* 设置页面不需要刷新功能 */ },
                showRefreshButton = false,
                locale = currentLocale
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding(),
                    start = 0.dp,
                    end = 0.dp
                )
        ) {
            // 外层Column，包含统一的水平内边距
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 0.dp)
            ) {
                // 增加顶部间距
                Spacer(modifier = Modifier.height(8.dp))
                
                // 设置页面的内容区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // API配置卡片
                    SettingsCategoryCard {
                        SettingsNavigationItem(
                            title = stringResource(R.string.api_configuration),
                            subTitle = if (siteUrl.isNotEmpty() && consumerKey.isNotEmpty() && consumerSecret.isNotEmpty()) {
                                stringResource(R.string.api_configured)
                            } else {
                                stringResource(R.string.api_not_configured)
                            },
                            icon = Icons.Filled.Cloud,
                            onClick = {
                                Log.d("设置导航", "点击了API配置项")
                                showApiDialog = true
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 自动化任务卡片
                    SettingsCategoryCard {
                        SettingsNavigationItem(
                            title = stringResource(R.string.automation_tasks),
                            subTitle = stringResource(R.string.automatic_order_processing_desc),
                            icon = Icons.Filled.AutoAwesome,
                            onClick = {
                                Log.d("设置导航", "点击了自动化任务项")
                                navController.navigate(Screen.AutomationSettings.route)
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 其他设置卡片
                    SettingsCategoryCard {
                        SettingsNavigationItem(
                            title = stringResource(R.string.printer_settings),
                            icon = Icons.Filled.Print,
                            onClick = {
                                Log.d("设置导航", "点击了打印设置项")
                                navController.navigate(Screen.PrinterSettings.route)
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        SettingsNavigationItem(
                            title = stringResource(R.string.sound_settings),
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            onClick = {
                                Log.d("设置导航", "点击了声音设置项")
                                navController.navigate(Screen.SoundSettings.route)
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        SettingsNavigationItem(
                            title = stringResource(R.string.store_settings),
                            icon = Icons.Default.Store,
                            onClick = { 
                                /* 导航到店铺设置 */
                                Log.d("设置导航", "点击了店铺信息设置项")
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(featureComingSoonText)
                                }
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 应用程序设置
                    SettingsCategoryCard {
                        // 语言设置
                        SettingItem(
                            icon = Icons.Outlined.Language,
                            title = stringResource(id = R.string.language),
                            subtitle = if (currentLocale.language == "zh") stringResource(id = R.string.chinese) else stringResource(id = R.string.english),
                            onClick = {
                                Log.d("设置", "点击了语言设置")
                                showLanguageDialog = true
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 许可设置
                        SettingItem(
                            icon = Icons.Default.VpnKey,
                            title = stringResource(id = R.string.license_settings),
                            subtitle = "",
                            onClick = {
                                Log.d("设置", "点击了许可设置")
                                navController.navigate(Screen.LicenseSettings.route)
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 关于
                        SettingsNavigationItem(
                            icon = Icons.Outlined.Info,
                            title = stringResource(R.string.about),
                            onClick = {
                                Log.d("设置", "点击了关于")
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(appVersionText)
                                }
                            }
                        )
                        
                        // 如果有更新可用，显示下载更新按钮
                        if (viewModel.hasUpdate.collectAsState().value) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .clickable { viewModel.downloadUpdate() },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GetApp,
                                    contentDescription = "下载更新",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(24.dp)
                                )
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    val isDownloading = viewModel.isDownloading.collectAsState().value
                                    val downloadProgress = viewModel.downloadProgress.collectAsState().value
                                    
                                    Text(
                                        text = if (isDownloading) "正在下载更新..." else "下载并安装更新",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    if (isDownloading) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { downloadProgress / 100f },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = "$downloadProgress%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // 自动更新
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.auto_update),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (viewModel.isCheckingUpdate.collectAsState().value) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "检查更新中...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            } else if (viewModel.hasUpdate.collectAsState().value) {
                                Text(
                                    text = "发现新版本",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        Switch(
                            checked = autoUpdate,
                            onCheckedChange = { 
                                autoUpdate = it
                                viewModel.setAutoUpdate(it)
                            }
                        )
                    }

                    // 底部空间
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
        
        // 语言选择对话框
        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { Text(stringResource(id = R.string.language)) },
                text = {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 不再在这里使用LocalContext.current
                                    viewModel.setAppLanguage(Locale.ENGLISH)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(id = R.string.english))
                            if (currentLocale.language == "en") {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        HorizontalDivider()
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 不再在这里使用LocalContext.current
                                    viewModel.setAppLanguage(Locale.SIMPLIFIED_CHINESE)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(id = R.string.chinese))
                            if (currentLocale.language == "zh") {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text(stringResource(id = R.string.cancel))
                    }
                }
            )
        }
        
        // API配置对话框  
        if (showApiDialog) {
            AlertDialog(
                onDismissRequest = { showApiDialog = false },
                title = { Text(stringResource(R.string.api_configuration)) },
                text = {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = tempSiteUrl,
                                onValueChange = { tempSiteUrl = it },
                                label = { Text(stringResource(R.string.website_url)) },
                                placeholder = { Text(stringResource(R.string.website_url_placeholder)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                isError = !tempSiteUrl.contains("http")
                            )
                            
                            IconButton(
                                onClick = { viewModel.handleQrCodeScan() }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = stringResource(R.string.scan_qr_code)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = tempConsumerKey,
                            onValueChange = { tempConsumerKey = it },
                            label = { Text(stringResource(R.string.api_key)) },
                            placeholder = { Text(stringResource(R.string.api_key_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = tempConsumerKey.contains("http"),
                            colors = OutlinedTextFieldDefaults.colors(
                                errorBorderColor = MaterialTheme.colorScheme.error,
                                errorLabelColor = MaterialTheme.colorScheme.error
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = tempConsumerSecret,
                            onValueChange = { tempConsumerSecret = it },
                            label = { Text(stringResource(R.string.api_secret)) },
                            placeholder = { Text(stringResource(R.string.api_secret_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = tempConsumerSecret.contains("http"),
                            colors = OutlinedTextFieldDefaults.colors(
                                errorBorderColor = MaterialTheme.colorScheme.error,
                                errorLabelColor = MaterialTheme.colorScheme.error
                            )
                        )
                        
                        OutlinedTextField(
                            value = pollingIntervalInput,
                            onValueChange = { newValue -> 
                                // 确保只输入数字
                                if (newValue.isEmpty() || newValue.all { char -> char.isDigit() }) {
                                    pollingIntervalInput = newValue
                                }
                            },
                            label = { Text(stringResource(R.string.polling_interval)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                        
                        // WooCommerce Food设置
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.use_woocommerce_food)
                            )
                            
                            Switch(
                                checked = useWooCommerceFoodInput,
                                onCheckedChange = { useWooCommerceFoodInput = it }
                            )
                        }
                        
                        if (isTestingApi) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.testing_connection))
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                if (tempSiteUrl.isNotEmpty() && tempConsumerKey.isNotEmpty() && tempConsumerSecret.isNotEmpty()) {
                                    // 保存配置
                                    viewModel.updateSiteUrl(tempSiteUrl)
                                    viewModel.updateConsumerKey(tempConsumerKey)
                                    viewModel.updateConsumerSecret(tempConsumerSecret)
                                    viewModel.updatePollingInterval(pollingIntervalInput.toIntOrNull() ?: 30)
                                    viewModel.updateUseWooCommerceFood(useWooCommerceFoodInput)
                                    
                                    // 测试连接
                                    viewModel.testConnection()
                                    showApiDialog = false
                                } else {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(fillAllFieldsText)
                                    }
                                }
                            },
                            enabled = !isTestingApi
                        ) {
                            Text(stringResource(R.string.save_and_test))
                        }
                        
                        TextButton(onClick = { showApiDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsCategoryCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 5.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
                content()
        }
    }
}

@Composable
fun SettingsNavigationItem(
    icon: ImageVector,
    title: String,
    subTitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            
            if (subTitle != null) {
            Text(
                    text = subTitle,
                    style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            }
        }
        
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "箭头",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            
            if (subtitle.isNotEmpty()) {
            Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            }
        }
        
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "箭头",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
} 