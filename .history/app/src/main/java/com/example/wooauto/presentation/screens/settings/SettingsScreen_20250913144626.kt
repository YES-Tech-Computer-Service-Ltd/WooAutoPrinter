package com.example.wooauto.presentation.screens.settings

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.wooauto.R
import com.example.wooauto.presentation.components.ScrollableWithEdgeScrim
import com.example.wooauto.presentation.components.SettingsModal
import com.example.wooauto.presentation.screens.settings.PrinterSettings.PrinterSettingsDialogContent
import kotlinx.coroutines.launch
import java.util.Locale

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
    val licenseRequiredMessage = stringResource(R.string.license_required_message)

    // 许可证状态检查 - 使用统一的权限检查逻辑
    val licenseManager = viewModel.licenseManager

    // 进入设置页面时重新验证证书状态
    LaunchedEffect(Unit) {
        Log.d("SettingsScreen", "进入设置页面，重新检查证书状态")
        viewModel.revalidateLicenseStatus()
    }

    val currentLocale by viewModel.currentLocale.collectAsState(initial = Locale.getDefault())

    // 获取统一的资格状态 - 使用新的资格检查系统
    val hasEligibility = licenseManager.hasEligibility

    // 各种对话框状态
    var showWebsiteSettingsDialog by remember { mutableStateOf(false) }
    var showSoundSettingsDialog by remember { mutableStateOf(false) }
    var showPrintTemplatesDialog by remember { mutableStateOf(false) }
    var showPrinterSettingsDialog by remember { mutableStateOf(false) }
    var showLicenseSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
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

                // 设置页面的内容区域：根据左侧主侧栏二级项（settings/{section}）决定内容
                val backStackEntry by navController.currentBackStackEntryAsState()
                val selectedSub = backStackEntry?.arguments?.getString("section") ?: "general"

                ScrollableWithEdgeScrim(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                ) { scrollModifier, _ ->
                    Column(
                        modifier = scrollModifier
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        when (selectedSub) {
                            "general" -> {
                                // 将 General 相关项合并到一个卡片内，视觉更整齐
                                SettingsCategoryCard {
                                    // API 配置
                                    SettingsNavigationItem(
                                        title = stringResource(R.string.api_configuration),
                                        subTitle = if (siteUrl.isNotEmpty() && consumerKey.isNotEmpty() && consumerSecret.isNotEmpty()) stringResource(
                                            R.string.api_configured
                                        ) else stringResource(R.string.api_not_configured),
                                        icon = Icons.Filled.Cloud,
                                        onClick = {
                                            Log.d("设置导航", "点击了API配置项")
                                            showWebsiteSettingsDialog = true
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Store Settings
                                    SettingsNavigationItem(
                                        title = stringResource(R.string.store_settings),
                                        icon = Icons.Default.Store,
                                        subTitle = stringResource(R.string.store_settings_desc),
                                        isLocked = !hasEligibility,
                                        onClick = {
                                            if (hasEligibility) {
                                                Log.d("设置导航", "点击了店铺信息设置项")
                                                navController.navigate("settings/general/store")
                                            } else {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        licenseRequiredMessage
                                                    )
                                                }
                                            }
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Language
                                    SettingItem(
                                        icon = Icons.Outlined.Language,
                                        title = stringResource(id = R.string.language),
                                        subtitle = if (currentLocale.language == "zh") stringResource(
                                            id = R.string.chinese
                                        ) else stringResource(id = R.string.english),
                                        onClick = {
                                            Log.d("设置", "点击了语言设置")
                                            // 改为跳转到设置下的二级页面，保留左侧侧栏与全局TopBar
                                            navController.navigate("settings/general/language")
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // 进入“屏幕设置”二级页
                                    SettingItem(
                                        icon = Icons.Outlined.Language, // 参数占位，不显示图标
                                        title = stringResource(id = R.string.display_settings),
                                        subtitle = run {
                                            val keepOn = viewModel.keepScreenOn.collectAsState().value
                                            val statusText = if (keepOn) stringResource(R.string.status_enabled) else stringResource(R.string.status_disabled)
                                            val brightnessPercent = viewModel.appBrightnessPercent.collectAsState().value
                                            val brightnessText = brightnessPercent?.let { "$it%" } ?: stringResource(R.string.follow_system)
                                            val stayAwakeLabel = stringResource(R.string.keep_screen_on_title)
                                            val brightnessLabel = stringResource(R.string.screen_brightness)
                                            "$stayAwakeLabel: $statusText · $brightnessLabel: $brightnessText"
                                        },
                                        onClick = { navController.navigate("settings/general/display") }
                                    )

                                    // 进入“屏幕设置”二级页
                                }
                            }
                                "printing" -> {
                                    SettingsCategoryCard {
                                        SettingsNavigationItem(
                                            title = stringResource(R.string.printer_settings),
                                            icon = Icons.Filled.Print,
                                            subTitle = run {
                                                val currentPrinter =
                                                    viewModel.currentPrinterConfig.collectAsState().value
                                                val printerStatus =
                                                    viewModel.printerStatus.collectAsState().value
                                                val defaultLabel =
                                                    stringResource(R.string.default_printer_label)
                                                val connectedLabel =
                                                    stringResource(R.string.connected)
                                                when {
                                                    currentPrinter != null -> {
                                                        val base =
                                                            "${currentPrinter.name} - ${currentPrinter.paperWidth}mm"
                                                        if (printerStatus == com.example.wooauto.domain.printer.PrinterStatus.CONNECTED) "$base ($connectedLabel)" else base
                                                    }

                                                    else -> {
                                                        val defaultPrinter =
                                                            viewModel.printerConfigs.collectAsState().value.find { it.isDefault }
                                                        if (defaultPrinter != null) "${defaultPrinter.name} - ${defaultPrinter.paperWidth}mm ($defaultLabel)" else stringResource(
                                                            R.string.no_printers_configured_prompt
                                                        )
                                                    }
                                                }
                                            },
                                            isLocked = !hasEligibility,
                                            onClick = {
                                                if (hasEligibility) showPrinterSettingsDialog =
                                                    true else coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        licenseRequiredMessage
                                                    )
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider()
                                        Spacer(modifier = Modifier.height(8.dp))
                                        SettingsNavigationItem(
                                            title = stringResource(R.string.printer_templates),
                                            icon = Icons.Filled.Edit,
                                            subTitle = stringResource(R.string.printer_templates_desc),
                                            isLocked = !hasEligibility,
                                            onClick = {
                                                if (hasEligibility) showPrintTemplatesDialog =
                                                    true else coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        licenseRequiredMessage
                                                    )
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider()
                                        Spacer(modifier = Modifier.height(8.dp))
                                        // 内嵌自动打印设置
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            // 顶部开关行
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = stringResource(R.string.automatic_printing),
                                                        style = MaterialTheme.typography.titleMedium
                                                    )
                                                    Text(
                                                        text = stringResource(R.string.automatic_printing_desc),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                val autoEnabled = viewModel.automaticPrinting.collectAsState().value
                                                Switch(
                                                    checked = autoEnabled,
                                                    onCheckedChange = { enabled ->
                                                        if (hasEligibility) {
                                                            viewModel.updateAutomaticPrinting(enabled)
                                                            // 通知服务重启轮询，使设置即时生效
                                                            viewModel.notifyServiceToRestartPolling()
                                                        } else {
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar(licenseRequiredMessage)
                                                            }
                                                        }
                                                    }
                                                )
                                            }

                                            // 模板份数配置：仅在开启时显示
                                            val autoEnabledNow = viewModel.automaticPrinting.collectAsState().value
                                            if (autoEnabledNow) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = stringResource(R.string.print_templates),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )

                                                // 加载模板与已保存份数
                                                val templateConfigViewModel: com.example.wooauto.presentation.screens.templatePreview.TemplateConfigViewModel = hiltViewModel()
                                                val allConfigs by templateConfigViewModel.allConfigs.collectAsState()
                                                LaunchedEffect(Unit) { templateConfigViewModel.loadAllConfigs() }

                                                var templatePrintCopies by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
                                                LaunchedEffect(Unit) {
                                                    try { templatePrintCopies = viewModel.getTemplatePrintCopies() } catch (_: Exception) {}
                                                }

                                                fun updateCopies(id: String, copies: Int) {
                                                    val newMap = templatePrintCopies.toMutableMap()
                                                    if (copies > 0) newMap[id] = copies else newMap.remove(id)
                                                    templatePrintCopies = newMap
                                                    // 即时保存，避免弹窗
                                                    viewModel.updateTemplatePrintCopies(newMap)
                                                }

                                                // 默认模板 + 自定义模板
                                                val defaultTemplates = listOf(
                                                    Triple("full_details", com.example.wooauto.domain.templates.TemplateType.FULL_DETAILS, stringResource(R.string.auto_print_template_full_details)),
                                                    Triple("delivery", com.example.wooauto.domain.templates.TemplateType.DELIVERY, stringResource(R.string.auto_print_template_delivery)),
                                                    Triple("kitchen", com.example.wooauto.domain.templates.TemplateType.KITCHEN, stringResource(R.string.auto_print_template_kitchen))
                                                )
                                                val customTemplates = allConfigs.filter { it.templateId.startsWith("custom_") }
                                                    .map { Triple(it.templateId, com.example.wooauto.domain.templates.TemplateType.FULL_DETAILS, it.templateName) }

                                                (defaultTemplates + customTemplates).forEach { (id, type, name) ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(text = name, modifier = Modifier.weight(1f))
                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                            IconButton(onClick = {
                                                                val current = templatePrintCopies[id] ?: 0
                                                                if (current > 0) updateCopies(id, current - 1)
                                                            }) { Icon(imageVector = Icons.Default.RemoveCircleOutline, contentDescription = null) }
                                                            Text(text = (templatePrintCopies[id] ?: 0).toString())
                                                            IconButton(onClick = {
                                                                val current = templatePrintCopies[id] ?: 0
                                                                if (current < 9) updateCopies(id, current + 1)
                                                            }) { Icon(imageVector = Icons.Default.AddCircleOutline, contentDescription = null) }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                "notification" -> {
                                    SettingsCategoryCard {
                                        SettingsNavigationItem(
                                            title = stringResource(R.string.sound_settings),
                                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                                            subTitle = run {
                                                val volume =
                                                    viewModel.soundVolume.collectAsState().value
                                                val soundType =
                                                    viewModel.soundType.collectAsState().value
                                                val enabled =
                                                    viewModel.soundEnabled.collectAsState().value
                                                val soundTypeDisplayName =
                                                    viewModel.getSoundTypeDisplayName(soundType)
                                                if (enabled) {
                                                    val volumeFormatted = stringResource(
                                                        R.string.sound_volume_format,
                                                        volume
                                                    )
                                                    val soundTypeFormatted = stringResource(
                                                        R.string.sound_type_format,
                                                        soundTypeDisplayName
                                                    )
                                                    stringResource(
                                                        R.string.sound_status_format,
                                                        volumeFormatted,
                                                        soundTypeFormatted
                                                    )
                                                } else stringResource(R.string.sound_disabled)
                                            },
                                            isLocked = !hasEligibility,
                                            onClick = {
                                                if (hasEligibility) showSoundSettingsDialog =
                                                    true else coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        licenseRequiredMessage
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                                "about" -> {
                                    SettingsCategoryCard {
                                        SettingItem(
                                            icon = Icons.Default.VpnKey,
                                            title = stringResource(id = R.string.license_settings),
                                            subtitle = viewModel.licenseStatusText.collectAsState().value,
                                            onClick = { showLicenseSettingsDialog = true }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider()
                                        Spacer(modifier = Modifier.height(8.dp))
                                        SettingsNavigationItem(
                                            icon = Icons.Outlined.Info,
                                            title = stringResource(R.string.about),
                                            subTitle = run {
                                                val currentVersion =
                                                    viewModel.updateInfo.collectAsState().value?.currentVersion?.toVersionString()
                                                        ?: ""
                                                val updateInfo =
                                                    viewModel.updateInfo.collectAsState().value
                                                val isCheckingUpdate =
                                                    viewModel.isCheckingUpdate.collectAsState().value
                                                if (isCheckingUpdate) stringResource(R.string.fetching_version_info) else if (currentVersion.isNotEmpty()) {
                                                    if (updateInfo?.needsUpdate() == true) {
                                                        val latestVersion =
                                                            updateInfo.latestVersion.toVersionString()
                                                        stringResource(
                                                            R.string.about_version_info,
                                                            currentVersion,
                                                            stringResource(
                                                                R.string.version_needs_update,
                                                                latestVersion
                                                            )
                                                        )
                                                    } else stringResource(
                                                        R.string.about_version_info,
                                                        currentVersion,
                                                        stringResource(R.string.version_is_latest)
                                                    )
                                                } else stringResource(R.string.fetching_version_info)
                                            },
                                            onClick = {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        viewModel.getAboutInfoText()
                                                    )
                                                }
                                            }
                                        )
                                        val updateInfo = viewModel.updateInfo.collectAsState().value
                                        if (updateInfo?.needsUpdate() == true) {
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
                                                    val isDownloading =
                                                        viewModel.isDownloading.collectAsState().value
                                                    val downloadProgress =
                                                        viewModel.downloadProgress.collectAsState().value
                                                    Text(
                                                        text = if (isDownloading) stringResource(R.string.downloading_update) else stringResource(
                                                            R.string.download_and_install_update
                                                        ),
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
                                                            text = stringResource(
                                                                R.string.download_running,
                                                                downloadProgress
                                                            ),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.7f
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }

        // 语言设置改为二级页面，移除弹窗

        // Website Settings Dialog
        if (showWebsiteSettingsDialog) {
            SettingsModal(onDismissRequest = { showWebsiteSettingsDialog = false }) {
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
            SettingsModal(onDismissRequest = {
                soundSettingsViewModel.stopSound()
                showSoundSettingsDialog = false
                viewModel.refreshSoundSettings()
            }) {
                SoundSettingsDialogContent(
                    onClose = {
                        soundSettingsViewModel.stopSound()
                        showSoundSettingsDialog = false
                        viewModel.refreshSoundSettings()
                    }
                )
            }
        }

        // Print Templates Dialog
        if (showPrintTemplatesDialog) {
            SettingsModal(onDismissRequest = { showPrintTemplatesDialog = false }) {
                PrintTemplatesDialogContent(onClose = { showPrintTemplatesDialog = false })
            }
        }

        // Printer Settings Dialog
        if (showPrinterSettingsDialog) {
            SettingsModal(onDismissRequest = { showPrinterSettingsDialog = false }) {
                PrinterSettingsDialogContent(
                    viewModel = viewModel,
                    onClose = { showPrinterSettingsDialog = false },
                    navController = navController
                )
            }
        }


        // License Settings Dialog
        if (showLicenseSettingsDialog) {
            SettingsModal(onDismissRequest = { showLicenseSettingsDialog = false }) {
                LicenseSettingsDialogContent(
                    onClose = { showLicenseSettingsDialog = false },
                    onLicenseActivated = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("许可证已激活")
                            viewModel.revalidateLicenseStatus()
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
            // 去除前置图标，保持简洁列表样式
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
            // 去除尾部图标
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
            // 去除前置图标
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
            // 去除尾部图标
        }
    }
