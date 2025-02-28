package com.wooauto.presentation.screens.products

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.wooauto.presentation.components.ErrorState
import com.wooauto.presentation.components.LoadingIndicator
import com.wooauto.presentation.viewmodels.ProductDetailsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProductDetailsScreen(
    productId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ProductDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val swipeRefreshState = rememberSwipeRefreshState(uiState.isLoading)
    var showEditDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    AnimatedContent(
                        targetState = uiState.product?.name ?: "产品详情",
                        transitionSpec = {
                            fadeIn() + slideInVertically() with 
                            fadeOut() + slideOutVertically()
                        }
                    ) { title ->
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
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
                                ProductImageGallery(images = uiState.product!!.images)
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
            AnimatedVisibility(
                visible = uiState.isLoading && uiState.product != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductImageGallery(
    images: List<String>
) {
    if (images.isNotEmpty()) {
        val pagerState = rememberPagerState(pageCount = { images.size })
        val scope = rememberCoroutineScope()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    ) {
                        AsyncImage(
                            model = images[page],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            if (images.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    images.forEachIndexed { index, _ ->
                        Surface(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(8.dp),
                            shape = CircleShape,
                            color = if (pagerState.currentPage == index)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ) {}
                    }
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
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
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "¥$regularPrice",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                } else {
                    Text(
                        text = "¥$regularPrice",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge
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

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "库存信息",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Surface(
                        color = when (stockStatus) {
                            "instock" -> MaterialTheme.colorScheme.primaryContainer
                            "outofstock" -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = when (stockStatus) {
                                "instock" -> "有货"
                                "outofstock" -> "缺货"
                                else -> "未知"
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = when (stockStatus) {
                                "instock" -> MaterialTheme.colorScheme.onPrimaryContainer
                                "outofstock" -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    if (stockQuantity != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "库存数量: $stockQuantity",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                
                FilledTonalButton(onClick = { showDialog = true }) {
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
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("库存数量") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "产品属性",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            attributes.forEach { attribute ->
                Column(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = attribute.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = attribute.options.joinToString(", "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (attribute != attributes.last()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = regularPrice,
                    onValueChange = { regularPrice = it },
                    label = { Text("正常价格") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = salePrice,
                    onValueChange = { salePrice = it },
                    label = { Text("促销价格") },
                    modifier = Modifier.fillMaxWidth()
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