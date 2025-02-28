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
                
                // 加载产品和分类
                loadAllProducts()
                loadCategories()
                
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
                Log.d("ProductsViewModel", "正在刷新产品数据")
                _refreshing.value = true
                
                // 清除错误消息
                _errorMessage.value = null
                
                // 先清除缓存
                productRepository.clearCache()
                
                val result = productRepository.refreshProducts(null)
                if (result.isSuccess) {
                    Log.d("ProductsViewModel", "成功刷新产品数据")
                    // 刷新分类
                    loadCategories()
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("ProductsViewModel", "刷新产品数据失败: ${error?.message}")
                    
                    // 特别处理API认证错误
                    if (error?.message?.contains("401") == true || 
                        error?.message?.contains("认证") == true || 
                        error?.message?.contains("API") == true) {
                        _errorMessage.value = "API认证失败，请检查您的站点URL、Consumer Key和Secret是否正确"
                    } else {
                        _errorMessage.value = "刷新数据失败: ${error?.message}"
                    }
                }
            } catch (e: Exception) {
                Log.e("ProductsViewModel", "刷新产品时出错: ${e.message}")
                _errorMessage.value = "刷新数据出错: ${e.message}"
            } finally {
                _refreshing.value = false
                _isLoading.value = false
            }
        }
    }
    
    fun filterProductsByCategory(categoryId: Long?) {
        viewModelScope.launch {
            try {
                Log.d("ProductsViewModel", "正在按分类过滤产品: $categoryId")
                _isLoading.value = true
                if (categoryId == null) {
                    loadAllProducts()
                } else {
                    productRepository.getProductsByCategoryFlow(categoryId).collectLatest { filteredProducts ->
                        Log.d("ProductsViewModel", "过滤后得到 ${filteredProducts.size} 个产品")
                        _products.value = filteredProducts
                        _isLoading.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e("ProductsViewModel", "过滤产品时出错: ${e.message}")
                _errorMessage.value = "无法过滤产品: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun searchProducts(query: String) {
        viewModelScope.launch {
            try {
                Log.d("ProductsViewModel", "正在搜索产品: $query")
                _isLoading.value = true
                if (query.isEmpty()) {
                    loadAllProducts()
                } else {
                    productRepository.searchProductsFlow(query).collectLatest { searchResults ->
                        Log.d("ProductsViewModel", "搜索到 ${searchResults.size} 个产品")
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
                Log.d("ProductsViewModel", "正在更新产品: ${product.id} - ${product.name}")
                _isLoading.value = true
                val result = productRepository.updateProduct(product)
                if (result.isSuccess) {
                    Log.d("ProductsViewModel", "成功更新产品")
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