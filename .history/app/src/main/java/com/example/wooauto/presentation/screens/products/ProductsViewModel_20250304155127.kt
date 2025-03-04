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

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val wooCommerceConfig: WooCommerceConfig,
    private val productRepository: DomainProductRepository,
    private val settingsRepository: DomainSettingRepository
) : ViewModel() {
    
    private val _isConfigured = MutableStateFlow(false)
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
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
    private val categoryProductsCache = mutableMapOf<Long?, List<Product>>()
    
    // 新增：记录最后一次分类数据刷新时间，用于智能刷新策略
    private val lastCategoryRefreshTime = mutableMapOf<Long?, Long>()
    
    // 新增：智能刷新阈值（毫秒）- 30分钟内不重复刷新同一分类
    private val refreshThreshold = 30 * 60 * 1000L
    
    init {
        Log.d("ProductsViewModel", "初始化ProductsViewModel")
        checkConfiguration()
    }
    
    private fun checkConfiguration() {
        viewModelScope.launch {
            try {
                Log.d("ProductsViewModel", "正在检查API配置")
                val config = settingsRepository.getWooCommerceConfig()
                
                // 检查配置有效性
                if (!config.isValid()) {
                    Log.e("ProductsViewModel", "API配置无效: $config")
                    _isConfigured.value = false
                    _errorMessage.value = "WooCommerce API未正确配置，请在设置中检查"
                    _isLoading.value = false
                    return@launch
                }
                
                Log.d("ProductsViewModel", "API配置有效，尝试加载数据")
                _isConfigured.value = true
                
                // 首先加载分类
                loadCategories()
                
                // 只有在没有选择分类的情况下才加载所有产品
                if (_currentSelectedCategoryId.value == null) {
                    loadAllProducts()
                }
                
                // 刷新远程数据
                refreshData()
                
            } catch (e: Exception) {
                Log.e("ProductsViewModel", "检查配置时出错: ${e.message}")
                _isConfigured.value = false
                _isLoading.value = false
                _errorMessage.value = "无法检查API配置: ${e.message}"
            }
        }
    }
    
    private fun loadAllProducts() {
        // 取消之前的产品收集作业
        productsCollectionJob?.cancel()
        
        // 从缓存加载全部产品 - 立即显示，无延迟
        if (categoryProductsCache.containsKey(null)) {
            Log.d("ProductsViewModel", "立即从缓存加载全部产品")
            _products.value = categoryProductsCache[null] ?: emptyList()
            // 如果缓存很新，就直接返回，不加载
            val currentTime = System.currentTimeMillis()
            val lastRefresh = lastCategoryRefreshTime[null] ?: 0L
            if ((currentTime - lastRefresh) < refreshThreshold) {
                _isLoading.value = false
                return
            }
        }
        
        productsCollectionJob = viewModelScope.launch {
            try {
                Log.d("ProductsViewModel", "开始加载所有产品")
                _isLoading.value = true
                
                // 尝试使用协程但避免collectLatest，使用挂起函数直接获取数据
                try {
                    val productsList = productRepository.getProducts()
                    Log.d("ProductsViewModel", "已加载 ${productsList.size} 个产品")
                    
                    // 如果当前没有选择分类，才更新产品列表
                    if (_currentSelectedCategoryId.value == null) {
                        _products.value = productsList
                        
                        // 更新缓存
                        categoryProductsCache[null] = productsList
                        lastCategoryRefreshTime[null] = System.currentTimeMillis()
                    } else {
                        Log.d("ProductsViewModel", "已忽略全部产品加载，因为当前有分类过滤")
                    }
                } catch (e: Exception) {
                    // 忽略协程取消异常，这是正常的流程控制
                    if (e is kotlinx.coroutines.CancellationException) {
                        throw e
                    } else {
                        Log.e("ProductsViewModel", "加载产品时出错: ${e.message}")
                        _errorMessage.value = "无法加载产品: ${e.message}"
                    }
                } finally {
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d("ProductsViewModel", "产品加载协程已取消")
                } else {
                    Log.e("ProductsViewModel", "加载所有产品时出错: ${e.message}")
                    _errorMessage.value = "无法加载产品: ${e.message}"
                }
                _isLoading.value = false
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
    
    fun refreshData() {
        viewModelScope.launch {
            try {
                Log.d("ProductsViewModel", "开始刷新产品数据")
                _isLoading.value = true
                _errorMessage.value = null // 清除任何现有错误
                _refreshing.value = true
                
                // 清除产品缓存
                Log.d("ProductsViewModel", "清除产品缓存")
                productRepository.clearCache()
                
                // 刷新产品
                Log.d("ProductsViewModel", "调用refreshProducts()")
                val success = productRepository.refreshProducts()
                if (success) {
                    Log.d("ProductsViewModel", "刷新产品成功")
                    
                    // 刷新分类
                    Log.d("ProductsViewModel", "开始刷新分类数据")
                    val categoriesSuccess = productRepository.refreshCategories()
                    if (categoriesSuccess) {
                        Log.d("ProductsViewModel", "刷新分类成功")
                    } else {
                        Log.e("ProductsViewModel", "刷新分类失败")
                    }
                    
                    // 加载分类列表
                    loadCategories()
                    
                    // 检查是否有选中的分类，如果有则重新应用过滤
                    val currentSelectedCategory = _currentSelectedCategoryId.value
                    if (currentSelectedCategory != null) {
                        Log.d("ProductsViewModel", "重新应用分类过滤: ${currentSelectedCategory}")
                        filterProductsByCategory(currentSelectedCategory)
                    } else {
                        // 只有在没有选中分类的情况下才加载所有产品
                        loadAllProducts()
                    }
                } else {
                    Log.e("ProductsViewModel", "刷新产品失败")
                    // 仍然尝试加载分类，即使产品刷新失败
                    loadCategories()
                    _errorMessage.value = "无法获取产品。请检查您的网络连接和API配置。"
                }
            } catch (e: Exception) {
                Log.e("ProductsViewModel", "刷新数据时发生错误: ${e.message}", e)
                _errorMessage.value = "刷新数据时发生错误: ${e.message}"
                
                // 检查是否是API认证错误
                if (e.message?.contains("Authentication failed") == true || 
                    e.message?.contains("401") == true) {
                    _errorMessage.value = "API认证失败。请检查您的Consumer Key和Consumer Secret。"
                }
            } finally {
                _isLoading.value = false
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
                
                // 标记为加载中，但只有在没有缓存时才设置完全加载状态
                val hasCachedData = categoryProductsCache.containsKey(categoryId)
                
                // 存储当前选择的分类ID
                _currentSelectedCategoryId.value = categoryId
                
                // 优先从缓存加载 - 立即显示，无延迟
                if (hasCachedData) {
                    Log.d("ProductsViewModel", "立即从缓存加载分类 ${categoryId ?: "全部"} 的产品")
                    _products.value = categoryProductsCache[categoryId] ?: emptyList()
                    // 如果有缓存数据，则不标记为加载中，避免UI闪烁
                } else {
                    // 如果没有缓存，则设置加载状态
                    _isLoading.value = true
                }
                
                if (categoryId == null) {
                    Log.d("ProductsViewModel", "加载所有产品")
                    // 如果没有缓存，或者缓存很旧，才加载所有产品
                    val shouldRefresh = !hasCachedData || 
                            (System.currentTimeMillis() - (lastCategoryRefreshTime[categoryId] ?: 0L)) > refreshThreshold
                    
                    if (shouldRefresh) {
                        loadAllProducts()
                    } else {
                        // 如果刚从缓存加载了数据，就不需要再加载了
                        _isLoading.value = false
                    }
                } else {
                    // 判断是否需要加载新数据
                    val currentTime = System.currentTimeMillis()
                    val lastRefresh = lastCategoryRefreshTime[categoryId] ?: 0L
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
                                    categoryProductsCache[categoryId] = productsForCategory
                                    lastCategoryRefreshTime[categoryId] = currentTime
                                    
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
                                categoryProductsCache[categoryId] = filteredProducts
                                lastCategoryRefreshTime[categoryId] = currentTime
                                
                                _products.value = filteredProducts
                            }
                            
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) {
                                throw e // 协程取消异常需要向上传播
                            } else {
                                Log.e("ProductsViewModel", "获取分类产品时出错: ${e.message}", e)
                                // 如果出错，尝试从内存中过滤
                                if (!hasCachedData) {
                                    handleInMemoryFiltering(categoryId)
                                }
                                _errorMessage.value = "无法获取分类产品: ${e.message}"
                            }
                        }
                    } else {
                        Log.d("ProductsViewModel", "使用现有缓存的分类 $categoryId 产品数据，无需加载")
                    }
                }
            } catch (e: Exception) {
                // 忽略协程取消异常
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d("ProductsViewModel", "过滤产品协程已取消")
                } else {
                    Log.e("ProductsViewModel", "过滤产品时出错: ${e.message}", e)
                    _errorMessage.value = "无法过滤产品: ${e.message}"
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
                                Log.d("ProductsViewModel", "搜索产品协程已取消")
                            } else {
                                Log.e("ProductsViewModel", "搜索产品时出错: ${e.message}", e)
                                _errorMessage.value = "无法搜索产品: ${e.message}"
                            }
                            _isLoading.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                // 忽略协程取消异常
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d("ProductsViewModel", "搜索产品协程已取消")
                } else {
                    Log.e("ProductsViewModel", "搜索产品时出错: ${e.message}")
                    _errorMessage.value = "无法搜索产品: ${e.message}"
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
                
                // 刷新数据但保持过滤状态
                refreshData()
            } else if (!config.isValid() && _isConfigured.value) {
                _isConfigured.value = false
                _errorMessage.value = "WooCommerce API配置已更改，但无效"
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
                    val cachedProducts = productRepository.getProducts()
                    if (cachedProducts.isNotEmpty()) {
                        Log.d("ProductsViewModel", "从缓存加载了 ${cachedProducts.size} 个产品")
                        _products.value = cachedProducts
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
        lastCategoryRefreshTime.clear()
        Log.d("ProductsViewModel", "ViewModel已清除，所有作业已取消")
    }
} 