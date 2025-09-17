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
import android.app.Activity

@Composable
fun DisplaySettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()

    val window = (context as? Activity)?.window
    val persistedPercent by viewModel.appBrightnessPercent.collectAsState()

    // 亮度 5% - 100%，以窗口或系统当前值初始化（只读系统值，无需授权）
    var brightness by remember {
        mutableFloatStateOf(
            run {
                val persisted = persistedPercent
                if (persisted != null) persisted / 100f
                else {
                    val w = window?.attributes?.screenBrightness ?: -1f
                    if (w in 0f..1f) w.coerceIn(0.05f, 1f) else try {
                        val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                        (raw / 255f).coerceIn(0.05f, 1f)
                    } catch (_: Exception) {
                        0.5f
                    }
                }
            }
        )
    }

    // 只在初始化时应用持久化值到窗口
    LaunchedEffect(Unit) {
        val p = persistedPercent
        if (p != null) {
            val v = (p / 100f).coerceIn(0.05f, 1f)
            window?.attributes = window?.attributes?.apply { screenBrightness = v }
        }
    }

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
                                val clamped = v.coerceIn(0.05f, 1f)
                                brightness = clamped
                                window?.attributes = window?.attributes?.apply { screenBrightness = clamped }
                                // 写入持久化（四舍五入到百分比）
                                viewModel.updateAppBrightnessPercent((clamped * 100f).toInt())
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


