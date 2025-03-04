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
                // 使用当前状态过滤器的值
                val currentStatus = _currentStatusFilter.value
                Log.d("OrdersViewModel", "【状态刷新】开始刷新订单，当前状态: $currentStatus")
                _refreshing.value = true
                _errorMessage.value = null
                
                // 检查API配置
                val config = wooCommerceConfig
                val configured = config.isConfigured.first()
                if (!configured) {
                    Log.e("OrdersViewModel", "API未配置，无法刷新订单")
                    _errorMessage.value = "请先在设置中配置WooCommerce API"
                    _refreshing.value = false
                    return@launch
                }
                
                // 将选择的中文状态映射为API需要的英文状态
                val apiStatus = mapStatusToChinese(currentStatus, true)
                Log.d("OrdersViewModel", "【状态刷新】状态映射结果: '$currentStatus' -> '$apiStatus'")
                
                // 确保API参数中使用的是有效的英文状态
                val result = orderRepository.refreshOrders(apiStatus)
                
                if (result.isSuccess) {
                    Log.d("OrdersViewModel", "【状态刷新】刷新成功，获取到 ${result.getOrDefault(emptyList()).size} 个订单")
                    
                    // 刷新完成后重新应用当前过滤状态
                    if (!currentStatus.isNullOrEmpty()) {
                        try {
                            // 使用本地状态过滤（中文状态）
                            val filteredOrders = orderRepository.getOrdersByStatusFlow(currentStatus).first()
                            Log.d("OrdersViewModel", "【状态刷新】过滤后获取到 ${filteredOrders.size} 个 '$currentStatus' 状态的订单")
                            _orders.value = filteredOrders
                        } catch (e: Exception) {
                            Log.e("OrdersViewModel", "重新应用过滤状态出错: ${e.message}", e)
                        }
                    }
                    
                    _errorMessage.value = null
                } else {
                    val exception = result.exceptionOrNull()
                    Log.e("OrdersViewModel", "刷新订单数据失败: ${exception?.message}")
                    _errorMessage.value = "刷新数据失败: ${exception?.message}"
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
    
    /**
     * 将UI中的中文状态和API中的英文状态互相映射
     * @param status 状态字符串
     * @param toEnglish 是否从中文转换到英文
     * @return 映射后的状态字符串
     */
    private fun mapStatusToChinese(status: String?, toEnglish: Boolean = false): String? {
        if (status == null) return null
        
        // 状态映射表 - 确保英文状态值完全符合WooCommerce API要求
        val statusMap = mapOf(
            // 中文 to 英文 (使用API支持的精确状态值)
            "处理中" to "processing",
            "待付款" to "pending",
            "已完成" to "completed",
            "已取消" to "cancelled",
            "已退款" to "refunded",
            "失败" to "failed",
            "暂挂" to "on-hold",
            // 英文 to 中文
            "processing" to "处理中",
            "pending" to "待付款",
            "completed" to "已完成",
            "cancelled" to "已取消",
            "refunded" to "已退款",
            "failed" to "失败",
            "on-hold" to "暂挂"
        )
        
        val cleanStatus = status.trim()
        
        // 根据方向转换状态
        val result = if (toEnglish) {
            // 尝试直接映射（如"失败" -> "failed"）
            val mapped = statusMap[cleanStatus]
            if (mapped != null) {
                Log.d("OrdersViewModel", "【状态映射】中文状态 '$cleanStatus' 映射为英文 '$mapped'")
                mapped
            } else {
                // 检查是否已经是英文状态，需匹配WooCommerce期望的值
                val validStatuses = listOf("pending", "processing", "on-hold", "completed", "cancelled", "refunded", "failed")
                val lowerStatus = cleanStatus.toLowerCase()
                if (validStatuses.contains(lowerStatus)) {
                    Log.d("OrdersViewModel", "【状态映射】已是有效的英文状态 '$lowerStatus'")
                    lowerStatus
                } else {
                    Log.w("OrdersViewModel", "【状态映射】警告：无法映射状态 '$cleanStatus'，使用原值")
                    cleanStatus
                }
            }
        } else {
            // 英文到中文的映射
            statusMap[cleanStatus.toLowerCase()] ?: cleanStatus
        }
        
        Log.d("OrdersViewModel", "【状态映射】${if (toEnglish) "中->英" else "英->中"}: '$cleanStatus' -> '$result'")
        return result
    }
    
    fun filterOrdersByStatus(status: String?) {
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "【状态筛选】开始按状态筛选: $status")
                _isLoading.value = true
                
                // 保存当前状态过滤条件(中文状态)
                _currentStatusFilter.value = status
                
                if (status.isNullOrEmpty()) {
                    Log.d("OrdersViewModel", "状态为空，显示所有订单")
                    observeOrders()
                } else {
                    // 将中文状态映射为英文状态用于API调用
                    val apiStatus = mapStatusToChinese(status, true)
                    Log.d("OrdersViewModel", "【状态筛选】映射状态: '$status' -> '$apiStatus'")
                    
                    // 刷新对应状态的订单数据
                    val refreshResult = orderRepository.refreshOrders(apiStatus)
                    
                    if (refreshResult.isSuccess) {
                        Log.d("OrdersViewModel", "【状态筛选】成功刷新 '$status'($apiStatus) 状态的订单")
                        
                        // 获取并应用过滤后的数据
                        val filteredOrders = orderRepository.getOrdersByStatusFlow(status).first()
                        Log.d("OrdersViewModel", "【状态筛选】过滤后得到 ${filteredOrders.size} 个订单")
                        
                        _orders.value = filteredOrders
                    } else {
                        val exception = refreshResult.exceptionOrNull()
                        Log.e("OrdersViewModel", "刷新状态 '$status' 的订单失败: ${exception?.message}")
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