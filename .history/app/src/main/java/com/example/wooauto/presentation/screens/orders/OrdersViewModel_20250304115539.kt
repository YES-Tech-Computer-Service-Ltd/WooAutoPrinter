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
import kotlinx.coroutines.flow.first
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
    
    // 当前选中的状态过滤条件
    private val _currentStatusFilter = MutableStateFlow<String?>(null)
    private val currentStatusFilter: StateFlow<String?> = _currentStatusFilter.asStateFlow()

    init {
        Log.d("OrdersViewModel", "初始化订单视图模型")
        checkConfiguration()
        observeOrders()
    }

    private fun checkConfiguration() {
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "正在检查API配置")
                val configured = wooCommerceConfig.isConfigured.first()
                Log.d("OrdersViewModel", "API配置状态: $configured")
                _isConfigured.value = configured
                if (configured) {
                    refreshOrders()
                } else {
                    _isLoading.value = false
                }
                
                // 监听配置变化
                viewModelScope.launch {
                    wooCommerceConfig.isConfigured.collectLatest { configured ->
                        Log.d("OrdersViewModel", "API配置状态变更: $configured")
                        _isConfigured.value = configured
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
                Log.d("OrdersViewModel", "【订单调试】开始刷新订单数据，当前状态过滤: ${_currentStatusFilter.value}")
                _refreshing.value = true
                _errorMessage.value = null // 清除任何现有错误
                
                // 检查API配置
                val config = wooCommerceConfig
                // 使用first()而不是collect，避免长时间阻塞
                val configured = config.isConfigured.first()
                if (!configured) {
                    Log.e("OrdersViewModel", "【订单调试】API未配置，无法刷新订单")
                    _errorMessage.value = "请先在设置中配置WooCommerce API"
                    _refreshing.value = false
                    return@launch
                }
                
                Log.d("OrdersViewModel", "【订单调试】API已配置，开始刷新")
                
                try {
                    val startTime = System.currentTimeMillis()
                    // 使用当前状态过滤器刷新订单
                    val result = orderRepository.refreshOrders(_currentStatusFilter.value)
                    val duration = System.currentTimeMillis() - startTime
                    
                    if (result.isSuccess) {
                        val orders = result.getOrDefault(emptyList())
                        Log.d("OrdersViewModel", "【订单调试】成功刷新订单数据，获取到 ${orders.size} 个订单，耗时 ${duration}ms")
                        
                        // 记录前几个订单的基本信息
                        if (orders.isNotEmpty()) {
                            val sample = orders.take(3).joinToString(", ") { order -> 
                                "#${order.id} (${order.status}: ${order.total})" 
                            }
                            Log.d("OrdersViewModel", "【订单调试】订单示例: $sample${if (orders.size > 3) "..." else ""}")
                        }
                        
                        // 刷新完成后重新应用当前过滤状态
                        if (!_currentStatusFilter.value.isNullOrEmpty()) {
                            try {
                                // 如果正在筛选某个状态，更新该状态的订单列表
                                val status = _currentStatusFilter.value!!
                                Log.d("OrdersViewModel", "【订单调试】刷新后重新应用过滤状态: '$status'")
                                
                                // 获取指定状态的订单
                                val filteredOrders = orderRepository.getOrdersByStatusFlow(status).first()
                                
                                Log.d("OrdersViewModel", "【订单调试】获取到 ${filteredOrders.size} 个 '$status' 状态的订单")
                                if (filteredOrders.isNotEmpty()) {
                                    val sample = filteredOrders.take(2).joinToString(", ") { order -> 
                                        "#${order.id} (${order.status})" 
                                    }
                                    Log.d("OrdersViewModel", "【订单调试】状态 '$status' 订单示例: $sample${if (filteredOrders.size > 2) "..." else ""}")
                                } else {
                                    Log.d("OrdersViewModel", "【订单调试】状态 '$status' 没有找到任何订单")
                                }
                                
                                // 更新UI显示
                                _orders.value = filteredOrders
                            } catch (e: Exception) {
                                Log.e("OrdersViewModel", "【订单调试】重新应用过滤状态出错: ${e.message}", e)
                            }
                        }
                        
                        _errorMessage.value = null
                    } else {
                        val exception = result.exceptionOrNull()
                        Log.e("OrdersViewModel", "【订单调试】刷新订单数据失败: ${exception?.message}", exception)
                        
                        // 提供更具体的错误消息
                        val errorMsg = when {
                            exception?.message?.contains("401") == true -> "API认证失败 (401)，请检查Consumer Key和Secret"
                            exception?.message?.contains("404") == true -> "API端点未找到 (404)，请检查网站URL是否正确"
                            exception?.message?.contains("无法解析主机") == true -> "无法连接到服务器，请检查网络连接和网站URL"
                            else -> "刷新数据失败: ${exception?.message}"
                        }
                        _errorMessage.value = errorMsg
                    }
                } catch (e: Exception) {
                    Log.e("OrdersViewModel", "【订单调试】刷新订单时发生异常: ${e.message}", e)
                    _errorMessage.value = "刷新数据出错: ${e.message}"
                } finally {
                    _refreshing.value = false
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "【订单调试】刷新订单时出错: ${e.message}", e)
                _errorMessage.value = "刷新数据出错: ${e.message}"
                _refreshing.value = false
                _isLoading.value = false
            }
        }
    }
    
    fun filterOrdersByStatus(status: String?) {
        // 取消之前的过滤任务
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "正在按状态过滤订单: $status")
                _isLoading.value = true
                // 保存当前状态过滤条件
                _currentStatusFilter.value = status
                
                if (status.isNullOrEmpty()) {
                    observeOrders()
                } else {
                    // 首先刷新对应状态的订单数据
                    val refreshResult = orderRepository.refreshOrders(status)
                    
                    if (refreshResult.isSuccess) {
                        Log.d("OrdersViewModel", "成功刷新状态 '$status' 的订单数据")
                        
                        // 然后获取并应用过滤后的数据
                        val filteredOrders = orderRepository.getOrdersByStatusFlow(status).first()
                        Log.d("OrdersViewModel", "状态 '$status' 过滤后得到 ${filteredOrders.size} 个订单")
                        _orders.value = filteredOrders
                    } else {
                        val exception = refreshResult.exceptionOrNull()
                        Log.e("OrdersViewModel", "刷新状态 '$status' 的订单数据失败: ${exception?.message}")
                        _errorMessage.value = "无法加载 '$status' 状态的订单: ${exception?.message}"
                    }
                    
                    _isLoading.value = false
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
                    // 刷新订单，确保使用现有的状态过滤
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