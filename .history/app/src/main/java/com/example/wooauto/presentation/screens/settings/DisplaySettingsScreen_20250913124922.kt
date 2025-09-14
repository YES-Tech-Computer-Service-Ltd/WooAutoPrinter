package com.example.wooauto.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.wooauto.R
import com.example.wooauto.presentation.components.SettingsSubPageScaffold
import kotlinx.coroutines.launch
import android.provider.Settings
import android.content.Intent
import android.net.Uri

@Composable
fun DisplaySettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()

    // 读取系统亮度（0..255），转为 0.05..1.0
    fun readSystemBrightnessNormalized(): Float {
        return try {
            val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            ((raw / 255f).coerceIn(0.05f, 1f))
        } catch (e: Exception) {
            0.5f
        }
    }

    // 写系统亮度；若无权限则跳到授权页
    fun writeSystemBrightnessFromNormalized(normalized: Float) {
        val clamped = normalized.coerceIn(0.05f, 1f)
        if (Settings.System.canWrite(context)) {
            // 切到手动亮度模式
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            // 写入亮度值（0..255）
            val value = (clamped * 255f).toInt().coerceIn(1, 255)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value
            )
        } else {
            // 跳转到系统设置授权页
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    // 亮度 5% - 100%，以系统当前值初始化
    var brightness by remember { mutableFloatStateOf(readSystemBrightnessNormalized()) }

    SettingsSubPageScaffold() {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部说明
            Text(
                text = stringResource(id = R.string.display_setting_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 常亮开关
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(id = R.string.keep_screen_on_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(id = R.string.keep_screen_on_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = { enabled -> viewModel.updateKeepScreenOn(enabled) }
                    )
                }
            }

            // 亮度卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(id = R.string.screen_brightness), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = "${(brightness * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrightnessLow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        // 主 Slider
                        Slider(
                            value = brightness,
                            onValueChange = { v ->
                                brightness = v.coerceIn(0.05f, 1f)
                            },
                            onValueChangeFinished = {
                                writeSystemBrightnessFromNormalized(brightness)
                                // 重新同步一次，避免外部模式干扰
                                brightness = readSystemBrightnessNormalized()
                            },
                            modifier = Modifier.weight(1f),
                        )

                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.BrightnessHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 移除示例预览条，系统亮度会直接生效，用户可直观看到效果
                }
            }
        }
    }
}


