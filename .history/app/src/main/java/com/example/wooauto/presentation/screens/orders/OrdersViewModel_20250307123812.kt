package com.example.wooauto.presentation.screens.orders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.domain.printer.PrinterManager
import com.example.wooauto.domain.templates.TemplateType
import com.example.wooauto.service.BackgroundPollingService
import com.example.wooauto.service.BackgroundPollingService.Companion.ACTION_ORDERS_UPDATED
import com.example.wooauto.service.BackgroundPollingService.Companion.ACTION_NEW_ORDERS_RECEIVED
import com.example.wooauto.service.BackgroundPollingService.Companion.EXTRA_ORDER_COUNT
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val orderRepository: DomainOrderRepository,
    private val settingRepository: DomainSettingRepository,
    private val printerManager: PrinterManager,
    @ApplicationContext private val context: Context
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

    // 广播接收器
    private val ordersUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_ORDERS_UPDATED -> {
                    Log.d(TAG, "收到订单更新广播，刷新订单列表")
                    refreshOrders()
                }
                ACTION_NEW_ORDERS_RECEIVED -> {
                    val orderCount = intent.getIntExtra(EXTRA_ORDER_COUNT, 0)
                    Log.d(TAG, "收到新订单广播，新订单数量: $orderCount，刷新订单列表")
                    refreshOrders()
                }
            }
        }
    }

    init {
        Log.d(TAG, "OrdersViewModel 初始化")
        checkConfiguration()
        registerBroadcastReceiver()
        observeOrders()
    }

    override fun onCleared() {
        super.onCleared()
        // 注销广播接收器
        try {
            context.unregisterReceiver(ordersUpdateReceiver)
            Log.d(TAG, "注销订单广播接收器")
        } catch (e: Exception) {
            Log.e(TAG, "注销订单广播接收器失败: ${e.message}")
        }
    }

    private fun registerBroadcastReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(ACTION_ORDERS_UPDATED)
                addAction(ACTION_NEW_ORDERS_RECEIVED)
            }
            // 添加RECEIVER_NOT_EXPORTED标志以符合Android安全要求
            context.registerReceiver(ordersUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            Log.d(TAG, "成功注册订单广播接收器")
        } catch (e: Exception) {
            Log.e(TAG, "注册订单广播接收器失败: ${e.message}")
        }
    }

    private fun checkConfiguration() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "正在检查API配置")
                
                // 首先从WooCommerceConfig获取配置状态
                val configuredFromStatus = WooCommerceConfig.isConfigured.first()
                Log.d(TAG, "从状态流获取的API配置状态: $configuredFromStatus")
                
                // 即使状态显示未配置，也尝试获取订单来验证API实际是否可用
                val result = orderRepository.refreshOrders()
                
                // 如果API请求成功，强制设置isConfigured为true
                if (result.isSuccess && result.getOrNull()?.isNotEmpty() == true) {
                    Log.d(TAG, "API调用成功，确认API已配置")
                    _isConfigured.value = true
                    // 同时更新全局状态
                    WooCommerceConfig.updateConfigurationStatus(true)
                } else if (!configuredFromStatus) {
                    Log.d(TAG, "API配置状态为未配置，且API调用未成功")
                    _isConfigured.value = false
                    _isLoading.value = false
                } else {
                    // 使用从状态流获取的配置
                    Log.d(TAG, "使用从状态流获取的配置: $configuredFromStatus")
                    _isConfigured.value = configuredFromStatus
                    if (configuredFromStatus) {
                        refreshOrders()
                    } else {
                        _isLoading.value = false
                    }
                }
                
                // 监听配置变化
                viewModelScope.launch {
                    WooCommerceConfig.isConfigured.collectLatest { configured ->
                        Log.d(TAG, "API配置状态变更: $configured")
                        _isConfigured.value = configured
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查配置时出错: ${e.message}", e)
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
                val config = WooCommerceConfig
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
        
        // 状态映射表
        val statusMap = mapOf(
            // 中文 to 英文
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
        
        return if (toEnglish) {
            // 从中文转到英文
            statusMap[status] ?: "any"
        } else {
            // 从英文转到中文
            statusMap[status] ?: status
        }
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
                    // 回到无过滤状态
                    _currentStatusFilter.value = null
                    observeOrders()
                } else {
                    // 将中文状态映射为英文状态用于API调用
                    val apiStatus = mapStatusToChinese(status, true)
                    Log.d("OrdersViewModel", "【状态筛选】映射后的API状态: '$apiStatus'")
                    
                    // 刷新对应状态的订单数据
                    val refreshResult = orderRepository.refreshOrders(apiStatus)
                    
                    if (refreshResult.isSuccess) {
                        Log.d("OrdersViewModel", "【状态筛选】成功刷新订单数据")
                        
                        // 获取刷新结果中的订单
                        val allRefreshedOrders = refreshResult.getOrDefault(emptyList())
                        Log.d("OrdersViewModel", "【状态筛选】刷新获取到 ${allRefreshedOrders.size} 个订单")
                        
                        // 检查是否有匹配当前状态的订单
                        val statusMatchedOrders = allRefreshedOrders.filter { order ->
                            // 检查状态是否匹配，考虑中英文转换
                            val orderStatus = order.status
                            val requestedStatus = status
                            
                            // 直接匹配
                            if (orderStatus == requestedStatus) return@filter true
                            
                            // 通过映射匹配
                            val mappedOrderStatus = mapStatusToChinese(orderStatus, false)
                            if (mappedOrderStatus == requestedStatus) return@filter true
                            
                            // 反向映射匹配
                            val mappedRequestedStatus = mapStatusToChinese(requestedStatus, true)
                            if (orderStatus == mappedRequestedStatus) return@filter true
                            
                            false
                        }
                        
                        Log.d("OrdersViewModel", "【状态筛选】筛选后得到 ${statusMatchedOrders.size} 个状态为 '$status' 的订单")
                        
                        // 如果没有找到匹配状态的订单，显示错误消息
                        if (statusMatchedOrders.isEmpty()) {
                            _errorMessage.value = "没有找到状态为 '$status' 的订单"
                            Log.w("OrdersViewModel", "【状态筛选】警告：未找到状态为 '$status' 的订单")
                        }
                        
                        // 应用过滤后的结果到UI
                        _orders.value = statusMatchedOrders
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

    /**
     * 获取所有有效的订单状态选项，包括中英文对应
     * 可用于在UI中显示状态选择器
     * @return 订单状态列表，包含中英文状态
     */
    fun getOrderStatusOptions(): List<Pair<String, String>> {
        return listOf(
            Pair("处理中", "processing"),
            Pair("待付款", "pending"),
            Pair("已完成", "completed"),
            Pair("已取消", "cancelled"),
            Pair("已退款", "refunded"),
            Pair("失败", "failed"),
            Pair("暂挂", "on-hold")
        )
    }

    /**
     * 打印订单
     * @param orderId 订单ID
     * @param templateType 模板类型，如果为null则使用默认模板
     */
    fun printOrder(orderId: Long, templateType: TemplateType? = null) {
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "打印订单: $orderId, 模板类型: $templateType")
                
                // 获取订单信息
                val order = orderRepository.getOrderById(orderId)
                if (order == null) {
                    Log.e("OrdersViewModel", "未找到订单: $orderId")
                    return@launch
                }
                
                // 获取默认打印机配置
                val printerConfig = settingRepository.getDefaultPrinterConfig()
                if (printerConfig == null) {
                    Log.e("OrdersViewModel", "未找到默认打印机配置")
                    return@launch
                }
                
                // 如果指定了模板类型，临时设置为默认模板
                if (templateType != null) {
                    settingRepository.saveDefaultTemplateType(templateType)
                    Log.d("OrdersViewModel", "临时设置打印模板为: $templateType")
                }
                
                // 打印订单
                val success = printerManager.printOrder(order, printerConfig)
                if (success) {
                    Log.d("OrdersViewModel", "打印订单成功: $orderId")
                    // 标记订单为已打印
                    markOrderAsPrinted(orderId)
                } else {
                    Log.e("OrdersViewModel", "打印订单失败: $orderId")
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "打印订单时出错: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val TAG = "OrdersViewModel"
    }
} 