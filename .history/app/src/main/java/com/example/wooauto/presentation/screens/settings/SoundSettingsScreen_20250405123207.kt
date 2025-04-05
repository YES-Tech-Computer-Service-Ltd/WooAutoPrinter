package com.example.wooauto.presentation.screens.settings

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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.R
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
    
    // 在Composable函数中提前获取字符串资源
    val savedMessage = stringResource(id = R.string.sound_settings_saved)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.sound_settings)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(id = R.string.back))
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.saveSettings()
                            snackbarHostState.showSnackbar(savedMessage)
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
                    Text(stringResource(id = R.string.save_settings))
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
                text = stringResource(id = R.string.notification_volume),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(id = R.string.notification_volume_desc),
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
                text = stringResource(id = R.string.sound_type_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(id = R.string.sound_type_desc),
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
                enabled = soundEnabled
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
                text = stringResource(id = R.string.enable_sound),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(id = R.string.enable_sound_desc),
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
                text = stringResource(id = R.string.volume_min),
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "$value%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Text(
                text = stringResource(id = R.string.volume_max),
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
    enabled: Boolean
) {
    // 声音类型列表和对应的字符串资源ID映射
    val soundTypeResources = mapOf(
        SoundSettings.SOUND_TYPE_DEFAULT to R.string.sound_type_default,
        SoundSettings.SOUND_TYPE_ALARM to R.string.sound_type_alarm,
        SoundSettings.SOUND_TYPE_RINGTONE to R.string.sound_type_ringtone,
        SoundSettings.SOUND_TYPE_EVENT to R.string.sound_type_event,
        SoundSettings.SOUND_TYPE_EMAIL to R.string.sound_type_email
    )
    
    // 声音类型分组
    val standardSoundTypes = listOf(
        SoundSettings.SOUND_TYPE_DEFAULT,
        SoundSettings.SOUND_TYPE_EVENT,
        SoundSettings.SOUND_TYPE_EMAIL
    )
    
    val loudSoundTypes = listOf(
        SoundSettings.SOUND_TYPE_ALARM,
        SoundSettings.SOUND_TYPE_RINGTONE
    )
    
    // 显示的声音类型分类
    val soundTypeGroups = listOf(
        "标准系统声音" to standardSoundTypes,
        "响亮系统声音" to loudSoundTypes
    )
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        soundTypeGroups.forEach { (group, types) ->
            Text(
                text = group,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(id = R.string.sound_type_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            types.forEach { type ->
                // 使用安全获取资源ID，如果不存在则使用默认unknown类型
                val displayTextResId = soundTypeResources[type] ?: R.string.sound_type_unknown
                
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
                        text = stringResource(id = displayTextResId),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .weight(1f),
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    
                    if (isSelected && enabled) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
} 