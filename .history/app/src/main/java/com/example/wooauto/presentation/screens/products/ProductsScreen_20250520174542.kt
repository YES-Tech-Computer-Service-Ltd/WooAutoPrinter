package com.example.wooauto.presentation.screens.products

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wooauto.R
import com.example.wooauto.domain.models.Product
import com.example.wooauto.navigation.NavigationItem
import com.example.wooauto.presentation.components.WooTopBar
import com.example.wooauto.utils.LocalAppLocale
import kotlinx.coroutines.launch
import com.example.wooauto.presentation.EventBus
import com.example.wooauto.presentation.navigation.Screen
import kotlinx.coroutines.DisposableEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    navController: NavController,
    viewModel: ProductsViewModel = hiltViewModel()
) {
    Log.d("ProductsScreen", "ProductsScreen 初始化")
    
    // 添加一个安全状态，用于捕获组合过程中的任何未处理错误
    var hasCompositionError by remember { mutableStateOf(false) }
    var compositionErrorMessage by remember { mutableStateOf<String?>(null) }
    
    // 使用LaunchedEffect捕获任何未处理的异常
    LaunchedEffect(Unit) {
        try {
            // 在这里可以执行一些初始化工作，如果有错误会被捕获
        } catch (e: Exception) {
            Log.e("ProductsScreen", "初始化产品页面时发生错误: ${e.message}", e)
            hasCompositionError = true
            compositionErrorMessage = e.message
        }
    }
    
    // 监听导航到许可证设置页面的状态
    val navigateToLicenseSettings by viewModel.navigateToLicenseSettings.collectAsState()
    
    LaunchedEffect(navigateToLicenseSettings) {
        if (navigateToLicenseSettings) {
            navController.navigate(Screen.LicenseSettings.route)
            viewModel.clearLicenseSettingsNavigation()
        }
    }
    
    // 使用状态机方式处理错误，而不是try-catch
    if (hasCompositionError) {
        // 显示友好的错误UI
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "错误",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "加载产品页面时出现错误",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                compositionErrorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        navController.navigate(NavigationItem.Orders.route) {
                            popUpTo(NavigationItem.Products.route) { inclusive = true }
                        }
                    }
                ) {
                    Text("返回订单页面")
                }
            }
        }
    } else {
        // 正常渲染产品内容
        ProductsScreenContent(
            navController = navController,
            viewModel = viewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductsScreenContent(
    navController: NavController,
    viewModel: ProductsViewModel = hiltViewModel()
) {
    // 使用状态管理来替代try-catch
    val isConfigured by viewModel.isConfigured.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val products by viewModel.products.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedProduct by viewModel.selectedProduct.collectAsState()
    val isRefreshing by viewModel.refreshing.collectAsState()
    
//    // 添加日志跟踪产品数量变化，但避免过多日志
//    LaunchedEffect(products.size) {
//        Log.d("ProductsScreen", "产品列表更新，当前数量: ${products.size}")
//    }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // 搜索和分类相关状态
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    
    // 接收搜索和刷新事件
    val eventScope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        // 订阅搜索事件
        val searchJob = eventScope.launch {
            EventBus.searchEvents.collect { event ->
                if (event.screenRoute == NavigationItem.Products.route) {
                    Log.d("ProductsScreen", "收到搜索事件：${event.query}")
                    searchQuery = event.query
                    if (event.query.isEmpty()) {
                        viewModel.filterProductsByCategory(selectedCategoryId)
                    } else {
                        viewModel.searchProducts(event.query)
                    }
                }
            }
        }
        
        // 订阅刷新事件
        val refreshJob = eventScope.launch {
            EventBus.refreshEvents.collect { event ->
                if (event.screenRoute == NavigationItem.Products.route) {
                    Log.d("ProductsScreen", "收到刷新事件")
                    viewModel.refreshData()
                }
            }
        }

        // 清理协程
        onDispose {
            searchJob.cancel()
            refreshJob.cancel()
            Log.d("ProductsScreen", "清理事件订阅协程")
        }
    }
    
    // 当进入此屏幕时执行刷新操作 - 仅当配置有效时触发一次
    LaunchedEffect(key1 = isConfigured) {
        if (isConfigured) {
            Log.d("ProductsScreen", "配置有效，检查并刷新数据")
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
    
    // 优化防抖动延迟逻辑，提供更长的缓冲时间
    var showErrorScreen by remember { mutableStateOf(false) }
    var errorMessageForDisplay by remember { mutableStateOf<String?>(null) }
    
    // 使用更长的延迟时间，确保有足够的加载时间
    LaunchedEffect(errorMessage, isLoading) {
        if (errorMessage != null && !isLoading) {
            // 保存当前错误消息用于显示
            errorMessageForDisplay = errorMessage
            // 设置较长的延迟，确保有足够的加载时间（3秒）
            kotlinx.coroutines.delay(3000)
            // 如果延迟后错误消息仍然存在且不在加载中，才显示错误界面
            if (errorMessage != null && !isLoading) {
                showErrorScreen = true
                Log.d("ProductsScreen", "显示错误界面: $errorMessage")
            }
        } else {
            // 如果错误消息消失或开始加载，立即隐藏错误界面
            showErrorScreen = false
        }
    }
    
    // 其他UI状态变量
    var showProductDetail by remember { mutableStateOf(false) }
    
    // 添加切换分类时的加载状态跟踪
    var isSwitchingCategory by remember { mutableStateOf(false) }
    
    // 记录上一次显示的产品列表，用于平滑过渡
    var previousProducts by remember { mutableStateOf<List<Product>>(emptyList()) }
    
    // 分类加载时的过渡动画控制
    var fadeTransition by remember { mutableStateOf(1f) }
    
    // 更新产品数据的副作用 - 优化性能
    LaunchedEffect(products.size) {
        // 当获取到新数据时，更新上一次的产品列表
        if (products.isNotEmpty() && !isLoading) {
            previousProducts = products
            fadeTransition = 1f
        }
    }
    
    // 监听加载状态变化 - 只关注实际状态变化
    LaunchedEffect(isLoading, isSwitchingCategory) {
        if (isLoading && isSwitchingCategory) {
            // 加载开始时，逐渐降低透明度但不完全消失
            fadeTransition = 0.7f  // 更高的透明度，提高可见性
        } else if (!isLoading) {
            // 加载完成时，恢复透明度
            fadeTransition = 1f
            isSwitchingCategory = false
        }
    }
    
    // 添加安全超时，防止切换状态卡住
    LaunchedEffect(isSwitchingCategory) {
        if (isSwitchingCategory) {
            // 如果切换状态持续超过3秒，自动重置
            kotlinx.coroutines.delay(3000)  // 减少超时时间
            if (isSwitchingCategory) {
                Log.d("ProductsScreen", "切换分类超时，自动重置状态")
                isSwitchingCategory = false
                fadeTransition = 1f
                // 如果加载状态也卡住了，也重置它
                if (isLoading) {
                    viewModel.resetLoadingState()
                }
            }
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
        listOf(null to stringResource(id = R.string.all_categories))
    } else {
        listOf(null to stringResource(id = R.string.all_categories)) + categories
    }
    
    // 获取当前语言环境
    val locale = LocalAppLocale.current
    
    // 使用Surface包装Scaffold，避免布局问题
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        // 使用key防止Scaffold不必要的重组
        androidx.compose.runtime.key(products.size) {
                        Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    WooTopBar(
                        title = stringResource(id = R.string.products),
                        showSearch = true,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { newQuery -> 
                            searchQuery = newQuery
                            if (newQuery.isEmpty()) {
                                viewModel.filterProductsByCategory(selectedCategoryId)
                            } else {
                                viewModel.searchProducts(newQuery)
                            }
                        },
                        searchPlaceholder = if (locale.language == "zh") "搜索商品..." else "Search products...",
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refreshData() },
                        showRefreshButton = true,
                        locale = locale
                    )
                }
            ) { paddingValues ->
                // 主要内容区域
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // 根据不同状态显示不同内容
                    when {
                        // 如果API未配置或配置错误
                        !isConfigured -> {
                            UnconfiguredView(
                                errorMessage = errorMessage,
                                onSettingsClick = { navController.navigate(NavigationItem.Settings.route) }
                            )
                        }
                        // 如果有错误但API配置正确（如获取数据失败）
                        showErrorScreen -> {
                            ErrorView(
                                errorMessage = errorMessageForDisplay,
                                onRetryClick = { 
                                    viewModel.clearError()
                                    viewModel.refreshData() 
                                },
                                onSettingsClick = { navController.navigate(NavigationItem.Settings.route) }
                            )
                        }
                        // 无数据时显示加载界面
                        products.isEmpty() -> {
                            LoadingProductsView()
                        }
                        // 显示产品列表（默认情况）
                        else -> {
                            ProductsContent(
                                products = products,
                                previousProducts = previousProducts,
                                isLoading = isLoading,
                                isSwitchingCategory = isSwitchingCategory,
                                fadeTransition = fadeTransition,
                                searchQuery = searchQuery,
                                selectedCategoryId = selectedCategoryId,
                                categoryOptions = categoryOptions,
                                onSearchChange = { query ->
                                    searchQuery = query
                                    if (query.isEmpty()) {
                                        Log.d("ProductsScreen", "清空搜索，恢复分类过滤: 分类ID=${selectedCategoryId}")
                                        viewModel.filterProductsByCategory(selectedCategoryId)
                                    } else {
                                        Log.d("ProductsScreen", "执行产品搜索: 关键词='$query'")
                                        viewModel.searchProducts(query)
                                    }
                                },
                                onCategorySelect = { id, name ->
                                    // 如果选择了不同的分类，设置切换状态标志
                                    if (selectedCategoryId != id) {
                                        isSwitchingCategory = true
                                        // 在开始新的加载前，保存当前产品列表作为过渡
                                        if (products.isNotEmpty()) {
                                            previousProducts = products
                                        }
                                        fadeTransition = 0.7f
                                    }
                                    selectedCategoryId = id
                                    Log.d("ProductsScreen", "选择产品分类: ID=${id}, 名称='$name'")
                                    viewModel.filterProductsByCategory(id)
                                },
                                onProductClick = { productId ->
                                    viewModel.getProductDetails(productId)
                                    showProductDetail = true
                                },
                                onRefreshClick = { viewModel.refreshData() }
                            )
                        }
                    }
                    
                    // 产品详情弹窗 - 使用key确保对话框状态稳定
                    if (showProductDetail && selectedProduct != null) {
                        val product = selectedProduct!!
                        androidx.compose.runtime.key(product.id) {
                            ProductDetailDialog(
                                product = product,
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
        }
    }
}

@Composable
fun UnconfiguredView(
    errorMessage: String?,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 0.dp),
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
            text = stringResource(id = R.string.error_api_not_configured),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = errorMessage?.toString() ?: "WooCommerce API未配置",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onSettingsClick,
            modifier = Modifier.padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(text = stringResource(id = R.string.go_to_settings))
        }
    }
}

@Composable
fun ErrorView(
    errorMessage: String?,
    onRetryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
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
            text = stringResource(id = R.string.error_loading_products),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = errorMessage?.toString() ?: "未知错误",
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
                onClick = onRetryClick,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = stringResource(id = R.string.retry))
            }
            
            Button(
                onClick = onSettingsClick,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = stringResource(id = R.string.check_settings))
            }
        }
    }
}

@Composable
fun LoadingProductsView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(id = R.string.loading_products),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(id = R.string.loading_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsContent(
    products: List<Product>,
    previousProducts: List<Product>,
    isLoading: Boolean,
    isSwitchingCategory: Boolean,
    fadeTransition: Float,
    searchQuery: String,
    selectedCategoryId: Long?,
    categoryOptions: List<Pair<Long?, String>>,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (Long?, String) -> Unit,
    onProductClick: (Long) -> Unit,
    onRefreshClick: () -> Unit
) {
    // 使用列表状态，支持滚动到指定位置
    val lazyListState = rememberLazyListState()
    
    // 计算要显示的产品列表，根据不同状态选择
    val displayProducts = if (products.isEmpty() && previousProducts.isNotEmpty() && isSwitchingCategory) {
        // 如果切换分类正在加载，且之前有数据，则显示previousProducts
        previousProducts
    } else {
        // 否则显示当前产品列表
        products
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // 分类选择区域
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp) // 减少底部边距
        ) {
            // 分类筛选器
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp), // 减少垂直边距为2dp
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp), // 减少水平内边距
                state = lazyListState
            ) {
                items(
                    items = categoryOptions,
                    key = { it.first ?: -1L } // 使用分类ID作为key
                ) { (id, name) ->
                    FilterChip(
                        selected = selectedCategoryId == id,
                        enabled = true,
                        onClick = { onCategorySelect(id, name) },
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
            
            Spacer(modifier = Modifier.height(8.dp)) // 减少垂直空间
        }
            
        // 产品列表区域
        Box(
            modifier = Modifier.weight(1f)
        ) {
            if (displayProducts.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(fadeTransition)
                ) {
                    items(
                        items = displayProducts,
                        key = { it.id } // 使用产品ID作为key避免重组
                    ) { product ->
                        // 使用key包装每个产品项防止不必要的重组
                        androidx.compose.runtime.key(product.id) {
                            ProductGridItem(
                                product = product,
                                onClick = { onProductClick(product.id) }
                            )
                        }
                    }
                }
            }
            
            // 加载指示器更美观且不遮挡内容
            if (isLoading || isSwitchingCategory) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.5f), // 降低透明度，使背景内容更可见
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isSwitchingCategory) 
                                    stringResource(id = R.string.switching_category) 
                                else 
                                    stringResource(id = R.string.loading_products),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
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
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部容器（图片和标题）
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                // 产品图片
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .align(Alignment.CenterHorizontally),
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
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 产品ID和名称
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 产品ID (如B8, C2等)
                    Text(
                        text = product.sku.ifEmpty { product.id.toString() },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    
                    // 产品名称 - 确保居中对齐
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // 底部容器（价格和库存）
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 分隔线
                Spacer(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .height(1.dp)
                        .fillMaxWidth()
                        .background(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                )
                
                // 价格
                Text(
                    text = "C$${product.regularPrice}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                
                // 库存
                Text(
                    text = if (product.stockStatus == "instock") 
                             stringResource(id = R.string.stock_status_in_stock)
                           else 
                             stringResource(id = R.string.stock_status_out_of_stock),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (product.stockStatus == "instock") 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
} 