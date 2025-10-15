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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream
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
    // 如需重组触发，可订阅；当前不使用则移除以免告警
    
    // 预先获取需要用到的字符串资源
    val licenseRequiredMessage = stringResource(R.string.license_required_message)
    
    // 许可证状态检查 - 使用统一的权限检查逻辑
    val licenseManager = viewModel.licenseManager
    
    // 进入设置页面时重新验证证书状态
    LaunchedEffect(Unit) {
        com.example.wooauto.utils.UiLog.d("SettingsScreen", "进入设置页面，重新检查证书状态")
        viewModel.revalidateLicenseStatus()
    }
    
    val currentLocale by viewModel.currentLocale.collectAsState(initial = Locale.getDefault())
    
    // 获取统一的资格状态 - 使用新的资格检查系统
    val hasEligibility = licenseManager.hasEligibility

    // 各种对话框状态
    var showWebsiteSettingsDialog by remember { mutableStateOf(false) }
    // 声音设置改为二级页面，移除旧弹窗状态
    // 打印模板改为二级页面展示，移除旧弹窗状态
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
                                com.example.wooauto.utils.UiLog.d("设置导航", "点击了API配置项")
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
                                                com.example.wooauto.utils.UiLog.d("设置导航", "点击了店铺信息设置项")
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
                                            com.example.wooauto.utils.UiLog.d("设置", "点击了语言设置")
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
                                            icon = null,
                                            subTitle = stringResource(R.string.printer_templates_desc),
                            isLocked = !hasEligibility,
                            onClick = {
                                if (hasEligibility) {
                                                    // 跳转到二级页面：settings/printing/templates
                                                    navController.navigate("settings/printing/templates")
                                                } else coroutineScope.launch {
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

                                                (defaultTemplates + customTemplates).forEach { (id, _ /*type*/, name) ->
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
                                    // 声音设置直接内嵌为一级页面内容
                                    val soundVM: SoundSettingsViewModel = hiltViewModel()
                                    val context = LocalContext.current
                                    val soundEnabled = soundVM.soundEnabled.collectAsState().value
                                    val volume = soundVM.notificationVolume.collectAsState().value
                                    val soundType = soundVM.soundType.collectAsState().value
                                    val customSoundUri = soundVM.customSoundUri.collectAsState().value
                                    val keepRingingUntilAccept = soundVM.keepRingingUntilAccept.collectAsState().value

                                    // 文件选择器与复制到内部存储
                                    val audioFilePicker = rememberLauncherForActivityResult(
                                        contract = ActivityResultContracts.StartActivityForResult()
                                    ) { result ->
                                        if (result.resultCode == Activity.RESULT_OK) {
                                            result.data?.data?.let { uri ->
                                                try {
                                                    val fileName = getFileNameFromUriLocal(context, uri) ?: "custom_sound.mp3"
                                                    val soundDir = File(context.filesDir, "sounds").apply { if (!exists()) mkdirs() }
                                                    val destinationFile = File(soundDir, fileName)
                                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                                        FileOutputStream(destinationFile).use { output -> input.copyTo(output) }
                                                    }
                                                    val internalPath = destinationFile.absolutePath
                                                    coroutineScope.launch {
                                                        soundVM.setCustomSoundUri(internalPath)
                                                        if (soundType != com.example.wooauto.domain.models.SoundSettings.SOUND_TYPE_CUSTOM) {
                                                            soundVM.setSoundType(com.example.wooauto.domain.models.SoundSettings.SOUND_TYPE_CUSTOM)
                                                        }
                                                        soundVM.saveSettings()
                                                        snackbarHostState.showSnackbar(context.getString(R.string.audio_file_selected))
                                                    }
                                                } catch (e: Exception) {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar(context.getString(R.string.audio_file_select_error) + ": " + (e.message ?: ""))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    fun openAudioFilePicker() {
                                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                            type = "audio/*"
                                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                                        }
                                        // 该函数在 @Composable 范围内，直接调用 launcher 即可，不涉及 @Composable 调用
                                        audioFilePicker.launch(intent)
                                    }

                                    // 辅助函数已提升为文件级，避免局部声明的可见性/顺序问题

                                    SettingsCategoryCard {
                                        // 概览行
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val soundTypeDisplayName = soundVM.getSoundTypeDisplayName(soundType)
                                            val displayVolume = (volume / 10).coerceIn(0, 100)
                                            val volumeFormatted = stringResource(R.string.sound_volume_format, displayVolume)
                                            val soundTypeFormatted = stringResource(R.string.sound_type_format, soundTypeDisplayName)
                                            val statusText = stringResource(R.string.sound_status_format, volumeFormatted, soundTypeFormatted)
                                            Text(
                                                text = if (soundEnabled) statusText else stringResource(R.string.sound_disabled),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        
                                        // 启用开关
                                        SoundEnabledSwitch(
                                            enabled = soundEnabled,
                                            onEnabledChange = { enabled ->
                                                if (hasEligibility) {
                                                    coroutineScope.launch {
                                                        soundVM.setSoundEnabled(enabled)
                                                        soundVM.saveSettings()
                                                    }
                                                } else {
                                                    coroutineScope.launch { snackbarHostState.showSnackbar(licenseRequiredMessage) }
                                                }
                                            }
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // 持续响铃
                                        KeepRingingSwitch(
                                            enabled = keepRingingUntilAccept,
                                            onEnabledChange = { value ->
                                                if (hasEligibility) {
                                                    coroutineScope.launch { soundVM.setKeepRingingUntilAccept(value) }
                                                } else {
                                                    coroutineScope.launch { snackbarHostState.showSnackbar(licenseRequiredMessage) }
                                                }
                                            },
                                            isSoundEnabled = soundEnabled
                                        )

                                        Spacer(modifier = Modifier.height(20.dp))

                                        // 音量（0-100 显示）
                                        Text(
                                            text = stringResource(id = R.string.notification_volume),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(id = R.string.notification_volume_desc),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        val displayVolume = (volume / 10).coerceIn(0, 100)
                                        androidx.compose.material3.Slider(
                                            value = displayVolume.toFloat(),
                                            onValueChange = { newValue ->
                                                if (hasEligibility) {
                                                    val mapped = (newValue.toInt() * 10).coerceIn(0, 1000)
                                                    coroutineScope.launch {
                                                        soundVM.setVolume(mapped)
                                                        soundVM.saveSettings()
                                                    }
                                                } else {
                                                    coroutineScope.launch { snackbarHostState.showSnackbar(licenseRequiredMessage) }
                                                }
                                            },
                                            valueRange = 0f..100f,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = androidx.compose.material3.SliderDefaults.colors(
                                                thumbColor = if (soundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                activeTrackColor = if (soundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                            enabled = soundEnabled
                                        )

                                        Spacer(modifier = Modifier.height(20.dp))

                                        // 声音类型
                                        Text(
                                            text = stringResource(id = R.string.sound_type_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(id = R.string.sound_type_desc),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        SoundTypeSelector(
                                            selectedType = soundType,
                                            customSoundUri = customSoundUri,
                                            onTypeSelected = { type ->
                                                if (hasEligibility) {
                                                    coroutineScope.launch {
                                                        soundVM.setSoundType(type)
                                                    }
                                                } else {
                                                    coroutineScope.launch { snackbarHostState.showSnackbar(licenseRequiredMessage) }
                                                }
                                            },
                                            onSelectCustomSound = {
                                                if (hasEligibility) openAudioFilePicker() else coroutineScope.launch { snackbarHostState.showSnackbar(licenseRequiredMessage) }
                                            },
                                            enabled = soundEnabled
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // 测试声音
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = { soundVM.playTestSound() },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = soundEnabled && hasEligibility
                                        ) {
                                            androidx.compose.material3.Icon(
                                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = null,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Text(stringResource(id = R.string.sound_test))
                                        }
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
        /* 已内嵌到 Printing 区域，移除旧弹窗 */
        /* if (showAutomationSettingsDialog) {
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
        } */

        // 声音设置已改为二级页面，移除旧弹窗

        // 打印模板已改为二级页面，移除旧弹窗

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
            // 使用更接近“面板”的浅色，减少阴影依赖
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(
            // 降低阴影，避免上下两端出现重影/分层感
            defaultElevation = 0.dp,
            pressedElevation = 1.dp
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) { content() }
    }
}

@Composable
fun SettingsNavigationItem(
        @Suppress("UNUSED_PARAMETER") icon: ImageVector?,
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
            // 去除前置图标，保持简洁列表样式（icon 可空，保留参数以兼容旧调用）
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
    @Suppress("UNUSED_PARAMETER") icon: ImageVector,
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

    // 在文件末尾添加：从 URI 获取显示名（供本文件调用）
    private fun getFileNameFromUriLocal(context: android.content.Context, uri: Uri): String? {
        var fileName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) fileName = cursor.getString(index)
            }
        }
        return fileName
    }
