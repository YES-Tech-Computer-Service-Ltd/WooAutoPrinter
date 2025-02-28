package com.wooauto.presentation.screens.products

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.wooauto.presentation.components.ErrorState
import com.wooauto.presentation.components.LoadingIndicator
import com.wooauto.presentation.viewmodels.ProductDetailsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailsScreen(
    productId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ProductDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val swipeRefreshState = rememberSwipeRefreshState(uiState.isLoading)
    var showEditDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("产品详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showEditDialog = true },
                        enabled = uiState.product != null
                    ) {
                        Icon(Icons.Default.Edit, "编辑产品")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.error != null -> {
                    ErrorState(
                        message = uiState.error!!,
                        onRetry = { viewModel.refreshProduct() }
                    )
                }
                uiState.isLoading && uiState.product == null -> {
                    LoadingIndicator()
                }
                uiState.product != null -> {
                    SwipeRefresh(
                        state = swipeRefreshState,
                        onRefresh = { viewModel.refreshProduct() }
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 产品图片
                            item {
                                ProductImages(images = uiState.product!!.images)
                            }

                            // 产品基本信息
                            item {
                                ProductBasicInfo(
                                    name = uiState.product!!.name,
                                    description = uiState.product!!.description,
                                    regularPrice = uiState.product!!.regularPrice,
                                    salePrice = uiState.product!!.salePrice
                                )
                            }

                            // 库存信息
                            item {
                                StockInfo(
                                    stockStatus = uiState.product!!.stockStatus,
                                    stockQuantity = uiState.product!!.stockQuantity,
                                    onUpdateStock = { viewModel.updateProductStock(it) }
                                )
                            }

                            // 产品属性
                            if (!uiState.product!!.attributes.isNullOrEmpty()) {
                                item {
                                    ProductAttributes(attributes = uiState.product!!.attributes)
                                }
                            }
                        }
                    }
                }
            }

            // 加载指示器覆盖层
            if (uiState.isLoading && uiState.product != null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }

    // 编辑对话框
    if (showEditDialog && uiState.product != null) {
        EditProductDialog(
            product = uiState.product!!,
            onDismiss = { showEditDialog = false },
            onConfirm = { regularPrice, salePrice ->
                viewModel.updateProductPrices(regularPrice, salePrice)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun ProductImages(
    images: List<String>
) {
    if (images.isNotEmpty()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(images) { imageUrl ->
                Card {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .width(200.dp)
                            .height(200.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductBasicInfo(
    name: String,
    description: String,
    regularPrice: String,
    salePrice: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (salePrice != null) {
                    Text(
                        text = "¥$salePrice",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "¥$regularPrice",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "¥$regularPrice",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockInfo(
    stockStatus: String,
    stockQuantity: Int?,
    onUpdateStock: (Int?) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var quantity by remember(stockQuantity) { mutableStateOf(stockQuantity?.toString() ?: "") }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "库存信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when (stockStatus) {
                            "instock" -> "有货"
                            "outofstock" -> "缺货"
                            else -> "未知"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = when (stockStatus) {
                            "instock" -> MaterialTheme.colorScheme.primary
                            "outofstock" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (stockQuantity != null) {
                        Text(
                            text = "库存数量: $stockQuantity",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Button(onClick = { showDialog = true }) {
                    Text("更新库存")
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("更新库存") },
            text = {
                TextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("库存数量") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        quantity.toIntOrNull()?.let { onUpdateStock(it) }
                        showDialog = false
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ProductAttributes(
    attributes: List<ProductAttributeEntity>
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "产品属性",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            attributes.forEach { attribute ->
                Column(
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = attribute.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = attribute.options.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProductDialog(
    product: Product,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var regularPrice by remember { mutableStateOf(product.regularPrice) }
    var salePrice by remember { mutableStateOf(product.salePrice ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑产品") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = regularPrice,
                    onValueChange = { regularPrice = it },
                    label = { Text("正常价格") }
                )
                TextField(
                    value = salePrice,
                    onValueChange = { salePrice = it },
                    label = { Text("促销价格") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        regularPrice,
                        salePrice.takeIf { it.isNotBlank() }
                    )
                }
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
} 