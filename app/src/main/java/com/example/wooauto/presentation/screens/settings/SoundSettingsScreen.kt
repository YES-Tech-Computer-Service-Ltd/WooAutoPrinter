package com.example.wooauto.presentation.screens.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.wooauto.R
import com.example.wooauto.domain.models.SoundSettings
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundSettingsScreen(
    viewModel: SoundSettingsViewModel = hiltViewModel(),
    navController: NavController
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    
    // 获取当前音量和声音类型
    val volume by viewModel.notificationVolume.collectAsState()
    val soundType by viewModel.soundType.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val customSoundUri by viewModel.customSoundUri.collectAsState()
    
    val scrollState = rememberScrollState()
    
    // 在Composable函数中提前获取字符串资源
    val savedMessage = stringResource(id = R.string.sound_settings_saved)
    
    // 文件选择器
    val audioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // 复制文件到应用内部存储
                try {
                    // 获取文件名
                    val fileName = getFileNameFromUri(context, uri) ?: "custom_sound.mp3"
                    
                    // 在应用内部存储创建目录
                    val soundDir = File(context.filesDir, "sounds")
                    if (!soundDir.exists()) {
                        soundDir.mkdirs()
                    }
                    
                    // 目标文件
                    val destinationFile = File(soundDir, fileName)
                    
                    // 复制文件
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destinationFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    // 保存文件路径而不是URI形式
                    val internalPath = destinationFile.absolutePath
                    
                    // 保存路径
                    coroutineScope.launch {
                        viewModel.setCustomSoundUri(internalPath)
                        
                        // 如果当前选中的不是自定义声音类型，自动切换
                        if (soundType != SoundSettings.SOUND_TYPE_CUSTOM) {
                            viewModel.setSoundType(SoundSettings.SOUND_TYPE_CUSTOM)
                        }
                        
                        // 提示用户
                        snackbarHostState.showSnackbar("音频文件设置成功")
                    }
                } catch (e: Exception) {
                    // 提示用户
                    Toast.makeText(
                        context,
                        "设置音频文件失败: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    // 打开音频文件选择器
    @RequiresApi(Build.VERSION_CODES.O)
    fun openAudioFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            
            // 对于API 19及以上，可以指定MIME类型
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        }
        
        audioFilePicker.launch(intent)
    }
    
    Scaffold(
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
                .padding(
                    top = 0.dp,
                    bottom = padding.calculateBottomPadding(),
                    start = 0.dp,
                    end = 0.dp
                )
        ) {
            // 外层Column，包含统一的水平内边距
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 0.dp)
            ) {
                // 增加顶部间距
                Spacer(modifier = Modifier.height(16.dp))
                
                // 顶部标题行，使用与其他页面一致的样式
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 返回按钮
                    IconButton(
                        onClick = { navController.navigateUp() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // 标题
                    Text(
                        text = stringResource(id = R.string.sound_settings),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // 内容区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(vertical = 8.dp)
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
                        customSoundUri = customSoundUri,
                        onTypeSelected = { 
                            coroutineScope.launch {
                                viewModel.setSoundType(it)
                            }
                        },
                        onSelectCustomSound = {
                            openAudioFilePicker()
                        },
                        enabled = soundEnabled
                    )
                }
            }
        }
    }
}

// 获取URI对应的文件名
private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
    var fileName: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (displayNameIndex != -1) {
                fileName = cursor.getString(displayNameIndex)
            }
        }
    }
    return fileName
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
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
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
    customSoundUri: String,
    onTypeSelected: (String) -> Unit,
    onSelectCustomSound: () -> Unit,
    enabled: Boolean
) {
    // 声音类型列表和对应的字符串资源ID映射
    val soundTypeResources = mapOf(
        SoundSettings.SOUND_TYPE_DEFAULT to R.string.sound_type_default,
        SoundSettings.SOUND_TYPE_ALARM to R.string.sound_type_alarm,
        SoundSettings.SOUND_TYPE_RINGTONE to R.string.sound_type_ringtone,
        SoundSettings.SOUND_TYPE_EVENT to R.string.sound_type_event,
        SoundSettings.SOUND_TYPE_EMAIL to R.string.sound_type_email,
        SoundSettings.SOUND_TYPE_CUSTOM to R.string.sound_type_custom
    )
    
    // 所有声音类型平铺展示
    val allSoundTypes = SoundSettings.getAllSoundTypes()
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        allSoundTypes.forEach { type ->
            // 使用安全获取资源ID，如果不存在则使用默认通知音类型
            val displayTextResId = soundTypeResources[type] ?: R.string.sound_type_default
            
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
                
                Icon(
                    imageVector = when (type) {
                        SoundSettings.SOUND_TYPE_CUSTOM -> Icons.Default.MusicNote
                        else -> Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(start = 8.dp, end = 16.dp)
                        .size(24.dp)
                )
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Text(
                        text = stringResource(id = displayTextResId),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    
                    // 如果是自定义声音类型，显示已选择的文件名
                    if (type == SoundSettings.SOUND_TYPE_CUSTOM) {
                        val fileName = if (customSoundUri.isNotEmpty()) {
                            // 从绝对路径中提取文件名
                            val file = File(customSoundUri)
                            file.name
                        } else {
                            "未选择音频文件"
                        }
                        
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // 对于自定义声音类型，添加选择文件按钮
                if (type == SoundSettings.SOUND_TYPE_CUSTOM) {
                    IconButton(
                        onClick = onSelectCustomSound,
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = "选择音频文件",
                            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                }
                
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