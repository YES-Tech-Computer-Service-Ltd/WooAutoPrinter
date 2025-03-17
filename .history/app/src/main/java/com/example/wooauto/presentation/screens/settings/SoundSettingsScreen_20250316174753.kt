package com.example.wooauto.presentation.screens.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.domain.models.SoundSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundSettingsScreen(
    viewModel: SoundSettingsViewModel = hiltViewModel(),
    navController: NavController
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 获取当前音量和声音类型
    val volume by viewModel.notificationVolume.collectAsState()
    val soundType by viewModel.soundType.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    
    val scrollState = rememberScrollState()
    
    // 是否显示声音文件提示对话框
    val showSoundFileDialog = remember { mutableStateOf(false) }
    
    // 检查是否有声音文件加载问题
    LaunchedEffect(Unit) {
        val soundManager = viewModel.getSoundManager()
        soundManager?.initialized?.collect { initialized ->
            if (initialized) {
                // 如果声音管理器初始化完成，但没有成功加载任何声音，显示提示
                if (soundManager.isFallbackMode()) {
                    showSoundFileDialog.value = true
                }
            }
        }
    }
    
    // 声音文件提示对话框
    if (showSoundFileDialog.value) {
        AlertDialog(
            onDismissRequest = { showSoundFileDialog.value = false },
            title = { Text("声音文件缺失") },
            text = { 
                Column {
                    Text("应用未找到有效的声音文件，将使用系统默认通知音效。")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("请在assets/sounds目录中添加以下MP3文件：")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("- notification_default.mp3")
                    Text("- notification_bell.mp3")
                    Text("- notification_cash.mp3")
                    Text("- notification_alert.mp3")
                    Text("- notification_chime.mp3")
                }
            },
            confirmButton = { 
                TextButton(onClick = { showSoundFileDialog.value = false }) {
                    Text("确定")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("声音设置") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.saveSettings()
                            snackbarHostState.showSnackbar("声音设置已保存")
                        }
                        navController.navigateUp()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("保存设置")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // 声音开关
            SoundEnabledSwitch(
                enabled = soundEnabled,
                onEnabledChange = { 
                    coroutineScope.launch {
                        viewModel.setSoundEnabled(it)
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 音量调节部分
            Text(
                text = "通知音量",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "调整提示音量大小，不影响系统音量",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            VolumeSlider(
                value = volume,
                onValueChange = { 
                    coroutineScope.launch {
                        viewModel.setVolume(it)
                    }
                },
                enabled = soundEnabled
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 声音类型选择部分
            Text(
                text = "提示音类型",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "选择新订单提示音",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SoundTypeSelector(
                selectedType = soundType,
                onTypeSelected = { 
                    coroutineScope.launch {
                        viewModel.setSoundType(it)
                    }
                },
                enabled = soundEnabled,
                viewModel = viewModel
            )
            
            Spacer(modifier = Modifier.height(16.dp)) // 为底部按钮留出空间
        }
    }
}

@Composable
fun SoundEnabledSwitch(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.VolumeUp,
            contentDescription = null,
            modifier = Modifier.padding(end = 16.dp)
        )
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "启用声音",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "控制是否播放新订单提示音",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun VolumeSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0%",
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "${value}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Text(
                text = "100%",
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            steps = 0,
            enabled = enabled,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
fun SoundTypeSelector(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    enabled: Boolean,
    viewModel: SoundSettingsViewModel = hiltViewModel()
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SoundSettings.getAllSoundTypes().forEach { type ->
            val displayName = SoundSettings.getSoundTypeDisplayName(type)
            val isSelected = type == selectedType
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected && enabled) 
                            MaterialTheme.colorScheme.primaryContainer
                        else 
                            Color.Transparent
                    )
                    .selectable(
                        selected = isSelected,
                        onClick = { onTypeSelected(type) },
                        enabled = enabled
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { onTypeSelected(type) },
                    enabled = enabled
                )
                
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .weight(1f),
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                
                if (enabled) {
                    IconButton(
                        onClick = { 
                            if (isSelected) {
                                viewModel.playTestSound()
                            } else {
                                onTypeSelected(type)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "播放测试声音",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
} 