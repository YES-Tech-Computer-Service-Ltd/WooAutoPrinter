package com.example.wooauto.presentation.screens.settings

import android.util.Log
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
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.R
import com.example.wooauto.presentation.navigation.Screen
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.material3.HorizontalDivider
import com.example.wooauto.presentation.components.WooTopBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.wooauto.presentation.screens.settings.PrinterSettings.PrinterSettingsDialogContent
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 获取当前设置
    val siteUrl by viewModel.siteUrl.collectAsState()
    val consumerKey by viewModel.consumerKey.collectAsState()
    val consumerSecret by viewModel.consumerSecret.collectAsState()
    val isAutoPrintEnabled by viewModel.automaticPrinting.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val defaultTemplateType by viewModel.defaultTemplateType.collectAsState()
    
    // 预先获取需要用到的字符串资源
    val featureComingSoonText = stringResource(R.string.feature_coming_soon)
    val licenseRequiredMessage = stringResource(R.string.license_required_message)
    
    val currentLocale by viewModel.currentLocale.collectAsState(initial = Locale.getDefault())
    
    // 获取证书状态
    val licenseStatus = viewModel.licenseStatusText.collectAsState().value
    val isLicenseValid = licenseStatus.contains("验证") || licenseStatus.contains("Verified") || licenseStatus.contains("Trial")

    // 各种对话框状态
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showWebsiteSettingsDialog by remember { mutableStateOf(false) }
    var showAutomationSettingsDialog by remember { mutableStateOf(false) }
    var showSoundSettingsDialog by remember { mutableStateOf(false) }
    var showPrintTemplatesDialog by remember { mutableStateOf(false) }
    var showPrinterSettingsDialog by remember { mutableStateOf(false) }
    var showStoreSettingsDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            WooTopBar(
                title = stringResource(id = R.string.settings),
                showSearch = false,
                isRefreshing = false,
                showRefreshButton = false,
                locale = currentLocale
            )
        }
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
                                showWebsiteSettingsDialog = true
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 其他设置卡片
                    SettingsCategoryCard {
                        SettingsNavigationItem(
                            title = stringResource(R.string.printer_settings),
                            icon = Icons.Filled.Print,
                            subTitle = run {
                                val currentPrinter = viewModel.currentPrinterConfig.collectAsState().value
                                val printerStatus = viewModel.printerStatus.collectAsState().value
                                val defaultLabel = stringResource(R.string.default_printer_label)
                                
                                if (currentPrinter != null && printerStatus.name == "CONNECTED") {
                                    "${currentPrinter.name} - ${currentPrinter.paperWidth}mm"
                                } else {
                                    val defaultPrinter = viewModel.printerConfigs.collectAsState().value.find { it.isDefault }
                                    if (defaultPrinter != null) {
                                        "${defaultPrinter.name} - ${defaultPrinter.paperWidth}mm ($defaultLabel)"
                                    } else {
                                        stringResource(R.string.no_printers_configured_prompt)
                                    }
                                }
                            },
                            isLocked = !isLicenseValid,
                            onClick = {
                                if (isLicenseValid) {
                                    Log.d("设置导航", "点击了打印设置项")
                                    showPrinterSettingsDialog = true
                                } else {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(licenseRequiredMessage)
                                    }
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 新增：模板设置
                        SettingsNavigationItem(
                            title = stringResource(R.string.printer_templates),
                            icon = Icons.Filled.Edit,
                            subTitle = stringResource(R.string.printer_templates_desc),
                            isLocked = !isLicenseValid,
                            onClick = {
                                if (isLicenseValid) {
                                    Log.d("设置导航", "点击了模板设置项")
                                    showPrintTemplatesDialog = true
                                } else {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(licenseRequiredMessage)
                                    }
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        SettingsNavigationItem(
                            title = stringResource(R.string.sound_settings),
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            subTitle = run {
                                val volume = viewModel.soundVolume.collectAsState().value
                                val soundType = viewModel.soundType.collectAsState().value
                                val enabled = viewModel.soundEnabled.collectAsState().value
                                val soundTypeDisplayName = viewModel.getSoundTypeDisplayName(soundType)
                                
                                if (enabled) {
                                    val volumeFormatted = stringResource(R.string.sound_volume_format, volume)
                                    val soundTypeFormatted = stringResource(R.string.sound_type_format, soundTypeDisplayName)
                                    stringResource(R.string.sound_status_format, volumeFormatted, soundTypeFormatted)
                                } else {
                                    stringResource(R.string.sound_disabled)
                                }
                            },
                            isLocked = !isLicenseValid,
                            onClick = {
                                if (isLicenseValid) {
                                    Log.d("设置导航", "点击了声音设置项")
                                    showSoundSettingsDialog = true
                                } else {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(licenseRequiredMessage)
                                    }
                                }
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
                                showStoreSettingsDialog = true
                            }
                        )

                        // Add Auto Print Settings here
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        SettingsNavigationItem(
                            title = stringResource(R.string.auto_print_settings_title),
                            subTitle = run {
                                val statusEnabledText = stringResource(R.string.status_enabled)
                                val statusDisabledText = stringResource(R.string.status_disabled)
                                val noTemplateSelectedText = stringResource(R.string.no_template_selected)
                                if (isAutoPrintEnabled) {
                                    val currentSelectedTemplateTypeName = defaultTemplateType.name
                                    val templateName = templates.find { it.id == currentSelectedTemplateTypeName }?.name ?: noTemplateSelectedText
                                    "$statusEnabledText - $templateName"
                                } else {
                                    statusDisabledText
                                }
                            },
                            icon = Icons.Filled.SettingsApplications,
                            isLocked = !isLicenseValid,
                            onClick = {
                                if (isLicenseValid) {
                                    Log.d("设置导航", "点击了自动打印设置项")
                                    showAutomationSettingsDialog = true
                                } else {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(licenseRequiredMessage)
                                    }
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
                            subtitle = viewModel.licenseStatusText.collectAsState().value,
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
                            subTitle = run {
                                val currentVersion = viewModel.updateInfo.collectAsState().value?.currentVersion?.toVersionString() ?: ""
                                val hasUpdate = viewModel.hasUpdate.collectAsState().value
                                val latestVersion = viewModel.updateInfo.collectAsState().value?.latestVersion?.toVersionString() ?: ""
                                val isCheckingUpdate = viewModel.isCheckingUpdate.collectAsState().value
                                
                                if (isCheckingUpdate) {
                                    // 显示正在获取版本信息
                                    stringResource(R.string.fetching_version_info)
                                } else if (currentVersion.isNotEmpty()) {
                                    if (hasUpdate && latestVersion.isNotEmpty()) {
                                        stringResource(R.string.about_version_info, currentVersion, 
                                            stringResource(R.string.version_needs_update, latestVersion))
                                    } else {
                                        stringResource(R.string.about_version_info, currentVersion,
                                            stringResource(R.string.version_is_latest))
                                    }
                                } else {
                                    // 如果版本信息为空，也显示正在获取中
                                    stringResource(R.string.fetching_version_info)
                                }
                            },
                            onClick = {
                                Log.d("设置", "点击了关于")
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(viewModel.getAboutInfoText())
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
                                    contentDescription = stringResource(R.string.download_update),
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
                                        text = if (isDownloading) stringResource(R.string.downloading_update) else stringResource(R.string.download_and_install_update),
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
                                            text = stringResource(R.string.download_running, downloadProgress),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // 底部空间
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
        
        // 语言设置对话框
        if (showLanguageDialog) {
            Dialog(
                onDismissRequest = { showLanguageDialog = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                LanguageSettingsDialogContent(
                    onClose = { showLanguageDialog = false }
                )
            }
        }

        // Website Settings Dialog
        if (showWebsiteSettingsDialog) {
            Dialog(
                onDismissRequest = { showWebsiteSettingsDialog = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.9f),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    WebsiteSettingsDialogContent(
                        viewModel = viewModel,
                        onClose = { showWebsiteSettingsDialog = false }
                    )
                }
            }
        }

        // Automation Settings Dialog
        if (showAutomationSettingsDialog) {
            Dialog(
                onDismissRequest = { showAutomationSettingsDialog = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                AutomationSettingsDialogContent(
                    viewModel = viewModel,
                    onClose = { showAutomationSettingsDialog = false }
                )
            }
        }

        // Sound Settings Dialog
        if (showSoundSettingsDialog) {
            val soundSettingsViewModel = hiltViewModel<SoundSettingsViewModel>()
            Dialog(
                onDismissRequest = { 
                    soundSettingsViewModel.stopSound() // 停止任何正在播放的声音
                    showSoundSettingsDialog = false
                    viewModel.refreshSoundSettings() // 关闭对话框时刷新声音设置
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                SoundSettingsDialogContent(
                    onClose = { 
                        soundSettingsViewModel.stopSound() // 停止任何正在播放的声音
                        showSoundSettingsDialog = false
                        viewModel.refreshSoundSettings() // 关闭对话框时刷新声音设置
                    }
                )
            }
        }

        // Print Templates Dialog
        if (showPrintTemplatesDialog) {
            Dialog(
                onDismissRequest = { showPrintTemplatesDialog = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                PrintTemplatesDialogContent(
                    onClose = { showPrintTemplatesDialog = false }
                )
            }
        }

        // Printer Settings Dialog
        if (showPrinterSettingsDialog) {
            Dialog(
                onDismissRequest = { showPrinterSettingsDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.9f),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    PrinterSettingsDialogContent(
                        viewModel = viewModel,
                        onClose = { showPrinterSettingsDialog = false },
                        navController = navController
                    )
                }
            }
        }

        // Store Settings Dialog
        if (showStoreSettingsDialog) {
            Dialog(
                onDismissRequest = { showStoreSettingsDialog = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                StoreSettingsDialogContent(
                    viewModel = viewModel,
                    onClose = { showStoreSettingsDialog = false }
                )
            }
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
    isLocked: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLocked, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = if (isLocked) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isLocked) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                } else {
                    MaterialTheme.colorScheme.primary
                },
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
                style = MaterialTheme.typography.titleMedium,
                color = if (isLocked) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            
            if (subTitle != null) {
                Text(
                    text = if (isLocked) stringResource(R.string.license_required) else subTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLocked) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }
                )
            }
        }
        
        Icon(
            imageVector = if (isLocked) Icons.Default.Lock else Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = if (isLocked) "锁定" else "箭头",
            tint = if (isLocked) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreSettingsDialogContent(
    viewModel: SettingsViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    val settingsSavedText = stringResource(R.string.settings_saved)
    
    // 获取当前商店信息状态
    val storeName by viewModel.storeName.collectAsState()
    val storeAddress by viewModel.storeAddress.collectAsState()
    val storePhone by viewModel.storePhone.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    
    // 本地状态用于输入字段
    var storeNameInput by remember { mutableStateOf(storeName) }
    var storeAddressInput by remember { mutableStateOf(storeAddress) }
    var storePhoneInput by remember { mutableStateOf(storePhone) }
    var currencySymbolInput by remember { mutableStateOf(currencySymbol) }
    
    LaunchedEffect(storeName, storeAddress, storePhone, currencySymbol) {
        storeNameInput = storeName
        storeAddressInput = storeAddress
        storePhoneInput = storePhone
        currencySymbolInput = currencySymbol
    }
    
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.store_settings)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                ) {
                    // 店铺名称
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.store_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = storeNameInput,
                            onValueChange = { storeNameInput = it },
                            label = { Text(stringResource(R.string.store_name_hint)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 店铺地址
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.store_address),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = storeAddressInput,
                            onValueChange = { storeAddressInput = it },
                            label = { Text(stringResource(R.string.store_address_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 联系电话
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.store_phone),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = storePhoneInput,
                            onValueChange = { storePhoneInput = it },
                            label = { Text(stringResource(R.string.store_phone_hint)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 货币符号
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.currency_symbol),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // 货币选择下拉菜单
                        var expanded by remember { mutableStateOf(false) }
                        val currencyOptions = listOf(
                            "¥" to stringResource(R.string.currency_cny),
                            "$" to stringResource(R.string.currency_usd), 
                            "C$" to stringResource(R.string.currency_cad),
                            "€" to stringResource(R.string.currency_eur),
                            "£" to stringResource(R.string.currency_gbp),
                            "¥JP" to stringResource(R.string.currency_jpy),
                            "₩" to stringResource(R.string.currency_krw),
                            "₹" to stringResource(R.string.currency_inr),
                            "₽" to stringResource(R.string.currency_rub),
                            "₴" to stringResource(R.string.currency_uah)
                        )
                        
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = currencyOptions.find { it.first == currencySymbolInput }?.let { "${it.first} (${it.second})" } ?: currencySymbolInput,
                                onValueChange = { },
                                readOnly = true,
                                label = { Text(stringResource(R.string.currency_symbol_hint)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                currencyOptions.forEach { (symbol, name) ->
                                    DropdownMenuItem(
                                        text = { Text("$symbol ($name)") },
                                        onClick = {
                                            currencySymbolInput = symbol
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                Button(
                    onClick = {
                        // 保存商店信息的功能
                        // viewModel.updateStoreName(storeNameInput)
                        // viewModel.updateStoreAddress(storeAddressInput) 
                        // viewModel.updateStorePhone(storePhoneInput)
                        viewModel.updateCurrencySymbol(currencySymbolInput)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(settingsSavedText)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp)
                ) {
                    Text(stringResource(id = R.string.save_settings))
                }
            }
        }
    }
} 