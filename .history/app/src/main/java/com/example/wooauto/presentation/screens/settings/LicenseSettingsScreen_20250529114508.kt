package com.example.wooauto.presentation.screens.settings

import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.example.wooauto.R
import com.example.wooauto.licensing.LicenseDataStore
import com.example.wooauto.licensing.LicenseValidator
import com.example.wooauto.licensing.LicenseVerificationManager
import com.example.wooauto.licensing.LicenseDetailsResult
import com.example.wooauto.licensing.TrialTokenManager
import com.example.wooauto.licensing.EligibilityStatus
import com.example.wooauto.licensing.EligibilityInfo
import com.example.wooauto.licensing.EligibilitySource
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseInputSection(
    onLicenseComplete: (String) -> Unit,
    isEditable: Boolean,
    savedLicenseKey: String
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 4 个输入框
    val focusRequesters = remember { List(4) { FocusRequester() } }
    val inputs = remember(savedLicenseKey) {
        // 确保inputs列表始终有4个元素，避免IndexOutOfBoundsException
        val parts = savedLicenseKey.split("-").take(4).map { it.take(4) }
        mutableStateListOf(
            parts.getOrElse(0) { "" },
            parts.getOrElse(1) { "" },
            parts.getOrElse(2) { "" },
            parts.getOrElse(3) { "" }
        )
    }

    var shouldPaste by remember { mutableStateOf(false) }

    fun clipboardContainsValidLicense(): Boolean {
        val clipText = clipboardManager.getText()?.text?.trim() ?: return false
        val clean = clipText.filter { it.isLetterOrDigit() }.uppercase()
        return clean.length == 16 && clean.chunked(4).size == 4
    }

    fun tryPasteFromClipboard() {
        val clipText = clipboardManager.getText()?.text?.trim() ?: return
        val clean = clipText.filter { it.isLetterOrDigit() }.uppercase()
        val parts = clean.chunked(4)
        if (parts.size == 4 && parts.all { it.length == 4 }) {
            // 确保安全访问inputs列表
            for (index in parts.indices) {
                if (index < inputs.size) {
                    inputs[index] = parts[index].trim()
                }
            }
            val license = parts.joinToString("-")
            onLicenseComplete(license)
            focusRequesters[3].freeFocus()
        }
    }

    LaunchedEffect(Unit) {
        if (isEditable) {
            focusRequesters[0].requestFocus()
            tryPasteFromClipboard()
        }
    }

    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                shouldPaste = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(shouldPaste) {
        if (shouldPaste && isEditable) {
            tryPasteFromClipboard()
            shouldPaste = false
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in 0 until 4) {
            OutlinedTextField(
                value = inputs[i].trim(),
                onValueChange = { value ->
                    if (!isEditable) return@OutlinedTextField

                    val filtered = value.filter { it.isLetterOrDigit() }.uppercase()

                    if (filtered.length == 16 && i == 0) {
                        val parts = filtered.chunked(4)
                        if (parts.size == 4) {
                            inputs.forEachIndexed { index, _ ->
                                inputs[index] = parts[index].trim()
                            }
                            val license = parts.joinToString("-")
                            onLicenseComplete(license)
                            focusRequesters[3].freeFocus()
                            return@OutlinedTextField
                        }
                    }

                    if (filtered.length <= 4) {
                        inputs[i] = filtered
                        if (filtered.length == 4 && i < 3) {
                            focusRequesters[i + 1].requestFocus()
                        }
                    }

                    if (inputs.all { it.length == 4 }) {
                        val license = inputs.joinToString("-")
                        onLicenseComplete(license)
                    }
                },
                enabled = isEditable,
                modifier = Modifier
                    .width(90.dp)
                    .height(56.dp)
                    .focusRequester(focusRequesters[i]),
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 0.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = Color.Gray,
                    disabledBorderColor = Color.Gray,
                    disabledLabelColor = Color.Gray,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@Composable
fun LicenseInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseSettingsDialogContent(
    onClose: () -> Unit,
    onLicenseActivated: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 获取LicenseManager实例
    val app = context.applicationContext as com.example.wooauto.WooAutoApplication
    val licenseManager = app.licenseManager
    
    // 统一使用LicenseManager的状态，避免混用多个数据源
    val eligibilityInfo by licenseManager.eligibilityInfo.observeAsState()
    val licenseInfo by licenseManager.licenseInfo.observeAsState()
    
    // 从DataStore获取详细的许可证信息用于显示
    val savedStartDate by LicenseDataStore.getLicenseStartDate(context).collectAsState(initial = null)
    val savedEndDate by LicenseDataStore.getLicenseEndDate(context).collectAsState(initial = null)
    val licenseEdition by LicenseDataStore.getLicenseEdition(context).collectAsState(initial = "Spire")
    val capabilities by LicenseDataStore.getCapabilities(context).collectAsState(initial = "cap1, cap2")
    val licensedTo by LicenseDataStore.getLicensedTo(context).collectAsState(initial = "MockCustomer")
    val licenseKey by LicenseDataStore.getLicenseKey(context).collectAsState(initial = "")
    
    var licenseCode by remember { mutableStateOf(licenseKey) }
    
    // 基于统一的资格状态判断是否已激活
    val isLicenseActivated = eligibilityInfo?.isLicensed ?: false
    val isTrialActive = eligibilityInfo?.isTrialActive ?: true
    val trialDaysRemaining = eligibilityInfo?.trialDaysRemaining ?: 10

    // 获取 deviceId 和 appId
    val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    val appId = "wooauto_app"

    // 获取屏幕宽度
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isSmallScreen = screenWidthDp < 512

    // 状态：许可证是否已过期
    var isLicenseExpired by remember { mutableStateOf(false) }
    var hasParseError by remember { mutableStateOf(false) }
    var isManualRefreshing by remember { mutableStateOf(false) }

    // 智能验证逻辑：只在必要时验证
    LaunchedEffect(Unit) {
        try {
            // 检查当前状态是否需要重新验证
            val currentEligibility = eligibilityInfo
            val needsValidation = when {
                currentEligibility == null -> {
                    Log.d("LicenseSettingsDialog", "状态为空，需要验证")
                    true
                }
                currentEligibility.status == EligibilityStatus.UNKNOWN -> {
                    Log.d("LicenseSettingsDialog", "状态未知，需要验证")
                    true
                }
                currentEligibility.status == EligibilityStatus.CHECKING -> {
                    Log.d("LicenseSettingsDialog", "状态为验证中，可能之前验证未完成，需要重新验证")
                    true
                }
                licenseManager.shouldRevalidate(forceThresholdMinutes = 60) -> {
                    Log.d("LicenseSettingsDialog", "距离上次验证超过1小时，需要重新验证")
                    true
                }
                else -> {
                    Log.d("LicenseSettingsDialog", "当前状态有效，无需重新验证: ${currentEligibility.status}")
                    false
                }
            }
            
            if (needsValidation) {
                // 只有在需要时才进行验证
                launch {
                    Log.d("LicenseSettingsDialog", "开始必要的后台验证")
                    
                    val isValid = licenseManager.forceRevalidateAndSync(context)
                    
                    Log.d("LicenseSettingsDialog", "后台验证完成: $isValid")
                }
            }
        } catch (e: Exception) {
            Log.e("LicenseSettingsDialog", "智能验证检查异常 - ${e.message}", e)
        }
    }

    // 手动刷新功能
    val manualRefresh: () -> Unit = {
        if (!isManualRefreshing) {
            isManualRefreshing = true
            coroutineScope.launch {
                try {
                    Log.d("LicenseSettingsDialog", "用户手动刷新许可证状态")
                    
                    val isValid = licenseManager.forceRevalidateAndSync(context)
                    
                    Log.d("LicenseSettingsDialog", "手动刷新完成: $isValid")
                    
                    val message = if (isValid) "许可证状态已刷新" else "刷新完成"
                    snackbarHostState.showSnackbar(message)
                } catch (e: Exception) {
                    Log.e("LicenseSettingsDialog", "手动刷新失败: ${e.message}", e)
                    snackbarHostState.showSnackbar("刷新失败: ${e.message}")
                } finally {
                    isManualRefreshing = false
                }
            }
        }
    }

    // 在 LaunchedEffect 中解析日期并更新状态
    LaunchedEffect(savedEndDate) {
        if (savedEndDate != null) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                sdf.timeZone = TimeZone.getDefault()
                val endDateParsed = sdf.parse(savedEndDate) ?: Date(0)
                val currentCalendar = Calendar.getInstance(TimeZone.getDefault())
                isLicenseExpired = endDateParsed.before(currentCalendar.time)
                hasParseError = false
            } catch (e: Exception) {
                Log.e("LicenseSettingsDialog", "解析结束日期时出错: ${e.message}", e)
                hasParseError = true
                isLicenseExpired = false
            }
        } else {
            isLicenseExpired = false
            hasParseError = false
        }
    }

    // 同步 licenseCode with licenseKey
    LaunchedEffect(licenseKey) {
        if (licenseKey.isNotEmpty() && licenseCode != licenseKey) {
            licenseCode = licenseKey
        }
    }

    // 移除阻塞性等待，使用默认状态确保UI立即可用
    // 如果eligibilityInfo为空，使用默认的允许状态
    val safeEligibilityInfo = eligibilityInfo ?: EligibilityInfo(
        status = EligibilityStatus.ELIGIBLE,
        isTrialActive = true,
        trialDaysRemaining = 10,
        displayMessage = "正在加载权限状态...",
        source = EligibilitySource.TRIAL
    )
    
    Log.d("LicenseSettingsDialog", "渲染UI - 资格状态: ${safeEligibilityInfo.status}, 试用天数: ${safeEligibilityInfo.trialDaysRemaining}")

    // 基于统一的资格状态显示逻辑
    val (licenseStatusText, statusIcon, statusBackgroundColor) = when (safeEligibilityInfo.status) {
        EligibilityStatus.ELIGIBLE -> {
            if (safeEligibilityInfo.isLicensed) {
                Triple(
                    "License is valid",
                    Icons.Default.CheckCircle,
                    Color(0xFFE0F7E0)
                )
            } else if (safeEligibilityInfo.isTrialActive) {
                Triple(
                    "Trial Mode - ${safeEligibilityInfo.trialDaysRemaining} days remaining",
                    Icons.Default.Timer,
                    Color(0xFFE0F7E0)
                )
            } else {
                Triple(
                    "Unknown eligible status",
                    Icons.Default.CheckCircle,
                    Color(0xFFE0F7E0)
                )
            }
        }
        EligibilityStatus.INELIGIBLE -> {
            Triple(
                "Trial expired, please activate a license",
                Icons.Default.Timer,
                Color(0xFFFFE0E0)
            )
        }
        EligibilityStatus.CHECKING -> {
            Triple(
                "Checking license status...",
                Icons.Default.Timer,
                Color(0xFFFFE0B0)
            )
        }
        else -> {
            Triple(
                "License status unknown",
                Icons.Default.Timer,
                Color(0xFFFFE0E0)
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 32.dp, horizontal = 16.dp), // Margins for the dialog card
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(text = stringResource(id = R.string.license_settings)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back"
                            )
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
                    .verticalScroll(rememberScrollState())
            ) {
                // 状态框
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = statusBackgroundColor,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (safeEligibilityInfo.isLicensed) Color.Green else Color.Red
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = licenseStatusText,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when {
                                    safeEligibilityInfo.isLicensed -> "No action required."
                                    safeEligibilityInfo.isTrialActive -> "You are in trial mode."
                                    else -> "Please activate a new license."
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
                            // 显示最后验证时间
                            val lastVerified = licenseInfo?.lastVerifiedTime ?: 0
                            if (lastVerified > 0) {
                                val timeSinceVerification = licenseManager.getTimeSinceLastVerification()
                                val timeText = when {
                                    timeSinceVerification < 1 -> "刚刚验证"
                                    timeSinceVerification < 60 -> "${timeSinceVerification}分钟前验证"
                                    timeSinceVerification < 1440 -> "${timeSinceVerification / 60}小时前验证"
                                    else -> "${timeSinceVerification / 1440}天前验证"
                                }
                                Text(
                                    text = timeText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        // 手动刷新按钮
                        IconButton(
                            onClick = manualRefresh,
                            enabled = !isManualRefreshing
                        ) {
                            if (isManualRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "刷新许可证状态",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 输入框和激活按钮
                if (isSmallScreen) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        LicenseInputSection(
                            onLicenseComplete = { finalKey ->
                                licenseCode = finalKey
                            },
                            isEditable = !isLicenseActivated,
                            savedLicenseKey = licenseCode
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    // 激活许可证的逻辑
                                    try {
                                        val deviceId = Settings.Secure.getString(
                                            context.contentResolver,
                                            Settings.Secure.ANDROID_ID
                                        )
                                        val clean = licenseCode.filter { it.isLetterOrDigit() || it == '-' }
                                        Log.d("LicenseDebug", "Activating license: $clean")
                                        val result = LicenseValidator.activateLicense(clean, deviceId)
                                        Log.d(
                                            "LicenseDebug",
                                            "Activation result: success=${result.success}, message=${result.message}"
                                        )

                                        if (result.success) {
                                            when (val details = LicenseValidator.getLicenseDetails(clean)) {
                                                is LicenseDetailsResult.Success -> {
                                                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                                    sdf.timeZone = TimeZone.getDefault()
                                                    val localStartDate = sdf.format(Calendar.getInstance().time)

                                                    val calcEnd = LicenseDataStore.calculateEndDate(
                                                        localStartDate,
                                                        details.validity
                                                    )
                                                    Log.d("LicenseDebug", "Activation: calcEnd=$calcEnd")
                                                    
                                                    // 清除并保存新的许可证信息
                                                    LicenseDataStore.clearLicenseInfo(context)
                                                    LicenseDataStore.saveLicenseStartDate(context, localStartDate)
                                                    LicenseDataStore.saveLicenseEndDate(context, calcEnd)
                                                    LicenseDataStore.saveLicenseInfo(
                                                        context,
                                                        true,
                                                        calcEnd,
                                                        clean,
                                                        details.edition,
                                                        details.capabilities,
                                                        details.licensedTo
                                                    )
                                                    LicenseDataStore.setLicensed(context, true)
                                                    
                                                    // 强制结束试用期
                                                    TrialTokenManager.forceExpireTrial(context)
                                                    Log.d("LicenseSettingsDialog", "试用期已结束")
                                                    
                                                    // 重新验证许可证状态，更新LicenseManager的状态
                                                    val isValid = licenseManager.forceRevalidateAndSync(context)
                                                    Log.d("LicenseSettingsDialog", "许可证激活后统一验证结果: $isValid")
                                                    if (isValid) {
                                                        snackbarHostState.showSnackbar(
                                                            context.getString(R.string.license_success, calcEnd)
                                                        )
                                                        onLicenseActivated()
                                                    }
                                                }
                                                is LicenseDetailsResult.Error -> {
                                                    Log.e("LicenseDebug", "Activation error: ${details.message}")
                                                    snackbarHostState.showSnackbar("Failed to get license details: ${details.message}")
                                                }
                                            }
                                        } else {
                                            snackbarHostState.showSnackbar("Failed to activate license: ${result.message}")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("LicenseSettingsDialog", "Error during activation: ${e.message}", e)
                                        snackbarHostState.showSnackbar("Error: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier
                                .width(120.dp)
                                .height(48.dp),
                            enabled = !isLicenseActivated,
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = Color.Gray.copy(alpha = 0.3f),
                                disabledContentColor = Color.White.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(stringResource(R.string.activate))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LicenseInputSection(
                            onLicenseComplete = { finalKey ->
                                licenseCode = finalKey
                            },
                            isEditable = !isLicenseActivated,
                            savedLicenseKey = licenseCode
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    // 重复的激活许可证逻辑（与上面相同）
                                    try {
                                        val deviceId = Settings.Secure.getString(
                                            context.contentResolver,
                                            Settings.Secure.ANDROID_ID
                                        )
                                        val clean = licenseCode.filter { it.isLetterOrDigit() || it == '-' }
                                        Log.d("LicenseDebug", "Activating license: $clean")
                                        val result = LicenseValidator.activateLicense(clean, deviceId)
                                        Log.d(
                                            "LicenseDebug",
                                            "Activation result: success=${result.success}, message=${result.message}"
                                        )

                                        if (result.success) {
                                            when (val details = LicenseValidator.getLicenseDetails(clean)) {
                                                is LicenseDetailsResult.Success -> {
                                                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                                    sdf.timeZone = TimeZone.getDefault()
                                                    val localStartDate = sdf.format(Calendar.getInstance().time)

                                                    val calcEnd = LicenseDataStore.calculateEndDate(
                                                        localStartDate,
                                                        details.validity
                                                    )
                                                    Log.d("LicenseDebug", "Activation: calcEnd=$calcEnd")
                                                    
                                                    // 清除并保存新的许可证信息
                                                    LicenseDataStore.clearLicenseInfo(context)
                                                    LicenseDataStore.saveLicenseStartDate(context, localStartDate)
                                                    LicenseDataStore.saveLicenseEndDate(context, calcEnd)
                                                    LicenseDataStore.saveLicenseInfo(
                                                        context,
                                                        true,
                                                        calcEnd,
                                                        clean,
                                                        details.edition,
                                                        details.capabilities,
                                                        details.licensedTo
                                                    )
                                                    LicenseDataStore.setLicensed(context, true)
                                                    
                                                    // 强制结束试用期
                                                    TrialTokenManager.forceExpireTrial(context)
                                                    Log.d("LicenseSettingsDialog", "试用期已结束")
                                                    
                                                    // 重新验证许可证状态，更新LicenseManager的状态
                                                    val isValid = licenseManager.forceRevalidateAndSync(context)
                                                    Log.d("LicenseSettingsDialog", "许可证激活后统一验证结果: $isValid")
                                                    if (isValid) {
                                                        snackbarHostState.showSnackbar(
                                                            context.getString(R.string.license_success, calcEnd)
                                                        )
                                                        onLicenseActivated()
                                                    }
                                                }
                                                is LicenseDetailsResult.Error -> {
                                                    Log.e("LicenseDebug", "Activation error: ${details.message}")
                                                    snackbarHostState.showSnackbar("Failed to get license details: ${details.message}")
                                                }
                                            }
                                        } else {
                                            snackbarHostState.showSnackbar("Failed to activate license: ${result.message}")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("LicenseSettingsDialog", "Error during activation: ${e.message}", e)
                                        snackbarHostState.showSnackbar("Error: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier
                                .width(120.dp)
                                .height(48.dp),
                            enabled = !isLicenseActivated,
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = Color.Gray.copy(alpha = 0.3f),
                                disabledContentColor = Color.White.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(stringResource(R.string.activate))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 许可信息框
                LicenseInfoRow(
                    icon = Icons.Default.Timer,
                    label = "生效日期",
                    value = LicenseDataStore.formatDate(savedStartDate) ?: "未设置"
                )
                LicenseInfoRow(
                    icon = Icons.Default.Timer,
                    label = "过期日期",
                    value = LicenseDataStore.formatDate(savedEndDate) ?: "未设置"
                )
                LicenseInfoRow(
                    icon = Icons.Default.Lock,
                    label = "许可类型",
                    value = licenseEdition
                )
                LicenseInfoRow(
                    icon = Icons.Default.VerifiedUser,
                    label = "注册用户",
                    value = licensedTo
                )
                LicenseInfoRow(
                    icon = Icons.Default.Email,
                    label = "用户邮箱",
                    value = "user@example.com" // TODO: 从实际数据源获取邮箱
                )
                LicenseInfoRow(
                    icon = Icons.Default.Devices,
                    label = "设备ID",
                    value = deviceId.take(8) + "..." // 显示设备ID的前8位
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseSettingsScreen(
    navController: NavController,
    onLicenseActivated: () -> Unit = {}
) {
    // 保留原始屏幕版本以支持直接导航（如果需要的话）
    LicenseSettingsDialogContent(
        onClose = { navController.popBackStack() },
        onLicenseActivated = onLicenseActivated
    )
}