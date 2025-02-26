package com.example.wooauto.ui.products

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.data.api.RetrofitClient
import com.example.wooauto.data.database.AppDatabase
import com.example.wooauto.data.database.entities.ProductEntity
import com.example.wooauto.data.repositories.ProductRepository
import com.example.wooauto.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.emit
import kotlinx.coroutines.flow.collect

class ProductViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)

    // Repository instance
    private lateinit var productRepository: ProductRepository

    // UI states
    private val _uiState = MutableStateFlow<ProductsUiState>(ProductsUiState.Loading)
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId = _selectedCategoryId.asStateFlow()

    private val _categories = MutableStateFlow<List<CategoryItem>>(emptyList())
    val categories = _categories.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    // Product detail state
    private val _productDetailState = MutableStateFlow<ProductDetailState>(ProductDetailState.Loading)
    val productDetailState: StateFlow<ProductDetailState> = _productDetailState.asStateFlow()

    // Edit product state
    private val _editProductState = MutableStateFlow<EditProductState>(EditProductState.Idle)
    val editProductState: StateFlow<EditProductState> = _editProductState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                // Initialize repository with API credentials
                val websiteUrl = preferencesManager.websiteUrl.first()
                val apiKey = preferencesManager.apiKey.first()
                val apiSecret = preferencesManager.apiSecret.first()

                if (websiteUrl.isNotEmpty() && apiKey.isNotEmpty() && apiSecret.isNotEmpty()) {
                    val apiService = RetrofitClient.getWooCommerceApiService(websiteUrl)
                    val productDao = AppDatabase.getInstance(getApplication()).productDao()
                    productRepository = ProductRepository(
                        productDao,
                        apiService,
                        apiKey,
                        apiSecret
                    )

                    // Load categories
                    loadCategories()

                    // Observe products from database based on filters
                    combine(
                        _searchQuery,
                        _selectedCategoryId,
                        getProductsFlow()
                    ) { query, categoryId, products ->
                        val filteredProducts = products.filter { product ->
                            val matchesQuery = if (query.isBlank()) {
                                true
                            } else {
                                product.name.contains(query, ignoreCase = true) ||
                                product.sku.contains(query, ignoreCase = true)
                            }
                            
                            val matchesCategory = if (categoryId == null) {
                                true
                            } else {
                                product.categoryIds.contains(categoryId)
                            }
                            
                            matchesQuery && matchesCategory
                        }
                        
                        if (filteredProducts.isEmpty() && !_isRefreshing.value) {
                            ProductsUiState.Empty
                        } else {
                            ProductsUiState.Success(filteredProducts)
                        }
                    }.catch { e ->
                        Log.e("ProductViewModel", "Error observing products", e)
                        emit(ProductsUiState.Error(e.message ?: "Unknown error"))
                    }.collect { state ->
                        _uiState.value = state
                    }

                    // Initial data load
                    refreshProducts()
                } else {
                    _uiState.value =
                        ProductsUiState.Error("API configuration missing. Please configure in settings.")
                }
            } catch (e: Exception) {
                Log.e("ProductViewModel", "Error initializing ProductViewModel", e)
                _uiState.value = ProductsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun getProductsFlow() = when {
        _searchQuery.value.isNotEmpty() -> productRepository.searchProductsFlow(_searchQuery.value)
        _selectedCategoryId.value != null -> productRepository.getProductsByCategoryFlow(_selectedCategoryId.value!!)
        else -> productRepository.getAllProductsFlow()
    }

    /**
     * Load categories from the repository
     */
    private suspend fun loadCategories() {
        try {
            val categoriesFromRepo = productRepository.getAllCategories()

            // Convert to CategoryItem list with "All" as first option
            val categoryItems = mutableListOf<CategoryItem>()
            categoryItems.add(CategoryItem(null, "All Categories"))

            categoriesFromRepo.forEach { (id, name) ->
                categoryItems.add(CategoryItem(id, name))
            }

            _categories.value = categoryItems
        } catch (e: Exception) {
            Log.e("ProductViewModel", "Error loading categories", e)
        }
    }

    /**
     * Update search query
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Update category filter
     */
    fun updateCategoryFilter(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    /**
     * Refresh products from the API
     */
    fun refreshProducts() {
        viewModelScope.launch {
            try {
                if (!::productRepository.isInitialized) {
                    _uiState.value = ProductsUiState.Error("请先在设置中配置API凭证")
                    return@launch
                }
                _isRefreshing.value = true
                val result = productRepository.refreshProducts(_selectedCategoryId.value)
                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    Log.e("ProductViewModel", "Error refreshing products", error)
                }
            } catch (e: Exception) {
                Log.e("ProductViewModel", "Error refreshing products", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Load product details
     */
    fun loadProductDetails(productId: Long) {
        viewModelScope.launch {
            _productDetailState.value = ProductDetailState.Loading

            try {
                // Try to get from local database first
                val localProduct = productRepository.getProductById(productId)

                if (localProduct != null) {
                    _productDetailState.value = ProductDetailState.Success(localProduct)
                }

                // Then refresh from API to get the latest data
                val result = productRepository.getProduct(productId)

                if (result.isSuccess) {
                    // API product available, update UI with latest data
                    val product = result.getOrNull()
                    if (product != null) {
                        _productDetailState.value = ProductDetailState.Success(
                            ProductEntity(
                                id = product.id,
                                name = product.name,
                                status = product.status,
                                description = product.description,
                                shortDescription = product.shortDescription,
                                sku = product.sku,
                                price = product.price,
                                regularPrice = product.regularPrice,
                                salePrice = product.salePrice,
                                onSale = product.onSale,
                                stockQuantity = product.stockQuantity,
                                stockStatus = product.stockStatus,
                                categoryIds = product.categories.map { it.id },
                                categoryNames = product.categories.map { it.name },
                                imageUrl = product.images.firstOrNull()?.src
                            )
                        )
                    }
                } else {
                    // If we don't have the product locally either, show error
                    if (localProduct == null) {
                        _productDetailState.value = ProductDetailState.Error(
                            result.exceptionOrNull()?.message ?: "Failed to load product"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("ProductViewModel", "Error loading product details", e)
                _productDetailState.value = ProductDetailState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Update product stock
     */
    fun updateProductStock(productId: Long, newStockQuantity: Int?) {
        viewModelScope.launch {
            _editProductState.value = EditProductState.Updating

            try {
                val result = productRepository.updateProductStock(productId, newStockQuantity)
                if (result.isSuccess) {
                    _editProductState.value = EditProductState.Success

                    // Refresh product details to show updated stock
                    loadProductDetails(productId)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to update stock"
                    _editProductState.value = EditProductState.Error(error)
                    Log.e("ProductViewModel", "Error updating product stock", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e("ProductViewModel", "Error updating product stock", e)
                _editProductState.value = EditProductState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Update product prices
     */
    fun updateProductPrices(productId: Long, regularPrice: String, salePrice: String) {
        viewModelScope.launch {
            _editProductState.value = EditProductState.Updating

            try {
                val result = productRepository.updateProductPrices(productId, regularPrice, salePrice)
                if (result.isSuccess) {
                    _editProductState.value = EditProductState.Success

                    // Refresh product details to show updated prices
                    loadProductDetails(productId)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to update prices"
                    _editProductState.value = EditProductState.Error(error)
                    Log.e("ProductViewModel", "Error updating product prices", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e("ProductViewModel", "Error updating product prices", e)
                _editProductState.value = EditProductState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetEditState() {
        _editProductState.value = EditProductState.Idle
    }
}

// UI States
sealed class ProductsUiState {
    data object Loading : ProductsUiState()
    data object Empty : ProductsUiState()
    data class Success(val products: List<ProductEntity>) : ProductsUiState()
    data class Error(val message: String) : ProductsUiState()
}

sealed class ProductDetailState {
    data object Loading : ProductDetailState()
    data class Success(val product: ProductEntity) : ProductDetailState()
    data class Error(val message: String) : ProductDetailState()
}

sealed class EditProductState {
    data object Idle : EditProductState()
    data object Updating : EditProductState()
    data object Success : EditProductState()
    data class Error(val message: String) : EditProductState()
}

data class CategoryItem(
    val id: Long?,
    val name: String
)