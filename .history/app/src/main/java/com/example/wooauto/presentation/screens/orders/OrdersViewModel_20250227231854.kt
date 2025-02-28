package com.example.wooauto.presentation.screens.orders

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.data.local.WooCommerceConfig
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.repositories.DomainOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val wooCommerceConfig: WooCommerceConfig,
    private val orderRepository: DomainOrderRepository
) : ViewModel() {

    private val _isConfigured = MutableStateFlow(false)
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _selectedOrder = MutableStateFlow<Order?>(null)
    val selectedOrder: StateFlow<Order?> = _selectedOrder.asStateFlow()
    
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        Log.d("OrdersViewModel", "初始化订单视图模型")
        checkConfiguration()
        observeOrders()
    }

    private fun checkConfiguration() {
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "正在检查API配置")
                wooCommerceConfig.isConfigured.collectLatest { configured ->
                    Log.d("OrdersViewModel", "API配置状态: $configured")
                    _isConfigured.value = configured
                    if (configured) {
                        refreshOrders()
                    } else {
                        _isLoading.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "检查配置时出错: ${e.message}")
                _isConfigured.value = false
                _isLoading.value = false
                _errorMessage.value = "无法检查API配置: ${e.message}"
            }
        }
    }
    
    private fun observeOrders() {
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "开始观察订单数据流")
                orderRepository.getAllOrdersFlow().collectLatest { ordersList ->
                    Log.d("OrdersViewModel", "收到${ordersList.size}个订单")
                    _orders.value = ordersList
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "观察订单时出错: ${e.message}")
                _errorMessage.value = "无法加载订单: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun refreshOrders() {
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "正在刷新订单数据")
                _refreshing.value = true
                val result = orderRepository.refreshOrders()
                if (result.isSuccess) {
                    Log.d("OrdersViewModel", "成功刷新订单数据")
                    _errorMessage.value = null
                } else {
                    Log.e("OrdersViewModel", "刷新订单数据失败: ${result.exceptionOrNull()?.message}")
                    _errorMessage.value = "刷新数据失败: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "刷新订单时出错: ${e.message}")
                _errorMessage.value = "刷新数据出错: ${e.message}"
            } finally {
                _refreshing.value = false
                _isLoading.value = false
            }
        }
    }
    
    fun filterOrdersByStatus(status: String?) {
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "正在按状态过滤订单: $status")
                _isLoading.value = true
                if (status.isNullOrEmpty()) {
                    observeOrders()
                } else {
                    orderRepository.getOrdersByStatusFlow(status).collectLatest { filteredOrders ->
                        Log.d("OrdersViewModel", "过滤后得到${filteredOrders.size}个订单")
                        _orders.value = filteredOrders
                        _isLoading.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "过滤订单时出错: ${e.message}")
                _errorMessage.value = "无法过滤订单: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    fun getOrderDetails(orderId: Long) {
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "正在获取订单详情: $orderId")
                val order = orderRepository.getOrderById(orderId)
                _selectedOrder.value = order
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "获取订单详情时出错: ${e.message}")
                _errorMessage.value = "无法获取订单详情: ${e.message}"
            }
        }
    }
    
    fun updateOrderStatus(orderId: Long, newStatus: String) {
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "正在更新订单状态: $orderId -> $newStatus")
                _isLoading.value = true
                val result = orderRepository.updateOrderStatus(orderId, newStatus)
                if (result.isSuccess) {
                    Log.d("OrdersViewModel", "成功更新订单状态")
                    _selectedOrder.value = result.getOrNull()
                    refreshOrders()
                } else {
                    Log.e("OrdersViewModel", "更新订单状态失败: ${result.exceptionOrNull()?.message}")
                    _errorMessage.value = "无法更新订单状态: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "更新订单状态时出错: ${e.message}")
                _errorMessage.value = "更新订单状态出错: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun markOrderAsPrinted(orderId: Long) {
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "正在标记订单为已打印: $orderId")
                orderRepository.markOrderAsPrinted(orderId)
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "标记订单为已打印时出错: ${e.message}")
                _errorMessage.value = "无法标记订单为已打印: ${e.message}"
            }
        }
    }
    
    fun clearSelectedOrder() {
        _selectedOrder.value = null
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
} 