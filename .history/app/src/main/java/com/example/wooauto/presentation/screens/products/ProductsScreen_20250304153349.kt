package com.example.wooauto.presentation.screens.products

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.wooauto.R
import com.example.wooauto.domain.models.Product
import com.example.wooauto.navigation.NavigationItem
import com.example.wooauto.presentation.theme.WooAutoTheme
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.foundation.layout.PaddingValues

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    navController: NavController,
    viewModel: ProductsViewModel = hiltViewModel()
) {
    Log.d("ProductsScreen", "ProductsScreen 初始化")
    
    val isConfigured by viewModel.isConfigured.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val products by viewModel.products.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedProduct by viewModel.selectedProduct.collectAsState()
    val isRefreshing by viewModel.refreshing.collectAsState()
    
    // 添加日志跟踪产品数量变化
    LaunchedEffect(products) {
        Log.d("ProductsScreen", "产品列表更新，当前数量: ${products.size}")
    }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // 当进入此屏幕时执行刷新操作
    LaunchedEffect(Unit) {
        Log.d("ProductsScreen", "LaunchedEffect 触发，刷新产品数据")
        if (isConfigured) {
            // 首次加载时通过refreshData加载数据，但不直接重置已过滤的结果
            viewModel.checkAndRefreshConfig()
        }
    }
    
    // 显示错误消息
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }
    
    var searchQuery by remember { mutableStateOf("") }
    var showProductDetail by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }
    
    // 添加切换分类时的加载状态跟踪
    var isSwitchingCategory by remember { mutableStateOf(false) }
    
    // 监听加载状态变化
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            isSwitchingCategory = false
        }
    }
    
    // 同步ViewModel中的分类ID状态
    val currentSelectedCategoryId by viewModel.currentSelectedCategoryId.collectAsState()
    
    // 确保UI的selectedCategoryId和ViewModel中的保持同步
    LaunchedEffect(currentSelectedCategoryId) {
        if (currentSelectedCategoryId != selectedCategoryId) {
            selectedCategoryId = currentSelectedCategoryId
            Log.d("ProductsScreen", "同步分类ID: $selectedCategoryId")
        }
    }
    
    val categoryOptions = if (categories.isEmpty()) {
        listOf(null to "全部分类")
    } else {
        listOf(null to "全部分类") + categories
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 如果API未配置或配置错误
            if (!isConfigured) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "未配置WooCommerce API",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = errorMessage.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = { navController.navigate(NavigationItem.Settings.route) },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = "前往设置")
                    }
                }
            }
            // 如果有错误但API配置正确（如获取数据失败）
            else if (errorMessage != null && !isRefreshing) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "获取产品数据失败",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = errorMessage.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { 
                                viewModel.clearError()
                                viewModel.refreshData() 
                            },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                            Text(text = "重试")
                        }
                        
                        Button(
                            onClick = { navController.navigate(NavigationItem.Settings.route) },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                            Text(text = "检查设置")
                        }
                    }
                }
            }
            // 数据加载中
            else if (isLoading || isRefreshing) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在加载...")
                }
            }
            // 产品列表
            else if (products.isEmpty() && !isLoading && !isRefreshing) {
                // 空列表状态
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "没有产品数据",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "您的WooCommerce商店中可能还没有产品，或API访问权限不足",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = { viewModel.refreshData() },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = "刷新数据")
                    }
                }
            }
            // 已配置，显示产品列表
            else {
                Log.d("ProductsScreen", "显示产品列表，共 ${products.size} 个产品")
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // 顶部提示和刷新按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "产品列表",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        IconButton(
                            onClick = { viewModel.refreshData() },
                            modifier = Modifier.size(48.dp),
                            enabled = !isRefreshing
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "刷新",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 搜索和过滤
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 搜索框
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { 
                                searchQuery = it
                                if (it.isEmpty()) {
                                    Log.d("ProductsScreen", "清空搜索，恢复分类过滤: 分类ID=${selectedCategoryId}")
                                    viewModel.filterProductsByCategory(selectedCategoryId)
                                } else {
                                    Log.d("ProductsScreen", "执行产品搜索: 关键词='$it'")
                                    viewModel.searchProducts(it)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("搜索产品...") },
                            leadingIcon = { 
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "搜索"
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { 
                                        searchQuery = ""
                                        viewModel.filterProductsByCategory(selectedCategoryId)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "清除"
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    // 分类过滤器 - 水平滚动按钮样式
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        state = rememberLazyListState()
                    ) {
                        items(categoryOptions) { (id, name) ->
                            FilterChip(
                                selected = selectedCategoryId == id,
                                onClick = {
                                    selectedCategoryId = id
                                    Log.d("ProductsScreen", "选择产品分类: ID=${id}, 名称='$name'")
                                    viewModel.filterProductsByCategory(id)
                                },
                                label = { 
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium
                                    ) 
                                },
                                leadingIcon = if (selectedCategoryId == id) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 产品列表
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(products) { product ->
                            ProductGridItem(
                                product = product,
                                onClick = {
                                    viewModel.getProductDetails(product.id)
                                    showProductDetail = true
                                }
                            )
                        }
                    }
                }
                
                // 产品详情弹窗
                if (showProductDetail && selectedProduct != null) {
                    ProductDetailDialog(
                        product = selectedProduct!!,
                        onDismiss = { 
                            showProductDetail = false
                            viewModel.clearSelectedProduct()
                        },
                        onUpdate = { updatedProduct ->
                            viewModel.updateProduct(updatedProduct)
                            showProductDetail = false
                        }
                    )
                }
            }
        }
    }

    // 添加OnResume效果检查配置变化
    LaunchedEffect(Unit) {
        // 当导航返回到产品页面时，检查配置
        viewModel.checkAndRefreshConfig()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductGridItem(
    product: Product,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            // 产品图片
            if (product.images.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(product.images.first().src)
                        .crossfade(true)
                        .build(),
                    contentDescription = product.name,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = R.drawable.ic_launcher_foreground),
                    placeholder = painterResource(id = R.drawable.ic_launcher_foreground)
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = product.name,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 产品名称
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 价格
            Text(
                text = "¥${product.regularPrice}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 库存
            Text(
                text = if (product.stockStatus == "instock") "有库存" else "无库存",
                style = MaterialTheme.typography.bodySmall,
                color = if (product.stockStatus == "instock") 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
        }
    }
}

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
                    text = "产品详情",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 产品图片
                if (product.images.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(product.images.first().src)
                            .crossfade(true)
                            .build(),
                        contentDescription = product.name,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop,
                        error = painterResource(id = R.drawable.ic_launcher_foreground),
                        placeholder = painterResource(id = R.drawable.ic_launcher_foreground)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = product.name,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 产品名称
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium
                )
                
                if (product.categories.isNotEmpty()) {
                    Text(
                        text = "分类: ${product.categories.joinToString(", ") { it.name }}",
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
                            "instock" -> "有库存"
                            "outofstock" -> "无库存"
                            else -> stockStatus
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("库存状态") },
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
                            text = { Text("有库存") },
                            onClick = {
                                stockStatus = "instock"
                                stockStatusExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("无库存") },
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
                            Log.d("ProductsScreen", "更新产品: ID=${product.id}, 名称='${product.name}', 价格=${regularPrice}, 库存状态=${stockStatus}")
                            onUpdate(updatedProduct)
                        }
                    ) {
                        Text("保存")
                    }
                    
                    Button(
                        onClick = onDismiss
                    ) {
                        Text("取消")
                    }
                }
            }
        }
    }
} 