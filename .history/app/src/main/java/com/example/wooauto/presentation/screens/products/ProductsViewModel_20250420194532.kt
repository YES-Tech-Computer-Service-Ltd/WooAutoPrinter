package com.example.wooauto.presentation.screens.products

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.data.local.WooCommerceConfig
import com.example.wooauto.domain.models.Category
import com.example.wooauto.domain.models.Product
import com.example.wooauto.domain.repositories.DomainProductRepository
import com.example.wooauto.domain.repositories.DomainSettingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.UnknownHostException
import com.google.gson.JsonParseException
import java.util.concurrent.ConcurrentHashMap

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val wooCommerceConfig: WooCommerceConfig,
    private val productRepository: DomainProductRepository,
    private val settingsRepository: DomainSettingRepository
) : ViewModel() {
    
    private val _isConfigured = MutableStateFlow(false)
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()
    
    private val _categories = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    val categories: StateFlow<List<Pair<Long, String>>> = _categories.asStateFlow()
    
    private val _selectedProduct = MutableStateFlow<Product?>(null)
    val selectedProduct: StateFlow<Product?> = _selectedProduct.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    
    private val _currentSelectedCategoryId = MutableStateFlow<Long?>(null)
    val currentSelectedCategoryId: StateFlow<Long?> = _currentSelectedCategoryId.asStateFlow()
    
    // 用于取消之前的Flow收集作业
    private var productsCollectionJob: kotlinx.coroutines.Job? = null
    
    // 新增：缓存已加载的分类数据，避免重复网络请求
    private val categoryProductsCache = ConcurrentHashMap<Long, Pair<List<Product>, Long>>()
    
    // 特殊值：代表"全部产品"的分类ID
    private val ALL_PRODUCTS_KEY = -1L
    
    // 新增：记录最后一次分类数据刷新时间，用于智能刷新策略
    private val lastCategoryRefreshTime = mutableMapOf<Long?, Long>()
    
    // 新增：智能刷新阈值（毫秒）- 30分钟内不重复刷新同一分类
    private val refreshThreshold = 30 * 60 * 1000L
    
    // 缓存上次全部产品加载时间
    private var lastAllProductsLoadTime: Long = 0
    
    // 缓存刷新阈值 (15分钟)
    private val cacheRefreshThreshold = 15 * 60 * 1000L
    
    // 取消所有产品集合作业的标志
    private var shouldCancelProductJobs = false
    
    init {
        Log.d("ProductsViewModel", "初始化ProductsViewModel")
        viewModelScope.launch {
            try {
                // 在初始化时不显示加载状态，避免闪烁
                checkConfiguration(showLoadingIndicator = false)
            } catch (e: Exception) {
                // 忽略初始化时的异常，会在后续交互中处理
                Log.e("ProductsViewModel", "初始化时出错: ${e.message}")
            }
        }
    }
    
    // 检查配置状态，可选显示加载指示器
    private suspend fun checkConfiguration(showLoadingIndicator: Boolean = true) {
        try {
            Log.d("ProductsViewModel", "正在检查API配置")
            
            // 只有在需要时显示加载指示器
            if (showLoadingIndicator) {
                _isLoading.value = true
            }
            
            val config = settingsRepository.getWooCommerceConfig()
            
            // 检查配置有效性
            if (!config.isValid()) {
                Log.e("ProductsViewModel", "API配置无效: $config")
                _isConfigured.value = false
                _errorMessage.value = "WooCommerce API未正确配置，请在设置中检查"
                _isLoading.value = false
                return
            }
            
            Log.d("ProductsViewModel", "API配置有效，尝试加载数据")
            _isConfigured.value = true
            
            // 首先加载分类
            loadCategories()
            
            // 只有在没有选择分类的情况下才加载所有产品
            if (_currentSelectedCategoryId.value == null) {
                loadAllProducts()
            }
            
            // 刷新远程数据但不阻止界面显示
            viewModelScope.launch {
                try {
                    refreshData(showFullscreenLoading = false)
                } catch (e: Exception) {
                    // 忽略刷新时的异常，因为我们已经有了本地数据
                    Log.e("ProductsViewModel", "首次自动刷新时出错: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                // 忽略协程取消异常，这是正常的流程控制，不应显示为错误
                Log.d("ProductsViewModel", "检查配置时协程被取消")
                return
            }
            
            Log.e("ProductsViewModel", "检查配置时出错: ${e.message}")
            _isConfigured.value = false
            _isLoading.value = false
            
            // 配置错误时才显示错误消息
            if (_errorMessage.value == null) {
                _errorMessage.value = "无法检查API配置: ${e.message}"
            }
        } finally {
            if (showLoadingIndicator) {
                _isLoading.value = false
            }
        }
    }
    
    private fun loadAllProducts() {
        // 取消之前的产品收集作业
        productsCollectionJob?.cancel()
        
        // 从缓存加载全部产品 - 立即显示，无延迟
        if (categoryProductsCache.containsKey(ALL_PRODUCTS_KEY)) {
            Log.d("ProductsViewModel", "立即从缓存加载全部产品")
            _products.value = categoryProductsCache[ALL_PRODUCTS_KEY]?.first ?: emptyList()
            // 如果缓存很新，就直接返回，不加载
            val currentTime = System.currentTimeMillis()
            val lastRefresh = categoryProductsCache[ALL_PRODUCTS_KEY]?.second ?: 0L
            if ((currentTime - lastRefresh) < refreshThreshold) {
                _isLoading.value = false
                return
            }
        }
        
        productsCollectionJob = viewModelScope.launch {
            try {
                Log.d("ProductsViewModel", "开始加载所有产品")
                _isLoading.value = true
                
                // 检查缓存是否有效
                val currentTime = System.currentTimeMillis()
                val shouldUseCache = !shouldCancelProductJobs && 
                        lastAllProductsLoadTime > 0 && 
                        (currentTime - lastAllProductsLoadTime < cacheRefreshThreshold)
                
                if (shouldUseCache && _products.value.isNotEmpty()) {
                    Log.d("ProductsViewModel", "使用缓存的所有产品数据")
                    // 已有数据且不强制刷新，直接返回
                    return@launch
                }
                
                // 仅从本地数据库加载，无网络延迟
                val localProducts = productRepository.getProducts()
                if (localProducts.isNotEmpty()) {
                    Log.d("ProductsViewModel", "立即显示本地产品: ${localProducts.size}个")
                    _products.value = localProducts
                    // 如果有本地数据可立即显示，取消加载状态
                    _isLoading.value = false
                }
                
                // 如果是强制刷新，或数据较旧，或本地数据为空，则从网络刷新
                if (shouldCancelProductJobs || 
                    (currentTime - lastAllProductsLoadTime) > cacheRefreshThreshold || 
                    localProducts.isEmpty()) {
                    
                    if (!shouldCancelProductJobs) {
                        // 强制刷新已经设置了refreshing，非强制刷新设置loading
                        _isLoading.value = true
                    }
                    
                    Log.d("ProductsViewModel", "从网络刷新产品数据")
                    // 网络刷新产品数据
                    productRepository.refreshProducts()
                    
                    // 从Flow获取最新数据
                    productRepository.getAllProductsFlow().collect { updatedProducts ->
                        if (shouldCancelProductJobs) return@collect
                        
                        _products.value = updatedProducts
                        lastAllProductsLoadTime = System.currentTimeMillis()
                        
                        // 更新缓存
                        categoryProductsCache[ALL_PRODUCTS_KEY] = Pair(updatedProducts, System.currentTimeMillis())
                        
                        Log.d("ProductsViewModel", "成功更新所有产品: ${updatedProducts.size} 个")
                        
                        _isLoading.value = false
                        _refreshing.value = false
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    // 忽略协程取消异常，这是正常的流程控制
                    Log.d("ProductsViewModel", "加载产品协程被取消，这是正常的")
                    _isLoading.value = false
                    _refreshing.value = false
                    return@launch
                }
                
                Log.e("ProductsViewModel", "加载产品出错: ${e.message}")
                
                // 检查网络相关错误，给出更友好的提示
                val errorMsg = when {
                    e is UnknownHostException || e is IOException -> "网络连接问题，请检查您的网络设置"
                    e is JsonParseException -> "数据解析错误，请稍后再试"
                    e.message?.contains("timeout", ignoreCase = true) == true -> "连接超时，请稍后再试"
                    else -> "无法加载产品: ${e.message}"
                }
                
                _errorMessage.value = errorMsg
                _isLoading.value = false
                _refreshing.value = false
            }
        }
    }
    
    private fun loadCategories() {
        viewModelScope.launch {
            try {
                Log.d("ProductsViewModel", "正在加载产品分类")
                val categoriesList = productRepository.getAllCategories()
                Log.d("ProductsViewModel", "已加载 ${categoriesList.size} 个分类")
                _categories.value = categoriesList
            } catch (e: Exception) {
                Log.e("ProductsViewModel", "加载分类时出错: ${e.message}")
                // 不设置错误消息，以免覆盖可能更重要的产品加载错误
            }
        }
    }
    
    fun refreshData(showFullscreenLoading: Boolean = true) {
        viewModelScope.launch {
            Log.d("ProductsViewModel", "刷新所有数据")
            try {
                _refreshing.value = true
                // 只有在需要时才显示全屏加载
                if (showFullscreenLoading) {
                    _isLoading.value = true
                }
                
                // 清除缓存
                categoryProductsCache.clear()
                lastAllProductsLoadTime = 0
                
                // 刷新分类
                loadCategories()
                
                // 根据当前筛选状态刷新产品
                if (_currentSelectedCategoryId.value != null) {
                    filterProductsByCategory(_currentSelectedCategoryId.value)
                } else {
                    loadAllProducts()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    // 忽略取消异常，这是正常的流程控制
                    Log.d("ProductsViewModel", "刷新数据协程已取消")
                    return@launch
                }
                
                Log.e("ProductsViewModel", "刷新数据出错: ${e.message}")
                _errorMessage.value = "刷新数据时发生错误: ${e.message}"
                
                // 检查是否是API认证错误
                if (e.message?.contains("Authentication failed") == true || 
                    e.message?.contains("401") == true) {
                    _errorMessage.value = "API认证失败。请检查您的Consumer Key和Consumer Secret。"
                }
            } finally {
                if (showFullscreenLoading) {
                    _isLoading.value = false
                }
                _refreshing.value = false
            }
        }
    }
    
    fun filterProductsByCategory(categoryId: Long?) {
        // 取消之前的产品收集作业
        productsCollectionJob?.cancel()
        
        viewModelScope.launch {
            try {
                Log.d("ProductsViewModel", "正在按分类过滤产品: 分类ID=${categoryId ?: "全部"}")
                
                // 使用适当的键（对于null使用ALL_PRODUCTS_KEY）
                val cacheKey = categoryId ?: ALL_PRODUCTS_KEY
                
                // 标记为加载中，但只有在没有缓存时才设置完全加载状态
                val hasCachedData = categoryProductsCache.containsKey(cacheKey)
                
                // 存储当前选择的分类ID
                _currentSelectedCategoryId.value = categoryId
                
                // 优先从缓存加载 - 立即显示，无延迟
                if (hasCachedData) {
                    Log.d("ProductsViewModel", "立即从缓存加载分类 ${categoryId ?: "全部"} 的产品")
                    _products.value = categoryProductsCache[cacheKey]?.first ?: emptyList()
                    // 如果有缓存数据，则不标记为加载中，避免UI闪烁
                } else {
                    // 如果没有缓存，则设置加载状态
                    _isLoading.value = true
                }
                
                if (categoryId == null) {
                    Log.d("ProductsViewModel", "加载所有产品")
                    // 如果没有缓存，或者缓存很旧，才加载所有产品
                    val shouldRefresh = !hasCachedData || 
                            (System.currentTimeMillis() - (categoryProductsCache[cacheKey]?.second ?: 0L)) > refreshThreshold
                    
                    if (shouldRefresh) {
                        loadAllProducts()
                    } else {
                        // 如果刚从缓存加载了数据，就不需要再加载了
                        _isLoading.value = false
                    }
                } else {
                    // 判断是否需要加载新数据
                    val currentTime = System.currentTimeMillis()
                    val lastRefresh = categoryProductsCache[cacheKey]?.second ?: 0L
                    val shouldRefresh = !hasCachedData || (currentTime - lastRefresh) > refreshThreshold
                    
                    if (shouldRefresh) {
                        Log.d("ProductsViewModel", "需要更新分类 $categoryId 数据")
                        
                        try {
                            // 只有在确实需要刷新时，才执行网络请求
                            if ((currentTime - lastRefresh) > refreshThreshold) {
                                Log.d("ProductsViewModel", "从网络刷新分类 $categoryId 数据")
                                // 尝试刷新该分类的产品
                                val refreshResult = productRepository.refreshProducts(categoryId)
                                if (refreshResult.isSuccess) {
                                    Log.d("ProductsViewModel", "成功刷新分类 $categoryId 的产品")
                                    val productsForCategory = refreshResult.getOrDefault(emptyList())
                                    _products.value = productsForCategory
                                    
                                    // 更新缓存
                                    categoryProductsCache[cacheKey] = Pair(productsForCategory, currentTime)
                                    
                                    _isLoading.value = false
                                    return@launch  // 已获取数据，不需要继续
                                }
                            }
                            
                            // 如果网络刷新未执行或失败，使用本地数据
                            if (!hasCachedData) {
                                Log.d("ProductsViewModel", "从本地加载分类 $categoryId 数据")
                                // 使用单协程加载本地数据，避免多余的协程创建
                                val filteredProducts = productRepository.getProductsByCategory(categoryId)
                                Log.d("ProductsViewModel", "从本地获取到分类 $categoryId 的产品: ${filteredProducts.size} 个")
                                
                                // 更新缓存
                                categoryProductsCache[cacheKey] = Pair(filteredProducts, currentTime)
                                
                                _products.value = filteredProducts
                            }
                            
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) {
                                Log.d("ProductsViewModel", "分类过滤协程被取消，这是正常的")
                                throw e // 协程取消异常需要向上传播
                            } else {
                                Log.e("ProductsViewModel", "获取分类产品时出错: ${e.message}", e)
                                // 如果出错，尝试从内存中过滤
                                if (!hasCachedData) {
                                    handleInMemoryFiltering(categoryId)
                                }
                                
                                // 检查网络相关错误，给出更友好的提示
                                val errorMsg = when {
                                    e is UnknownHostException || e is IOException -> "网络连接问题，请检查您的网络设置"
                                    e is JsonParseException -> "数据解析错误，请稍后再试"
                                    e.message?.contains("timeout", ignoreCase = true) == true -> "连接超时，请稍后再试"
                                    else -> "无法获取分类产品: ${e.message}"
                                }
                                
                                _errorMessage.value = errorMsg
                            }
                        }
                    } else {
                        Log.d("ProductsViewModel", "使用现有缓存的分类 $categoryId 产品数据，无需加载")
                    }
                }
            } catch (e: Exception) {
                // 忽略协程取消异常
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d("ProductsViewModel", "过滤产品协程已取消，这是正常流程")
                } else {
                    Log.e("ProductsViewModel", "过滤产品时出错: ${e.message}", e)
                    
                    // 检查网络相关错误，给出更友好的提示
                    val errorMsg = when {
                        e is UnknownHostException || e is IOException -> "网络连接问题，请检查您的网络设置"
                        e is JsonParseException -> "数据解析错误，请稍后再试"
                        e.message?.contains("timeout", ignoreCase = true) == true -> "连接超时，请稍后再试"
                        else -> "无法过滤产品: ${e.message}"
                    }
                    
                    _errorMessage.value = errorMsg
                }
            } finally {
                // 确保加载状态最终会被重置
                _isLoading.value = false
            }
        }
    }
    
    // 辅助方法：从内存中过滤产品
    private fun handleInMemoryFiltering(categoryId: Long) {
        Log.d("ProductsViewModel", "尝试从内存中过滤分类 $categoryId 的产品")
        val allProducts = _products.value
        val filtered = allProducts.filter { product ->
            product.categories.any { it.id == categoryId }
        }
        Log.d("ProductsViewModel", "内存过滤后得到 ${filtered.size} 个产品")
        _products.value = filtered
    }
    
    fun searchProducts(query: String) {
        // 取消之前的产品收集作业
        productsCollectionJob?.cancel()
        
        viewModelScope.launch {
            try {
                Log.d("ProductsViewModel", "正在搜索产品: 关键词='$query'")
                _isLoading.value = true
                if (query.isEmpty()) {
                    // 如果搜索词为空，恢复当前的分类过滤
                    if (_currentSelectedCategoryId.value != null) {
                        Log.d("ProductsViewModel", "搜索词为空，恢复分类过滤: ${_currentSelectedCategoryId.value}")
                        filterProductsByCategory(_currentSelectedCategoryId.value)
                    } else {
                        loadAllProducts()
                    }
                } else {
                    // 使用新的作业跟踪搜索结果的获取
                    productsCollectionJob = launch {
                        try {
                            productRepository.searchProductsFlow(query).collectLatest { searchResults ->
                                Log.d("ProductsViewModel", "搜索结果: 找到 ${searchResults.size} 个匹配产品")
                                _products.value = searchResults
                                _isLoading.value = false
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) {
                                Log.d("ProductsViewModel", "搜索产品协程已取消，这是正常流程")
                            } else {
                                Log.e("ProductsViewModel", "搜索产品时出错: ${e.message}", e)
                                
                                // 检查网络相关错误，给出更友好的提示
                                val errorMsg = when {
                                    e is UnknownHostException || e is IOException -> "网络连接问题，请检查您的网络设置"
                                    e is JsonParseException -> "数据解析错误，请稍后再试"
                                    e.message?.contains("timeout", ignoreCase = true) == true -> "连接超时，请稍后再试"
                                    else -> "无法搜索产品: ${e.message}"
                                }
                                
                                _errorMessage.value = errorMsg
                            }
                            _isLoading.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                // 忽略协程取消异常
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d("ProductsViewModel", "搜索产品协程已取消，这是正常流程")
                } else {
                    Log.e("ProductsViewModel", "搜索产品时出错: ${e.message}", e)
                    
                    // 检查网络相关错误，给出更友好的提示
                    val errorMsg = when {
                        e is UnknownHostException || e is IOException -> "网络连接问题，请检查您的网络设置"
                        e is JsonParseException -> "数据解析错误，请稍后再试"
                        e.message?.contains("timeout", ignoreCase = true) == true -> "连接超时，请稍后再试"
                        else -> "无法搜索产品: ${e.message}"
                    }
                    
                    _errorMessage.value = errorMsg
                }
                _isLoading.value = false
            }
        }
    }
    
    fun getProductDetails(productId: Long) {
        viewModelScope.launch {
            try {
                Log.d("ProductsViewModel", "正在获取产品详情: $productId")
                val product = productRepository.getProductById(productId)
                _selectedProduct.value = product
            } catch (e: Exception) {
                Log.e("ProductsViewModel", "获取产品详情时出错: ${e.message}")
                _errorMessage.value = "无法获取产品详情: ${e.message}"
            }
        }
    }
    
    fun updateProduct(product: Product) {
        viewModelScope.launch {
            try {
                Log.d("ProductsViewModel", "正在更新产品: ID=${product.id}, 名称='${product.name}', 价格=${product.regularPrice}, 库存状态=${product.stockStatus}")
                _isLoading.value = true
                val result = productRepository.updateProduct(product)
                if (result.isSuccess) {
                    Log.d("ProductsViewModel", "成功更新产品: ${product.id}")
                    _selectedProduct.value = result.getOrNull()
                    refreshData()
                } else {
                    Log.e("ProductsViewModel", "更新产品失败: ${result.exceptionOrNull()?.message}")
                    _errorMessage.value = "无法更新产品: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                Log.e("ProductsViewModel", "更新产品时出错: ${e.message}")
                _errorMessage.value = "更新产品出错: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearSelectedProduct() {
        _selectedProduct.value = null
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    fun checkAndRefreshConfig() {
        // 用户从设置页面返回时调用此方法
        viewModelScope.launch {
            try {
                val config = settingsRepository.getWooCommerceConfig()
                if (config.isValid() && !_isConfigured.value) {
                    _isConfigured.value = true
                    // 加载分类
                    loadCategories()
                    
                    // 检查是否有选中的分类
                    if (_currentSelectedCategoryId.value != null) {
                        // 如果有选中的分类，则按分类过滤
                        filterProductsByCategory(_currentSelectedCategoryId.value)
                    } else {
                        // 否则加载所有产品
                        loadAllProducts()
                    }
                    
                    // 刷新数据但保持过滤状态，且不显示全屏加载
                    refreshData(showFullscreenLoading = false)
                } else if (!config.isValid() && _isConfigured.value) {
                    _isConfigured.value = false
                    _errorMessage.value = "WooCommerce API配置已更改，但无效"
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    // 忽略取消异常
                    Log.d("ProductsViewModel", "检查和刷新配置时协程已取消")
                    return@launch
                }
                
                Log.e("ProductsViewModel", "检查和刷新配置时出错: ${e.message}")
                // 不设置错误消息，避免频繁错误提示干扰用户
            }
        }
    }
    
    // 用于在状态卡住时进行紧急重置
    fun resetLoadingState() {
        Log.d("ProductsViewModel", "紧急重置加载状态")
        _isLoading.value = false
        _refreshing.value = false
        
        // 确保我们有一些数据显示
        viewModelScope.launch {
            try {
                // 检查是否有产品数据
                if (_products.value.isEmpty()) {
                    Log.d("ProductsViewModel", "产品列表为空，尝试从本地加载")
                    
                    // 尝试从当前分类加载
                    if (_currentSelectedCategoryId.value != null) {
                        // 检查缓存
                        val cacheKey = _currentSelectedCategoryId.value ?: ALL_PRODUCTS_KEY
                        val cachedData = categoryProductsCache[cacheKey]
                        if (cachedData != null) {
                            Log.d("ProductsViewModel", "使用缓存的分类产品")
                            _products.value = cachedData.first
                        } else {
                            // 从数据库加载
                            try {
                                val localProducts = productRepository.getProductsByCategory(_currentSelectedCategoryId.value!!)
                                if (localProducts.isNotEmpty()) {
                                    Log.d("ProductsViewModel", "从数据库加载分类产品")
                                    _products.value = localProducts
                                    // 更新缓存
                                    categoryProductsCache[cacheKey] = Pair(localProducts, System.currentTimeMillis())
                                }
                            } catch (e: Exception) {
                                Log.e("ProductsViewModel", "重置状态时加载分类产品失败: ${e.message}")
                            }
                        }
                    } else {
                        // 加载所有产品
                        try {
                            val localProducts = productRepository.getProducts()
                            if (localProducts.isNotEmpty()) {
                                Log.d("ProductsViewModel", "从数据库加载所有产品")
                                _products.value = localProducts
                                // 更新缓存
                                categoryProductsCache[ALL_PRODUCTS_KEY] = Pair(localProducts, System.currentTimeMillis())
                            }
                        } catch (e: Exception) {
                            Log.e("ProductsViewModel", "重置状态时加载所有产品失败: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ProductsViewModel", "重置状态时出错: ${e.message}")
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // 取消所有产品收集作业
        productsCollectionJob?.cancel()
        // 清空缓存
        categoryProductsCache.clear()
        lastAllProductsLoadTime = 0
        Log.d("ProductsViewModel", "ViewModel已清除，所有作业已取消")
    }
    
    // 取消所有产品相关协程
    private fun cancelAllProductJobs() {
        shouldCancelProductJobs = true
        viewModelScope.launch {
            delay(100) // 短暂延迟确保取消生效
            shouldCancelProductJobs = false
        }
    }
    
    /**
     * 重置加载状态，用于从潜在的错误状态中恢复
     */
    fun resetLoadingState() {
        // 重置加载状态
        _isLoading.value = false
        _refreshing.value = false
        
        // 记录重置操作
        Log.d("ProductsViewModel", "手动重置加载状态")
    }
    
    /**
     * 安全地执行操作，捕获任何异常并转换为错误状态
     */
    private fun safeExecute(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (e: Exception) {
                Log.e("ProductsViewModel", "操作执行失败: ${e.message}", e)
                _errorMessage.value = e.message ?: "未知错误"
                _isLoading.value = false
                _refreshing.value = false
            }
        }
    }
} 