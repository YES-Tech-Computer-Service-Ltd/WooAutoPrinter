package com.wooauto.presentation.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wooauto.domain.models.Order
import com.wooauto.domain.usecases.orders.ManageOrderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 订单详情页面的状态
 */
data class OrderDetailsUiState(
    val order: Order? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * 订单详情页面的 ViewModel
 */
@HiltViewModel
class OrderDetailsViewModel @Inject constructor(
    private val manageOrderUseCase: ManageOrderUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val orderId: Long = checkNotNull(savedStateHandle["orderId"])
    
    private val _uiState = MutableStateFlow(OrderDetailsUiState(isLoading = true))
    val uiState: StateFlow<OrderDetailsUiState> = _uiState.asStateFlow()

    init {
        loadOrderDetails()
    }

    /**
     * 加载订单详情
     */
    private fun loadOrderDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val order = manageOrderUseCase.getOrderById(orderId)
                if (order != null) {
                    _uiState.update {
                        it.copy(
                            order = order,
                            isLoading = false,
                            error = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "找不到订单"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "加载订单详情失败"
                    )
                }
            }
        }
    }

    /**
     * 更新订单状态
     */
    fun updateOrderStatus(newStatus: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                manageOrderUseCase.updateOrderStatus(orderId, newStatus)
                    .onSuccess { updatedOrder ->
                        _uiState.update {
                            it.copy(
                                order = updatedOrder,
                                isLoading = false,
                                error = null
                            )
                        }
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "更新订单状态失败"
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "更新订单状态失败"
                    )
                }
            }
        }
    }

    /**
     * 标记订单为已打印
     */
    fun markOrderAsPrinted() {
        viewModelScope.launch {
            try {
                manageOrderUseCase.markOrderAsPrinted(orderId)
                    .onSuccess {
                        // 重新加载订单详情以获取最新状态
                        loadOrderDetails()
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "标记订单为已打印失败")
                }
            }
        }
    }

    /**
     * 标记订单通知为已显示
     */
    fun markOrderNotificationShown() {
        viewModelScope.launch {
            try {
                manageOrderUseCase.markOrderNotificationShown(orderId)
                    .onSuccess {
                        // 重新加载订单详情以获取最新状态
                        loadOrderDetails()
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "标记订单通知为已显示失败")
                }
            }
        }
    }

    /**
     * 刷新订单详情
     */
    fun refreshOrderDetails() {
        loadOrderDetails()
    }
} 