package com.example.wooauto.presentation.screens.products

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wooauto.R
import com.example.wooauto.domain.models.Product

/**
 * 产品详情对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailDialog(
    product: Product,
    onDismiss: () -> Unit
) {
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
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // 顶部栏
                TopAppBar(
                    title = {
                        Text(
                            text = product.name,
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
                
                // 内容区（可滚动）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                ) {
                    // 产品图片
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (product.images.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(product.images.first().src)
                                    .crossfade(true)
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
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 产品基本信息
                    ProductDetailRow(
                        label = "产品ID",
                        value = product.sku.ifEmpty { product.id.toString() },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    
                    ProductDetailRow(
                        label = "价格",
                        value = "¥${product.price}",
                        icon = {
                            Icon(
                                imageVector = Icons.Default.AttachMoney,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    
                    if (product.regularPrice.isNotEmpty() && product.regularPrice != product.price) {
                        ProductDetailRow(
                            label = "原价",
                            value = "¥${product.regularPrice}",
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.AttachMoney,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                    
                    if (product.salePrice.isNotEmpty() && product.salePrice != product.price) {
                        ProductDetailRow(
                            label = "特价",
                            value = "¥${product.salePrice}",
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.LocalOffer,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                    
                    ProductDetailRow(
                        label = "库存状态",
                        value = when(product.stockStatus) {
                            "instock" -> "有库存"
                            "outofstock" -> "无库存"
                            "onbackorder" -> "可预订"
                            else -> product.stockStatus
                        },
                        icon = {
                            Icon(
                                imageVector = when(product.stockStatus) {
                                    "instock" -> Icons.Default.CheckCircle
                                    "outofstock" -> Icons.Default.Error
                                    "onbackorder" -> Icons.Default.Schedule
                                    else -> Icons.Default.Info
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = when(product.stockStatus) {
                                    "instock" -> Color(0xFF4CAF50)
                                    "outofstock" -> Color(0xFFE53935)
                                    "onbackorder" -> Color(0xFFFFA000)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    )
                    
                    if (product.stockQuantity != null) {
                        ProductDetailRow(
                            label = "库存数量",
                            value = product.stockQuantity.toString(),
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Inventory,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                    
                    // 产品描述
                    if (product.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "产品描述",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = product.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    // 产品分类
                    if (product.categories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "产品分类",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        product.categories.forEach { category ->
                            Text(
                                text = "• ${category.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                    
                    // 产品属性
                    if (product.attributes?.isNotEmpty() == true) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "产品属性",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        product.attributes?.forEach { attribute ->
                            Text(
                                text = "• ${attribute.name}: ${attribute.options.joinToString(", ")}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
                
                // 底部按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("关闭")
                    }
                }
            }
        }
    }
}

/**
 * 产品详情行组件
 */
@Composable
fun ProductDetailRow(
    label: String,
    value: String,
    icon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(100.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(4.dp))
        }
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
} 