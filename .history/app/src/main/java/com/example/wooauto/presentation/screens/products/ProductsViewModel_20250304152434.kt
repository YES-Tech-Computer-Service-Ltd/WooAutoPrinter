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
        viewModelScope.launch {
            try {
                Log.d("ProductsViewModel", "开始加载所有产品")
                productRepository.getAllProductsFlow().collectLatest { productsList ->
                    Log.d("ProductsViewModel", "已加载 ${productsList.size} 个产品")
                    _products.value = productsList
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e("ProductsViewModel", "加载产品时出错: ${e.message}")
                _errorMessage.value = "无法加载产品: ${e.message}"
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
        viewModelScope.launch {
            try {
                Log.d("ProductsViewModel", "正在按分类过滤产品: 分类ID=${categoryId ?: "全部"}")
                _isLoading.value = true
                
                // 存储当前选择的分类ID
                _currentSelectedCategoryId.value = categoryId
                
                if (categoryId == null) {
                    Log.d("ProductsViewModel", "加载所有产品")
                    loadAllProducts()
                } else {
                    Log.d("ProductsViewModel", "开始从仓库获取分类 $categoryId 的产品")
                    try {
                        // 先尝试刷新该分类的产品
                        val refreshResult = productRepository.refreshProducts(categoryId)
                        if (refreshResult.isSuccess) {
                            Log.d("ProductsViewModel", "成功刷新分类 $categoryId 的产品")
                            _products.value = refreshResult.getOrDefault(emptyList())
                            _isLoading.value = false
                        } else {
                            // 如果刷新失败，尝试从本地获取
                            Log.d("ProductsViewModel", "刷新失败，尝试从本地获取分类 $categoryId 的产品")
                            productRepository.getProductsByCategoryFlow(categoryId).collectLatest { filteredProducts ->
                                Log.d("ProductsViewModel", "从本地获取到分类 $categoryId 的产品: ${filteredProducts.size} 个")
                                _products.value = filteredProducts
                                _isLoading.value = false
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ProductsViewModel", "获取分类产品时出错: ${e.message}", e)
                        // 如果出错，尝试从内存中过滤
                        Log.d("ProductsViewModel", "尝试从内存中过滤分类 $categoryId 的产品")
                        val allProducts = _products.value
                        val filtered = allProducts.filter { product ->
                            product.categories.any { it.id == categoryId }
                        }
                        Log.d("ProductsViewModel", "内存过滤后得到 ${filtered.size} 个产品")
                        _products.value = filtered
                        _isLoading.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e("ProductsViewModel", "过滤产品时出错: ${e.message}", e)
                _errorMessage.value = "无法过滤产品: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun searchProducts(query: String) {
        viewModelScope.launch {
            try {
                Log.d("ProductsViewModel", "正在搜索产品: 关键词='$query'")
                _isLoading.value = true
                if (query.isEmpty()) {
                    loadAllProducts()
                } else {
                    productRepository.searchProductsFlow(query).collectLatest { searchResults ->
                        Log.d("ProductsViewModel", "搜索结果: 找到 ${searchResults.size} 个匹配产品")
                        _products.value = searchResults
                        _isLoading.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e("ProductsViewModel", "搜索产品时出错: ${e.message}")
                _errorMessage.value = "无法搜索产品: ${e.message}"
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
                // 加载产品
                loadAllProducts()
                loadCategories()
                refreshData()
            } else if (!config.isValid() && _isConfigured.value) {
                _isConfigured.value = false
                _errorMessage.value = "WooCommerce API配置已更改，但无效"
            }
        }
    }
} 