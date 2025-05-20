package com.example.wooauto.presentation.screens.products

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wooauto.R
import com.example.wooauto.domain.models.Product

/**
 * 产品详情对话框
 * 
 * @param product 要显示的产品
 * @param onDismiss 关闭对话框的回调
 * @param onUpdate 更新产品的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailDialog(
    product: Product,
    onDismiss: () -> Unit,
    onUpdate: (Product) -> Unit
) {
    var regularPrice by remember { mutableStateOf(product.regularPrice) }
    var stockStatus by remember { mutableStateOf(product.stockStatus) }
    var stockStatusExpanded by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.product_details),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 产品图片
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(4.dp)),
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
                        // 使用与列表项相同的占位符样式
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
                
                // 产品名称
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium
                )
                
                if (product.categories.isNotEmpty()) {
                    Text(
                        text = stringResource(id = R.string.product_category, product.categories.joinToString(", ") { it.name }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 可编辑字段
                OutlinedTextField(
                    value = regularPrice,
                    onValueChange = { regularPrice = it },
                    label = { Text("价格") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 库存状态下拉框
                ExposedDropdownMenuBox(
                    expanded = stockStatusExpanded,
                    onExpandedChange = { stockStatusExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = when(stockStatus) {
                            "instock" -> stringResource(id = R.string.stock_status_in_stock)
                            "outofstock" -> stringResource(id = R.string.stock_status_out_of_stock)
                            else -> stockStatus
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(id = R.string.status)) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stockStatusExpanded) }
                    )
                    
                    ExposedDropdownMenu(
                        expanded = stockStatusExpanded,
                        onDismissRequest = { stockStatusExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.stock_status_in_stock)) },
                            onClick = {
                                stockStatus = "instock"
                                stockStatusExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.stock_status_out_of_stock)) },
                            onClick = {
                                stockStatus = "outofstock"
                                stockStatusExpanded = false
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            // 创建更新后的产品对象
                            val updatedProduct = product.copy(
                                regularPrice = regularPrice,
                                salePrice = "", // 移除折扣价
                                stockStatus = stockStatus,
                                stockQuantity = null, // 不再管理具体库存数量
                                status = "publish" // 始终使用发布状态
                            )
                            Log.d("ProductDetailDialog", "更新产品: ID=${product.id}, 名称='${product.name}', 价格=${regularPrice}, 库存状态=${stockStatus}")
                            onUpdate(updatedProduct)
                        }
                    ) {
                        Text(stringResource(id = R.string.save))
                    }
                    
                    Button(
                        onClick = onDismiss
                    ) {
                        Text(stringResource(id = R.string.cancel))
                    }
                }
            }
        }
    }
} 