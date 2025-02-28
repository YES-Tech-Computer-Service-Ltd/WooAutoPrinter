package com.wooauto.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wooauto.domain.models.Order
import com.wooauto.domain.usecases.orders.GetOrdersUseCase
import com.wooauto.domain.usecases.orders.ManageOrderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

/**
 * 订单列表页面的状态
 */
data class OrdersUiState(
    val orders: List<Order> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedStatus: String? = null,
    val selectedDeliveryMethod: String? = null,
    val selectedDate: Date? = null,
    val searchQuery: String = ""
)

/**
 * 订单列表页面的 ViewModel
 */
@HiltViewModel
class OrderViewModel @Inject constructor(
    private val getOrdersUseCase: GetOrdersUseCase,
    private val manageOrderUseCase: ManageOrderUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    private val _selectedStatus = MutableStateFlow<String?>(null)
    private val _selectedDeliveryMethod = MutableStateFlow<String?>(null)
    private val _selectedDate = MutableStateFlow<Date?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            combine(
                _selectedStatus,
                _isRefreshing,
                _error
            ) { status, isRefreshing, error ->
                Triple(status, isRefreshing, error)
            }.collect { (status, isRefreshing, error) ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = isRefreshing,
                        error = error,
                        selectedStatus = status
                    )
                }
            }
        }

        // 监听订单数据流
        viewModelScope.launch {
            _selectedStatus
                .flatMapLatest { status ->
                    if (status != null) {
                        getOrdersUseCase.getOrdersByStatusFlow(status)
                    } else {
                        getOrdersUseCase.getAllOrdersFlow()
                    }
                }
                .catch { e ->
                    _error.value = e.message
                }
                .collect { orders ->
                    _uiState.update { it.copy(orders = orders) }
                }
        }
    }

    /**
     * 刷新订单列表
     */
    fun refreshOrders() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                getOrdersUseCase.refreshOrders(_selectedStatus.value)
                    .onSuccess {
                        _error.value = null
                    }
                    .onFailure { e ->
                        _error.value = e.message
                    }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * 更新订单状态
     */
    fun updateOrderStatus(orderId: Long, newStatus: String) {
        viewModelScope.launch {
            try {
                manageOrderUseCase.updateOrderStatus(orderId, newStatus)
                    .onSuccess {
                        // 状态更新成功后刷新订单列表
                        refreshOrders()
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "更新订单状态失败")
                }
            }
        }
    }

    /**
     * 设置状态过滤器
     */
    fun setStatusFilter(status: String?) {
        _selectedStatus.value = status
        _uiState.update { it.copy(selectedStatus = status) }
    }

    /**
     * 设置配送方式过滤器
     */
    fun setDeliveryMethodFilter(method: String?) {
        _selectedDeliveryMethod.value = method
        _uiState.update { it.copy(selectedDeliveryMethod = method) }
    }

    /**
     * 设置日期过滤器
     */
    fun setDateFilter(date: Date?) {
        _selectedDate.value = date
        _uiState.update { it.copy(selectedDate = date) }
    }

    /**
     * 设置搜索关键词
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * 判断两个日期是否是同一天
     */
    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }
} 