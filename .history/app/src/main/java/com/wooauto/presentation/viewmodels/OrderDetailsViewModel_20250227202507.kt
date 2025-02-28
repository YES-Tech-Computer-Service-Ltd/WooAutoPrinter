package com.wooauto.presentation.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wooauto.domain.models.Order
import com.wooauto.domain.repositories.DomainOrderRepository
import com.wooauto.domain.usecases.orders.ManageOrderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class OrderDetailsUiState {
    object Loading : OrderDetailsUiState()
    data class Success(val order: Order) : OrderDetailsUiState()
    data class Error(val message: String) : OrderDetailsUiState()
}

/**
 * 订单详情页面的 ViewModel
 */
@HiltViewModel
class OrderDetailsViewModel @Inject constructor(
    private val orderRepository: DomainOrderRepository,
    private val manageOrderUseCase: ManageOrderUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val orderId: Long = checkNotNull(savedStateHandle["orderId"])
    
    private val _uiState = MutableStateFlow<OrderDetailsUiState>(OrderDetailsUiState.Loading)
    val uiState: StateFlow<OrderDetailsUiState> = _uiState.asStateFlow()

    init {
        loadOrderDetails()
    }

    /**
     * 加载订单详情
     */
    fun loadOrderDetails() {
        viewModelScope.launch {
            _uiState.value = OrderDetailsUiState.Loading

            try {
                val order = orderRepository.getOrderById(orderId)
                if (order != null) {
                    _uiState.value = OrderDetailsUiState.Success(order)
                } else {
                    _uiState.value = OrderDetailsUiState.Error("找不到订单")
                }
            } catch (e: Exception) {
                _uiState.value = OrderDetailsUiState.Error(e.message ?: "加载订单详情失败")
            }
        }
    }

    /**
     * 更新订单状态
     */
    fun updateOrderStatus(newStatus: String) {
        viewModelScope.launch {
            _uiState.value = OrderDetailsUiState.Loading

            try {
                manageOrderUseCase.updateOrderStatus(orderId, newStatus)
                    .onSuccess { updatedOrder ->
                        _uiState.value = OrderDetailsUiState.Success(updatedOrder)
                    }
                    .onFailure { error ->
                        _uiState.value = OrderDetailsUiState.Error(error.message ?: "更新订单状态失败")
                    }
            } catch (e: Exception) {
                _uiState.value = OrderDetailsUiState.Error(e.message ?: "更新订单状态失败")
            }
        }
    }

    /**
     * 标记订单为已完成
     */
    fun markOrderAsComplete() {
        viewModelScope.launch {
            _uiState.value = OrderDetailsUiState.Loading
            try {
                manageOrderUseCase.updateOrderStatus(orderId, "completed")
                    .onSuccess { updatedOrder ->
                        _uiState.value = OrderDetailsUiState.Success(updatedOrder)
                    }
                    .onFailure { error ->
                        _uiState.value = OrderDetailsUiState.Error(error.message ?: "更新订单状态失败")
                    }
            } catch (e: Exception) {
                _uiState.value = OrderDetailsUiState.Error(e.message ?: "更新订单状态失败")
            }
        }
    }

    /**
     * 打印订单
     */
    fun printOrder() {
        viewModelScope.launch {
            try {
                manageOrderUseCase.markOrderAsPrinted(orderId)
                    .onSuccess {
                        // 重新加载订单详情以获取最新状态
                        loadOrderDetails()
                    }
            } catch (e: Exception) {
                _uiState.value = OrderDetailsUiState.Error(e.message ?: "打印订单失败")
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
                _uiState.value = OrderDetailsUiState.Error(e.message ?: "标记订单通知为已显示失败")
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