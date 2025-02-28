package com.example.wooauto.presentation.screens.settings

import android.util.Log
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
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navController: NavController = rememberNavController()
) {
    val scrollState = rememberScrollState()
    
    // 获取当前语言
    val currentLocale by viewModel.currentLocale.collectAsState()
    
    // 自动更新状态
    var autoUpdate by remember { mutableStateOf(false) }
    
    // 显示语言选择对话框的状态
    var showLanguageDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 网站连接设置
        SettingsCategoryCard {
            SettingsNavigationItem(
                title = "网站设置",
                icon = Icons.Filled.Cloud,
                onClick = {
                    Log.d("设置导航", "点击了网站设置项")
                    // 这里应该导航到网站设置详情页面
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            
            // 声音设置
            SettingsNavigationItem(
                title = "声音设置",
                icon = Icons.Filled.VolumeUp,
                onClick = {
                    Log.d("设置导航", "点击了声音设置项")
                    // 导航到声音设置详情页面
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            
            // 打印设置
            SettingsNavigationItem(
                title = "打印设置",
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
                icon = Icons.Outlined.Language,
                title = "语言设置",
                onClick = {
                    Log.d("设置", "点击了语言设置")
                    showLanguageDialog = true
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            
            // 关于
            SettingsNavigationItem(
                icon = Icons.Outlined.Info,
                title = "关于",
                onClick = {
                    Log.d("设置", "点击了关于")
                }
            )
        }
        
        // 自动更新
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "自动更新",
                style = MaterialTheme.typography.bodyLarge
            )
            
            Switch(
                checked = autoUpdate,
                onCheckedChange = { autoUpdate = it }
            )
        }
    }
    
    // 语言选择对话框
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("选择语言") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setAppLanguage(Locale.ENGLISH)
                                showLanguageDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "English",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (currentLocale == Locale.ENGLISH) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "已选择",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    Divider()
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setAppLanguage(Locale.SIMPLIFIED_CHINESE)
                                showLanguageDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "简体中文",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (currentLocale == Locale.SIMPLIFIED_CHINESE) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "已选择",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("取消")
                }
            }
        )
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
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SettingsNavigationItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        
        Icon(
            imageVector = Icons.Filled.ArrowForwardIos,
            contentDescription = "箭头",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
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