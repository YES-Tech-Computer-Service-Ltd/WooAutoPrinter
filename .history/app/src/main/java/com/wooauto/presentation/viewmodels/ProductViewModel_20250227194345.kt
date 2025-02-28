package com.wooauto.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wooauto.domain.models.Product
import com.wooauto.domain.repositories.DomainProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 产品列表页面的状态
 */
data class ProductsUiState(
    val products: List<Product> = emptyList(),
    val categories: List<Pair<Long, String>> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedCategory: Long? = null,
    val searchQuery: String = ""
)

/**
 * 产品列表页面的 ViewModel
 */
@HiltViewModel
class ProductViewModel @Inject constructor(
    private val productRepository: DomainProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductsUiState())
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    private val _selectedCategory = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")

    init {
        // 加载类别列表
        loadCategories()

        // 合并所有过滤条件的Flow，并根据条件获取产品
        combine(
            _selectedCategory,
            _searchQuery
        ) { category, query ->
            loadProducts(category, query)
        }.launchIn(viewModelScope)
    }

    /**
     * 加载产品类别
     */
    private fun loadCategories() {
        viewModelScope.launch {
            try {
                val categories = productRepository.getAllCategories()
                _uiState.update { it.copy(categories = categories) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "加载类别失败")
                }
            }
        }
    }

    /**
     * 加载产品列表
     */
    private fun loadProducts(categoryId: Long?, query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // 根据类别获取产品Flow
                val productsFlow = if (categoryId != null) {
                    productRepository.getProductsByCategoryFlow(categoryId)
                } else if (query.isNotEmpty()) {
                    productRepository.searchProductsFlow(query)
                } else {
                    productRepository.getAllProductsFlow()
                }

                // 收集产品Flow
                productsFlow.collect { products ->
                    _uiState.update {
                        it.copy(
                            products = products,
                            isLoading = false,
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "加载产品失败"
                    )
                }
            }
        }
    }

    /**
     * 刷新产品列表
     */
    fun refreshProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val result = productRepository.refreshProducts(_selectedCategory.value)
                result.onSuccess { products ->
                    _uiState.update {
                        it.copy(isLoading = false, error = null)
                    }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "刷新产品失败"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "刷新产品失败"
                    )
                }
            }
        }
    }

    /**
     * 设置类别过滤器
     */
    fun setCategoryFilter(categoryId: Long?) {
        _selectedCategory.value = categoryId
        _uiState.update { it.copy(selectedCategory = categoryId) }
    }

    /**
     * 设置搜索关键词
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }
} 