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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.livedata.observeAsState

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
fun LicenseSettingsScreen(
    navController: NavController,
    onLicenseActivated: () -> Unit = {},
    onGoToAppClicked: () -> Unit = {}
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
    
    // 移除阻塞性等待，使用默认状态确保UI立即可用
    // 如果eligibilityInfo为空，使用默认的允许状态
    val safeEligibilityInfo = eligibilityInfo ?: EligibilityInfo(
        status = EligibilityStatus.ELIGIBLE,
        isTrialActive = true,
        trialDaysRemaining = 10,
        displayMessage = "正在加载权限状态...",
        source = EligibilitySource.TRIAL
    )
    
    Log.d("LicenseSettingsScreen", "🎨 渲染UI - 资格状态: ${safeEligibilityInfo.status}, 试用天数: ${safeEligibilityInfo.trialDaysRemaining}")

    // 基于统一的资格状态显示逻辑
    val (licenseStatusText, statusIcon, statusBackgroundColor) = when (safeEligibilityInfo.status) {
        EligibilityStatus.ELIGIBLE -> {
            if (safeEligibilityInfo.isTrialActive) {
                Triple(
                    "Trial Mode - ${safeEligibilityInfo.trialDaysRemaining} days remaining",
                    Icons.Default.Timer,
                    Color(0xFFE0F7E0)
                )
            } else {
                Triple(
                    "License is valid",
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.license_settings)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

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
                        tint = if (safeEligibilityInfo.isTrialActive) Color.Red else Color.Green
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = licenseStatusText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = safeEligibilityInfo.displayMessage,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 输入框和激活按钮
            if (safeEligibilityInfo.isTrialActive) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    LicenseInputSection(
                        onLicenseComplete = { finalKey ->
                            licenseCode = finalKey
                        },
                        isEditable = true,
                        savedLicenseKey = licenseCode
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
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
                                                Log.d("LicenseSettingsScreen", "试用期已结束")
                                                
                                                // 重新验证许可证状态，更新LicenseManager的状态
                                                val isValid = licenseManager.forceRevalidateAndSync(context)
                                                Log.d("LicenseSettingsScreen", "许可证激活后统一验证结果: $isValid")
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
                                    Log.e("LicenseSettingsScreen", "Error during activation: ${e.message}", e)
                                    snackbarHostState.showSnackbar("Error: ${e.message}")
                                }
                            }
                        },
                        modifier = Modifier
                            .width(120.dp)
                            .height(48.dp),
                        enabled = true,
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
                        isEditable = true,
                        savedLicenseKey = licenseCode
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
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
                                                Log.d("LicenseSettingsScreen", "试用期已结束")
                                                
                                                // 重新验证许可证状态，更新LicenseManager的状态
                                                val isValid = licenseManager.forceRevalidateAndSync(context)
                                                Log.d("LicenseSettingsScreen", "许可证激活后统一验证结果: $isValid")
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
                                    Log.e("LicenseSettingsScreen", "Error during activation: ${e.message}", e)
                                    snackbarHostState.showSnackbar("Error: ${e.message}")
                                }
                            }
                        },
                        modifier = Modifier
                            .width(120.dp)
                            .height(48.dp),
                        enabled = true,
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
                label = "Expiration Date",
                value = LicenseDataStore.formatDate(savedEndDate) ?: "Not set"
            )
            LicenseInfoRow(
                icon = Icons.Default.ThumbUp,
                label = "Valid For Version",
                value = "All"
            )
            LicenseInfoRow(
                icon = Icons.Default.Lock,
                label = "License Edition",
                value = licenseEdition
            )
            LicenseInfoRow(
                icon = Icons.Default.Science,
                label = "Capabilities",
                value = capabilities
            )
            LicenseInfoRow(
                icon = Icons.Default.Science,
                label = "Trial",
                value = if (safeEligibilityInfo.isTrialActive) "Yes" else "No"
            )
            LicenseInfoRow(
                icon = Icons.Default.VerifiedUser,
                label = "Licensed To",
                value = licensedTo
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 底部
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { /* 空链接，未来填充 */ }) {
                    Text(
                        text = "End User License Agreement",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                    onClick = onGoToAppClicked,
                    modifier = Modifier
                        .width(120.dp)
                        .height(48.dp)
                ) {
                    Text("Go to App")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}