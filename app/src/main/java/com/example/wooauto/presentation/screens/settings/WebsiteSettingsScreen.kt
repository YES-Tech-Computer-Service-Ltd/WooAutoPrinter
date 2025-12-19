package com.example.wooauto.presentation.screens.settings

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wooauto.R
import com.example.wooauto.data.remote.exfood.ExFoodLocation
import com.example.wooauto.domain.models.StoreLocationSelection
import com.example.wooauto.presentation.components.ScrollableWithEdgeScrim
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    // Multi-store selection (WooCommerce Food / ExFood)
    val selectedStoreLocations by viewModel.settingsRepository.getSelectedStoreLocationsFlow()
        .collectAsState(initial = emptyList())
    var isApplying by remember { mutableStateOf(false) }
    var isEnablingMultiLocation by remember { mutableStateOf(false) }
    var pendingLocations by remember { mutableStateOf<List<ExFoodLocation>>(emptyList()) }
    var requireLocationSelection by remember { mutableStateOf(false) }
    var tempSelectedSlugs by remember { mutableStateOf<Set<String>>(emptySet()) }

    val requestClose: () -> Unit = {
        if (isApplying) {
            // 正在应用/保存时，禁止退出，避免中途状态不一致
        } else if (requireLocationSelection) {
            // 未确认就退出：关闭 multi-location（不保存选择）
            coroutineScope.launch {
                try {
                    viewModel.settingsRepository.setSelectedStoreLocations(emptyList())
                } catch (_: Exception) {
                    // ignore
                }
                requireLocationSelection = false
                pendingLocations = emptyList()
                tempSelectedSlugs = emptySet()
                onClose()
            }
        } else {
            onClose()
        }
    }

    BackHandler {
        requestClose()
    }

    rememberScrollState()
    // 移除测试弹窗按钮/状态

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
                        IconButton(
                            enabled = !isApplying,
                            onClick = {
                                requestClose()
                            }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface, // Match card background
                    )
                )
            }
        ) { paddingValuesInternal ->
            Box(
                modifier = Modifier
                    .padding(paddingValuesInternal)
                    .fillMaxSize()
            ) {
                ScrollableWithEdgeScrim(
                    modifier = Modifier
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                        .fillMaxSize()
                ) { scrollModifier, _ ->
                    Column(
                        modifier = scrollModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 表单主体宽度限制，避免超宽
                        Column(modifier = Modifier.fillMaxWidth(0.96f)) {
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
                                        viewModel.updateSiteUrl(
                                            com.example.wooauto.utils.UrlNormalizer.sanitizeSiteUrl(
                                                siteUrlInput
                                            )
                                        )
                                        viewModel.updateConsumerKey(consumerKeyInput)
                                        viewModel.updateConsumerSecret(consumerSecretInput)
                                        viewModel.updatePollingInterval(
                                            pollingIntervalInput.toIntOrNull() ?: 30
                                        )
                                        // 再更新开关（内部会保存）
                                        viewModel.updateUseWooCommerceFood(checked)

                                        // 关闭插件时：同时关闭 multi-location（清空门店选择，避免残留过滤/显示）
                                        if (!checked) {
                                            coroutineScope.launch {
                                                try { viewModel.settingsRepository.setSelectedStoreLocations(emptyList()) } catch (_: Exception) {}
                                                viewModel.notifyServiceToRestartPolling()
                                            }
                                            requireLocationSelection = false
                                            pendingLocations = emptyList()
                                            tempSelectedSlugs = emptySet()
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Multi location toggle: enabled only after user confirms selection.
                            if (useWooCommerceFood) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.enable_multi_location),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = selectedStoreLocations.isNotEmpty() || requireLocationSelection || isEnablingMultiLocation,
                                        enabled = !isApplying && !isTestingConnection,
                                        onCheckedChange = { checked ->
                                            if (!checked) {
                                                // Disable: clear confirmed selection
                                                coroutineScope.launch {
                                                    try { viewModel.settingsRepository.setSelectedStoreLocations(emptyList()) } catch (_: Exception) {}
                                                    viewModel.notifyServiceToRestartPolling()
                                                }
                                                isEnablingMultiLocation = false
                                            } else {
                                                // Enable: fetch locations and open multi-select dialog (confirm required)
                                                if (siteUrlInput.isBlank() || consumerKeyInput.isBlank() || consumerSecretInput.isBlank()) {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar(context.getString(R.string.fill_all_fields))
                                                    }
                                                    return@Switch
                                                }

                                                coroutineScope.launch {
                                                    try {
                                                        isEnablingMultiLocation = true
                                                        isApplying = true

                                                        val interval = pollingIntervalInput.toIntOrNull() ?: 30
                                                        val result = viewModel.applyApiConfigAndFetchLocations(
                                                            rawSiteUrl = siteUrlInput,
                                                            consumerKey = consumerKeyInput,
                                                            consumerSecret = consumerSecretInput,
                                                            pollingIntervalSeconds = interval,
                                                            useWooCommerceFood = true
                                                        )

                                                        val locations = result.getOrNull()
                                                        if (locations != null) {
                                                            if (locations.isEmpty()) {
                                                                // No locations: keep multi-location disabled
                                                                try { viewModel.settingsRepository.setSelectedStoreLocations(emptyList()) } catch (_: Exception) {}
                                                                snackbarHostState.showSnackbar(context.getString(R.string.apply_failed_format, "No locations"))
                                                                isEnablingMultiLocation = false
                                                            } else {
                                                                pendingLocations = locations
                                                                tempSelectedSlugs = emptySet()
                                                                requireLocationSelection = true
                                                                // selection dialog is now the "pending" state
                                                                isEnablingMultiLocation = false
                                                            }
                                                        } else {
                                                            val e = result.exceptionOrNull()
                                                            snackbarHostState.showSnackbar(
                                                                context.getString(
                                                                    R.string.apply_failed_format,
                                                                    (e?.message ?: "")
                                                                )
                                                            )
                                                            try { viewModel.settingsRepository.setSelectedStoreLocations(emptyList()) } catch (_: Exception) {}
                                                            isEnablingMultiLocation = false
                                                        }
                                                    } finally {
                                                        isApplying = false
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // 当前已选门店（仅在确认启用 multi-location 后展示）
                                if (selectedStoreLocations.isNotEmpty()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Text(
                                                text = stringResource(R.string.store_location_selected_title),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            selectedStoreLocations.forEach { sel ->
                                                Text(text = sel.name, style = MaterialTheme.typography.bodyMedium)
                                                sel.address?.takeIf { it.isNotBlank() }?.let { addr ->
                                                    Text(
                                                        text = addr,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Text(
                                                    text = stringResource(R.string.store_location_slug_format, sel.slug),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            // 保存和测试连接按钮
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (siteUrlInput.isBlank() || consumerKeyInput.isBlank() || consumerSecretInput.isBlank()) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(context.getString(R.string.fill_all_fields))
                                            }
                                            return@Button
                                        }
                                        val sanitized =
                                            com.example.wooauto.utils.UrlNormalizer.sanitizeSiteUrl(
                                                siteUrlInput
                                            )
                                        viewModel.updateSiteUrl(sanitized)
                                        viewModel.updateConsumerKey(consumerKeyInput)
                                        viewModel.updateConsumerSecret(consumerSecretInput)
                                        viewModel.updatePollingInterval(
                                            pollingIntervalInput.toIntOrNull() ?: 30
                                        )
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
                                        if (siteUrlInput.isBlank() || consumerKeyInput.isBlank() || consumerSecretInput.isBlank()) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(context.getString(R.string.fill_all_fields))
                                            }
                                            return@Button
                                        }
                                        // 先校验 URL，若需要修正，先提示
                                        val sanitized =
                                            com.example.wooauto.utils.UrlNormalizer.sanitizeSiteUrl(
                                                siteUrlInput
                                            )
                                        // 先同步输入，再进行连接测试
                                        viewModel.updateSiteUrl(sanitized)
                                        viewModel.updateConsumerKey(consumerKeyInput)
                                        viewModel.updateConsumerSecret(consumerSecretInput)
                                        viewModel.updatePollingInterval(
                                            pollingIntervalInput.toIntOrNull() ?: 30
                                        )
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
                                val backgroundColor =
                                    if (isOk) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                val textColor =
                                    if (isOk) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer

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
                        }
                    }
                }
            }
        }
    }

    // 多门店强制选择：不允许 dismiss，必须选中后确认
    if (requireLocationSelection) {
        AlertDialog(
            onDismissRequest = {
                // 未确认就退出：关闭 multi-location（不保存选择）
                coroutineScope.launch {
                    try { viewModel.settingsRepository.setSelectedStoreLocations(emptyList()) } catch (_: Exception) {}
                }
                requireLocationSelection = false
                pendingLocations = emptyList()
                tempSelectedSlugs = emptySet()
            },
            title = { Text(text = stringResource(R.string.store_location_select_title)) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    items(pendingLocations, key = { it.slug }) { loc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tempSelectedSlugs = if (tempSelectedSlugs.contains(loc.slug)) {
                                        tempSelectedSlugs - loc.slug
                                    } else {
                                        tempSelectedSlugs + loc.slug
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val checked = tempSelectedSlugs.contains(loc.slug)
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    tempSelectedSlugs = if (isChecked) {
                                        tempSelectedSlugs + loc.slug
                                    } else {
                                        tempSelectedSlugs - loc.slug
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = loc.name, style = MaterialTheme.typography.bodyLarge)
                                loc.address?.takeIf { it.isNotBlank() }?.let { addr ->
                                    Text(
                                        text = addr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.store_location_slug_format, loc.slug),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = tempSelectedSlugs.isNotEmpty() && !isApplying,
                    onClick = {
                        val chosen = pendingLocations.filter { tempSelectedSlugs.contains(it.slug) }
                        if (chosen.isEmpty()) return@TextButton
                        coroutineScope.launch {
                            try {
                                isApplying = true
                                viewModel.settingsRepository.setSelectedStoreLocations(
                                    chosen.map { loc ->
                                        StoreLocationSelection(
                                            slug = loc.slug,
                                            name = loc.name,
                                            address = loc.address
                                        )
                                    }
                                )
                                requireLocationSelection = false
                                pendingLocations = emptyList()
                                tempSelectedSlugs = emptySet()
                                viewModel.notifyServiceToRestartPolling()
                            } finally {
                                isApplying = false
                            }
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.confirm))
                }
            }
            ,
            dismissButton = {
                TextButton(
                    enabled = !isApplying,
                    onClick = {
                        coroutineScope.launch {
                            try { viewModel.settingsRepository.setSelectedStoreLocations(emptyList()) } catch (_: Exception) {}
                        }
                        requireLocationSelection = false
                        pendingLocations = emptyList()
                        tempSelectedSlugs = emptySet()
                    }
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}