package com.wooauto.presentation.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.wooauto.R
import com.wooauto.presentation.theme.WooAutoTheme
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    
    // 跟踪当前展开的设置类别
    var expandedCategory by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 网站连接设置
        ExpandableSettingsCategoryCard(
            title = stringResource(R.string.website_setup),
            icon = Icons.Filled.Cloud,
            isExpanded = expandedCategory == "website",
            onExpandToggle = { 
                expandedCategory = if (expandedCategory == "website") null else "website" 
            }
        ) {
            WebsiteSettingsScreen(viewModel)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 声音设置
        ExpandableSettingsCategoryCard(
            title = stringResource(R.string.sound_setup),
            icon = Icons.Filled.VolumeUp,
            isExpanded = expandedCategory == "sound",
            onExpandToggle = { 
                expandedCategory = if (expandedCategory == "sound") null else "sound" 
            }
        ) {
            var useTTS by remember { mutableStateOf(true) }
            
            SettingsToggleItem(
                title = "使用文字转语音",
                subtitle = "使用自定义文字播放提醒声音",
                checked = useTTS,
                onCheckedChange = { useTTS = it }
            )
            
            if (useTTS) {
                SettingsItem(title = "提醒文字", subtitle = "新订单提醒时播放的文字")
            } else {
                SettingsItem(title = "选择声音文件", subtitle = "选择提醒音效文件")
            }
            
            SettingsItem(title = "音量设置", subtitle = "调整提醒音量")
            SettingsItem(title = "播放次数", subtitle = "设置提醒声音播放次数")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 打印设置
        ExpandableSettingsCategoryCard(
            title = stringResource(R.string.printer_setup),
            icon = Icons.Filled.Print,
            isExpanded = expandedCategory == "printer",
            onExpandToggle = { 
                expandedCategory = if (expandedCategory == "printer") null else "printer" 
            }
        ) {
            SettingsItem(title = "添加打印机", subtitle = "配置蓝牙或网络打印机")
            SettingsItem(title = "打印机列表", subtitle = "管理已配置的打印机")
            SettingsItem(title = "打印模板", subtitle = "选择订单打印模板")
            SettingsItem(title = "测试打印", subtitle = "发送测试页面到打印机")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 程序设置
        ExpandableSettingsCategoryCard(
            title = stringResource(R.string.app_settings),
            icon = Icons.Outlined.Settings,
            isExpanded = expandedCategory == "app",
            onExpandToggle = { 
                expandedCategory = if (expandedCategory == "app") null else "app" 
            }
        ) {
            SettingsItem(title = "语言", subtitle = "选择应用显示语言")
            SettingsItem(title = "主题", subtitle = "选择应用主题")
            SettingsItem(title = "自动启动", subtitle = "设置系统启动时自动运行应用")
            
            var autoUpdate by remember { mutableStateOf(true) }
            SettingsToggleItem(
                title = "自动更新",
                subtitle = "有新版本时自动更新",
                checked = autoUpdate,
                onCheckedChange = { autoUpdate = it }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 关于
        ExpandableSettingsCategoryCard(
            title = stringResource(R.string.about),
            icon = Icons.Outlined.Info,
            isExpanded = expandedCategory == "about",
            onExpandToggle = { 
                expandedCategory = if (expandedCategory == "about") null else "about" 
            }
        ) {
            SettingsItem(title = stringResource(R.string.version), subtitle = "1.0.0")
            SettingsItem(title = "开发者", subtitle = "WooAuto团队")
            SettingsItem(title = "反馈", subtitle = "发送反馈或报告问题")
            SettingsItem(title = "隐私政策", subtitle = "查看应用隐私政策")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun ExpandableSettingsCategoryCard(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                
                Switch(
                    checked = isExpanded,
                    onCheckedChange = { onExpandToggle() }
                )
            }
            
            HorizontalDivider()
            
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    WooAutoTheme {
        SettingsScreen()
    }
} 