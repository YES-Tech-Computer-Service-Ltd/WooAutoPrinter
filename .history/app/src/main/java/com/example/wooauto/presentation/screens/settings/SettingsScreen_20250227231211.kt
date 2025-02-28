package com.example.wooauto.presentation.screens.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.wooauto.R
import com.example.wooauto.presentation.theme.WooAutoTheme
import android.util.Log
import android.content.res.Configuration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.LayoutDirection.LTR
import androidx.compose.ui.unit.LayoutDirection.RTL
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal
import androidx.compose.ui.unit.LayoutDirection.Unspecified
import androidx.compose.ui.unit.LayoutDirection.Vertical
import androidx.compose.ui.unit.LayoutDirection.Horizontal

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navController: NavController = rememberNavController()
) {
    val scrollState = rememberScrollState()
    
    // 当前选择的语言
    var selectedLanguage by remember { mutableStateOf("中文") }
    
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
        SettingsCategoryCard {
            SettingsNavigationItem(
                title = stringResource(R.string.website_setup),
                icon = Icons.Filled.Cloud,
                onClick = {
                    Log.d("设置导航", "点击了网站设置项")
                    // 这里应该导航到网站设置详情页面
                    // 目前先显示WebsiteSettingsScreen直接在当前页面
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            
            // 声音设置
            SettingsNavigationItem(
                title = stringResource(R.string.sound_setup),
                icon = Icons.Filled.VolumeUp,
                onClick = {
                    Log.d("设置导航", "点击了声音设置项")
                    // 导航到声音设置详情页面
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            
            // 打印设置
            SettingsNavigationItem(
                title = stringResource(R.string.printer_setup),
                icon = Icons.Filled.Print,
                onClick = {
                    Log.d("设置导航", "点击了打印设置项")
                    // 导航到打印设置详情页面
                }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 应用程序设置
        SettingsCategoryCard {
            // 语言设置
            SettingsNavigationItem(
                title = stringResource(R.string.language),
                icon = Icons.Filled.Language,
                description = selectedLanguage,
                onClick = {
                    Log.d("设置导航", "点击了语言设置项")
                    // 这里应该打开一个对话框让用户选择语言
                    selectedLanguage = if (selectedLanguage == "中文") "English" else "中文"
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            
            // 自动更新开关
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
        SettingsCategoryCard {
            SettingsNavigationItem(
                title = stringResource(R.string.about),
                icon = Icons.Outlined.Info,
                onClick = {
                    Log.d("设置导航", "点击了关于项")
                    // 导航到关于页面
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            
            SettingsItem(
                title = stringResource(R.string.version),
                subtitle = "1.0.0"
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SettingsCategoryCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SettingsNavigationItem(
    title: String,
    icon: ImageVector,
    description: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
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
            modifier = Modifier.weight(1f)
        )
        
        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Icon(
            imageVector = Icons.Filled.ArrowForwardIos,
            contentDescription = "前往",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
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
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp)
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
            .padding(vertical = 12.dp, horizontal = 16.dp),
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