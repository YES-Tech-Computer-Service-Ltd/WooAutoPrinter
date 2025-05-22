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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    
    val currentLocale by viewModel.currentLocale.collectAsState(initial = Locale.getDefault())

    // 各种对话框状态
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showWebsiteSettingsDialog by remember { mutableStateOf(false) }
    var showAutomationSettingsDialog by remember { mutableStateOf(false) }
    var showSoundSettingsDialog by remember { mutableStateOf(false) }
    var showPrintTemplatesDialog by remember { mutableStateOf(false) }
    
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
                            onClick = {
                                Log.d("设置导航", "点击了打印设置项")
                                navController.navigate(Screen.PrinterSettings.route)
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
                            onClick = {
                                Log.d("设置导航", "点击了模板设置项")
                                showPrintTemplatesDialog = true
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
                            onClick = {
                                Log.d("设置导航", "点击了声音设置项")
                                showSoundSettingsDialog = true
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
                            onClick = {
                                Log.d("设置导航", "点击了自动打印设置项")
                                showAutomationSettingsDialog = true 
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
                            subTitle = run {
                                val currentVersion = viewModel.updateInfo.collectAsState().value?.currentVersion?.toVersionString() ?: ""
                                val hasUpdate = viewModel.hasUpdate.collectAsState().value
                                val latestVersion = viewModel.updateInfo.collectAsState().value?.latestVersion?.toVersionString() ?: ""
                                
                                if (currentVersion.isNotEmpty()) {
                                    if (hasUpdate && latestVersion.isNotEmpty()) {
                                        stringResource(R.string.about_version_info, currentVersion, 
                                            stringResource(R.string.version_needs_update, latestVersion))
                                    } else {
                                        stringResource(R.string.about_version_info, currentVersion,
                                            stringResource(R.string.version_is_latest))
                                    }
                                } else {
                                    ""
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
                                            text = "$downloadProgress%",
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
                WebsiteSettingsDialogContent(
                    viewModel = viewModel,
                    onClose = { showWebsiteSettingsDialog = false }
                )
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