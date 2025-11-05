package com.example.wooauto.presentation.screens.products

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import coil.decode.BitmapFactoryDecoder
import android.graphics.Bitmap
import com.example.wooauto.R
import com.example.wooauto.domain.models.Product
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.livedata.observeAsState
import com.example.wooauto.licensing.LicenseStatus
import com.example.wooauto.licensing.EligibilityStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailDialog(
    product: Product,
    onDismiss: () -> Unit,
    onUpdate: (Product) -> Unit,
    viewModel: ProductsViewModel = hiltViewModel()
) {
    remember { viewModel.licenseManager }
    val licenseInfo by viewModel.licenseManager.licenseInfo.observeAsState()
    val eligibilityInfo by viewModel.licenseManager.eligibilityInfo.observeAsState()
    val hasEligibility = eligibilityInfo?.status == EligibilityStatus.ELIGIBLE
    
    var regularPrice by remember { mutableStateOf(product.regularPrice) }
    var stockStatus by remember { mutableStateOf(product.stockStatus) }
    var stockStatusExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 顶部栏
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.product_details),
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.close)
                            )
                        }
                    }
                )
                
                // 内容区域（可滚动）
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 产品图片
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (product.images.isNotEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(product.images.first().src)
                                        .crossfade(true)
                                        .size(160, 160)
                                        .allowHardware(false)
                                        .bitmapConfig(Bitmap.Config.RGB_565)
                                        .memoryCachePolicy(CachePolicy.ENABLED)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .decoderFactory(BitmapFactoryDecoder.Factory())
                                        .build(),
                                    contentDescription = product.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(id = R.drawable.ic_launcher_foreground),
                                    placeholder = painterResource(id = R.drawable.ic_launcher_foreground)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color = MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restaurant,
                                        contentDescription = product.name,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 产品ID
                        Text(
                            text = stringResource(id = R.string.product_id_label, product.id),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 产品名称
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // 价格输入
                        OutlinedTextField(
                            value = regularPrice,
                            onValueChange = { regularPrice = it },
                            label = { Text(stringResource(id = R.string.regular_price)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = hasEligibility
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 库存状态选择
                        ExposedDropdownMenuBox(
                            expanded = stockStatusExpanded,
                            onExpandedChange = { expanded ->
                                if (hasEligibility) {
                                    stockStatusExpanded = expanded
                                }
                            }
                        ) {
                            OutlinedTextField(
                                value = if (stockStatus == "instock") 
                                    stringResource(id = R.string.stock_status_in_stock)
                                else 
                                    stringResource(id = R.string.stock_status_out_of_stock),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(id = R.string.status)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stockStatusExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                enabled = hasEligibility
                            )
                            
                            ExposedDropdownMenu(
                                expanded = stockStatusExpanded && hasEligibility,
                                onDismissRequest = { stockStatusExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = R.string.stock_status_in_stock)) },
                                    onClick = {
                                        if (hasEligibility) {
                                            stockStatus = "instock"
                                            stockStatusExpanded = false
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = R.string.stock_status_out_of_stock)) },
                                    onClick = {
                                        if (hasEligibility) {
                                            stockStatus = "outofstock"
                                            stockStatusExpanded = false
                                        }
                                    }
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 产品附加信息
                        if (product.sku.isNotEmpty()) {
                            DetailItem(label = stringResource(id = R.string.sku), value = product.sku)
                        }
                        
                        if (product.categories.isNotEmpty()) {
                            DetailItem(
                                label = stringResource(id = R.string.categories), 
                                value = product.categories.joinToString(", ") { it.name }
                            )
                        }
                    }
                    
                    // 如果证书无效，显示模糊层和提示
                    if (!hasEligibility) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.9f),
                                            Color.White.copy(alpha = 0.95f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.license_required),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.license_required_message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                // 使用Row放置两个按钮
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                                ) {
                                    // 激活许可证按钮
                                    Button(
                                        onClick = {
                                            onDismiss() // 先关闭当前对话框
                                            // 导航到许可证设置页面
                                            viewModel.navigateToLicenseSettings()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VpnKey,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(text = stringResource(R.string.activate_license))
                                    }
                                    
                                    // 关闭按钮
                                    OutlinedButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(text = stringResource(R.string.close))
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 按钮区域（固定在底部）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text(stringResource(id = R.string.cancel))
                    }
                    
                    Button(
                        onClick = {
                            // 添加日志记录更新的产品信息
                            Log.d("ProductDetailDialog", "更新产品：ID=${product.id}, 原价=${regularPrice}, 库存状态=${stockStatus}")
                            onUpdate(
                                product.copy(
                                    regularPrice = regularPrice,
                                    stockStatus = stockStatus
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = hasEligibility
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(id = R.string.save))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
                    Text(
                text = stringResource(id = R.string.product_label_colon, label),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.width(100.dp)
            )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
