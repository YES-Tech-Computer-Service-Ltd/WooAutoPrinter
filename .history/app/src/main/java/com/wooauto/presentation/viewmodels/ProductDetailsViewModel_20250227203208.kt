package com.wooauto.presentation.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wooauto.domain.models.Product
import com.wooauto.domain.repositories.DomainProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 产品详情页面的状态
 */
data class ProductDetailsUiState(
    val product: Product? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEditDialogVisible: Boolean = false,
    val successMessage: String? = null
)

/**
 * 产品详情页面的 ViewModel
 */
@HiltViewModel
class ProductDetailsViewModel @Inject constructor(
    private val productRepository: DomainProductRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val productId: Long = checkNotNull(savedStateHandle["productId"].toString().toLong())
    
    private val _uiState = MutableStateFlow(ProductDetailsUiState(isLoading = true))
    val uiState: StateFlow<ProductDetailsUiState> = _uiState.asStateFlow()

    init {
        refreshProductDetails()
    }

    /**
     * 加载产品详情
     */
    fun refreshProductDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                productRepository.getProduct(productId)
                    .onSuccess { product ->
                        _uiState.update { 
                            it.copy(
                                product = product,
                                isLoading = false,
                                error = null
                            )
                        }
                    }
                    .onFailure { error ->
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "加载产品详情失败"
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "加载产品详情失败"
                    )
                }
            }
        }
    }

    /**
     * 更新产品
     */
    fun updateProduct(product: Product) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                productRepository.updateProduct(product)
                    .onSuccess { updatedProduct ->
                        _uiState.update { 
                            it.copy(
                                product = updatedProduct,
                                isLoading = false,
                                error = null
                            )
                        }
                    }
                    .onFailure { error ->
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "更新产品失败"
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "更新产品失败"
                    )
                }
            }
        }
    }

    /**
     * 更新产品价格
     */
    fun updateProductPrices(regularPrice: String, salePrice: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                productRepository.updateProductPrices(productId, regularPrice, salePrice ?: "")
                    .onSuccess {
                        refreshProductDetails()
                    }
                    .onFailure { error ->
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "更新产品价格失败"
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "更新产品价格失败"
                    )
                }
            }
        }
    }

    /**
     * 更新产品库存
     */
    fun updateProductStock(stockQuantity: Int?) {
        viewModelScope.launch {
            try {
                productRepository.updateProductStock(productId, stockQuantity)
                    .onSuccess {
                        refreshProductDetails() // 重新加载产品详情
                        _uiState.update {
                            it.copy(successMessage = "库存更新成功")
                        }
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(error = error.message ?: "更新库存失败")
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "更新库存失败")
                }
            }
        }
    }

    /**
     * 显示编辑对话框
     */
    fun showEditDialog() {
        _uiState.update { it.copy(isEditDialogVisible = true) }
    }

    /**
     * 隐藏编辑对话框
     */
    fun hideEditDialog() {
        _uiState.update { it.copy(isEditDialogVisible = false) }
    }

    /**
     * 清除成功消息
     */
    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
} 