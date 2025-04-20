package com.example.wooauto.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.wooauto.R

/**
 * 通用API配置表单组件
 * 在设置页面和API配置对话框中使用相同的UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConfigForm(
    siteUrl: String,
    consumerKey: String,
    consumerSecret: String,
    pollingInterval: String,
    useWooCommerceFood: Boolean,
    onSiteUrlChange: (String) -> Unit,
    onConsumerKeyChange: (String) -> Unit,
    onConsumerSecretChange: (String) -> Unit,
    onPollingIntervalChange: (String) -> Unit,
    onUseWooCommerceFoodChange: (Boolean) -> Unit,
    onQrCodeScan: () -> Unit = {},
    isTestingConnection: Boolean = false,
    showQrScanner: Boolean = true,
    showPluginOption: Boolean = true
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 站点URL输入框
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = siteUrl,
                onValueChange = onSiteUrlChange,
                label = { Text(stringResource(R.string.website_url)) },
                placeholder = { Text(stringResource(R.string.website_url_placeholder)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                isError = siteUrl.isNotEmpty() && !siteUrl.contains("http")
            )
            
            if (showQrScanner) {
                IconButton(onClick = onQrCodeScan) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = stringResource(R.string.scan_qr_code)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Consumer Key输入框
        OutlinedTextField(
            value = consumerKey,
            onValueChange = onConsumerKeyChange,
            label = { Text(stringResource(R.string.api_key)) },
            placeholder = { Text(stringResource(R.string.api_key_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = consumerKey.isNotEmpty() && consumerKey.contains("http"),
            colors = OutlinedTextFieldDefaults.colors(
                errorBorderColor = MaterialTheme.colorScheme.error,
                errorLabelColor = MaterialTheme.colorScheme.error
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Consumer Secret输入框
        OutlinedTextField(
            value = consumerSecret,
            onValueChange = onConsumerSecretChange,
            label = { Text(stringResource(R.string.api_secret)) },
            placeholder = { Text(stringResource(R.string.api_secret_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = consumerSecret.isNotEmpty() && consumerSecret.contains("http"),
            colors = OutlinedTextFieldDefaults.colors(
                errorBorderColor = MaterialTheme.colorScheme.error,
                errorLabelColor = MaterialTheme.colorScheme.error
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 轮询间隔输入框
        OutlinedTextField(
            value = pollingInterval,
            onValueChange = { 
                // 确保只输入数字
                if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                    onPollingIntervalChange(it)
                }
            },
            label = { Text(stringResource(R.string.polling_interval)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardType = KeyboardType.Number
        )
        
        if (showPluginOption) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // WooCommerce Food插件选择
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.plugin_woocommerce_food),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Switch(
                    checked = useWooCommerceFood,
                    onCheckedChange = onUseWooCommerceFoodChange
                )
            }
        }
        
        // 显示连接测试状态
        if (isTestingConnection) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.testing_connection))
            }
        }
    }
} 