package com.example.wooauto.presentation.screens.orders

import android.app.Application
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepository: DomainOrderRepository,
    private val settingRepository: DomainSettingRepository,
    private val printerManager: PrinterManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "OrdersViewModel"
    }

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

    // 添加未读订单相关状态
    private val _unreadOrders = MutableStateFlow<List<Order>>(emptyList())
    val unreadOrders: StateFlow<List<Order>> = _unreadOrders
    
    private val _unreadOrdersCount = MutableStateFlow(0)
    val unreadOrdersCount: StateFlow<Int> = _unreadOrdersCount

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
        
        // 检查配置和注册广播
        checkConfiguration()
        registerBroadcastReceiver()
        
        // 注册接收刷新订单的广播
        registerRefreshOrdersBroadcastReceiver()
        
        // 观察订单数据
        observeOrders()
        
        // 应用启动时验证未读订单状态，然后重新加载未读订单
        viewModelScope.launch {
            try {
                Log.d(TAG, "应用启动：初始化未读订单状态")
                
                // 首先强制重置未读计数为0，防止显示错误的未读数量
                _unreadOrders.value = emptyList()
                _unreadOrdersCount.value = 0
                
                // 先清理数据库中不存在的未读订单ID
                val cleanupResult = cleanupNonExistentUnreadOrders()
                
                // 无论是否有需要清理的条目，都验证一次未读订单的有效性
                Log.d(TAG, "清理结果：移除了 $cleanupResult 个无效未读标记")
                
                // 验证未读订单有效性
                val verifyResult = verifyUnreadOrdersValid()
                if (!verifyResult) {
                    Log.d(TAG, "发现无效未读订单，重置所有未读状态")
                    resetAllUnreadStatus()
                    
                    // 延迟执行发送广播通知UI更新
                    delay(300)
                    val updateIntent = Intent(ACTION_ORDERS_UPDATED)
                    LocalBroadcastManager.getInstance(context).sendBroadcast(updateIntent)
                } else {
                    // 正常加载未读订单
                    Log.d(TAG, "未读订单验证通过，正常加载未读订单")
                    delay(300)
                    loadUnreadOrders()
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动时加载未读订单出错", e)
                // 确保未读计数为0
                _unreadOrders.value = emptyList()
                _unreadOrdersCount.value = 0
            }
        }
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
            // 使用不带flags参数的registerReceiver方法以确保兼容性
            context.registerReceiver(ordersUpdateReceiver, filter)
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
                
                // 初始设置为加载中
                _isLoading.value = true
                
                // 先检查是否有缓存的订单数据
                val cachedOrders = orderRepository.getCachedOrders()
                if (cachedOrders.isNotEmpty()) {
                    Log.d(TAG, "存在缓存订单数据 ${cachedOrders.size} 个，临时显示缓存数据")
                    _orders.value = cachedOrders
                    // 如果有缓存数据，提前将isLoading设为false，让用户可以查看缓存数据
                    _isLoading.value = false
                }
                
                // 设置API调用超时
                var apiCallCompleted = false
                var apiCallSuccess = false
                
                // 启动API调用
                val apiCallJob = viewModelScope.launch {
                    try {
                        // 执行API调用，尝试获取订单
                        val result = orderRepository.refreshOrders()
                        
                        // 处理API请求结果
                        if (result.isSuccess) {
                            val orders = result.getOrNull() ?: emptyList()
                            // 如果成功获取了订单，强制设置配置状态为true
                            if (orders.isNotEmpty()) {
                                Log.d(TAG, "API调用成功返回了 ${orders.size} 个订单，更新API配置状态为true")
                                _isConfigured.value = true
                                // 同时更新全局状态
                                WooCommerceConfig.updateConfigurationStatus(true)
                                com.example.wooauto.data.remote.WooCommerceConfig.updateConfigurationStatus(true)
                                apiCallSuccess = true
                            } else {
                                Log.d(TAG, "API调用成功但未返回订单，保持当前配置状态: $configuredFromStatus")
                                _isConfigured.value = configuredFromStatus
                                apiCallSuccess = configuredFromStatus
                            }
                        } else {
                            Log.d(TAG, "API调用失败")
                            // 如果存在缓存数据，不要将isConfigured设为false
                            if (cachedOrders.isEmpty()) {
                                _isConfigured.value = false
                            } else {
                                // 有缓存数据时，保持当前状态或设为true以允许用户正常使用
                                _isConfigured.value = true
                                Log.d(TAG, "虽然API调用失败，但有缓存数据，设置为已配置状态以允许用户查看")
                            }
                            apiCallSuccess = false
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "API调用过程中发生异常: ${e.message}", e)
                        // 如果存在缓存数据，不要将isConfigured设为false
                        if (cachedOrders.isEmpty()) {
                            _isConfigured.value = false
                        } else {
                            // 有缓存数据时，保持当前状态或设为true以允许用户正常使用
                            _isConfigured.value = true
                            Log.d(TAG, "虽然API调用异常，但有缓存数据，设置为已配置状态以允许用户查看")
                        }
                        apiCallSuccess = false
                        
                        // 设置用户友好的错误消息，但只在没有缓存数据时提示
                        if (cachedOrders.isEmpty()) {
                            val errorMsg = when {
                                e.message?.contains("timeout") == true -> "API请求超时，请检查网络连接"
                                e.message?.contains("401") == true -> "API认证失败，请检查设置中的API密钥"
                                e.message?.contains("404") == true -> "API端点未找到，请检查站点URL"
                                e.message?.contains("网络") == true || e.message?.contains("连接") == true -> "网络连接失败，请检查网络设置"
                                else -> "API调用失败: ${e.message}"
                            }
                            _errorMessage.value = errorMsg
                        }
                    } finally {
                        apiCallCompleted = true
                    }
                }
                
                // 等待API调用完成或超时(5秒)
                kotlinx.coroutines.withTimeoutOrNull(5000) {
                    while (!apiCallCompleted) {
                        kotlinx.coroutines.delay(100)
                    }
                }
                
                // 如果API调用已完成，使用其结果
                if (apiCallCompleted) {
                    Log.d(TAG, "API配置检查完成，配置状态: ${_isConfigured.value}")
                } else {
                    // API调用超时，取消并使用默认设置
                    apiCallJob.cancel()
                    Log.d(TAG, "API调用超时，使用默认设置")
                    // 如果有缓存数据，假设API配置是正常的，减少用户干扰
                    if (cachedOrders.isNotEmpty()) {
                        Log.d(TAG, "有缓存数据，假设API配置正常")
                        _isConfigured.value = true
                    } else {
                        _isConfigured.value = configuredFromStatus
                    }
                }

                // 请求完成后，无论结果如何，都标记为非加载状态
                _isLoading.value = false
                
                // 监听配置变化
                viewModelScope.launch {
                    WooCommerceConfig.isConfigured.collectLatest { configured ->
                        Log.d(TAG, "API配置状态变更: $configured")
                        _isConfigured.value = configured
                        
                        // 如果配置状态变为已配置，尝试刷新订单
                        if (configured) {
                            refreshOrders()
                        }
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
                // 首先从数据库加载缓存的订单，不等待API
                val cachedOrders = orderRepository.getCachedOrders()
                if (cachedOrders.isNotEmpty()) {
                    Log.d("OrdersViewModel", "从缓存加载${cachedOrders.size}个订单")
                    _orders.value = cachedOrders
                    _isLoading.value = false
                }
                
                // 观察订单流以获取持续更新
                orderRepository.getAllOrdersFlow().collectLatest { ordersList ->
                    Log.d("OrdersViewModel", "订单流更新，收到${ordersList.size}个订单")
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
            _refreshing.value = true
            _isLoading.value = true
            
            try {
                val apiConfigured = checkApiConfiguration()
                if (apiConfigured) {
                    val result = orderRepository.refreshOrders()
                    if (result.isSuccess) {
                        _orders.value = result.getOrDefault(emptyList())
                        
                        // 确保所有订单已正确持久化到数据库后再加载未读订单
                        delay(300) // 添加短暂延迟确保数据库操作完成
                        loadUnreadOrders() // 从数据库加载未读订单
                    }
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "刷新订单时发生错误", e)
                _errorMessage.value = e.localizedMessage ?: "刷新订单失败"
            } finally {
                _isLoading.value = false
                _refreshing.value = false
            }
        }
    }
    
    /**
     * 检查API配置是否有效
     * @return 配置是否有效
     */
    private suspend fun checkApiConfiguration(): Boolean {
        return try {
            val config = settingRepository.getWooCommerceConfig()
            val isValid = config.isValid()
            _isConfigured.value = isValid
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "检查API配置失败: ${e.message}")
            _isConfigured.value = false
            false
        }
    }
    
    /**
     * 将UI中的中文状态和API中的英文状态互相映射
     * @param status 状态字符串
     * @param toEnglish 是否从中文转换到英文
     * @return 映射后的状态字符串
     */
    private fun mapStatusToChinese(status: String?, toEnglish: Boolean = false): String {
        if (status == null) return "any" // 空值时返回默认值
        
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
                val success = orderRepository.markOrderAsPrinted(orderId)
                if (success) {
                    Log.d("OrdersViewModel", "成功标记订单为已打印")
                    // 获取更新后的订单
                    val updatedOrder = orderRepository.getOrderById(orderId)
                    updatedOrder?.let {
                        Log.d("OrdersViewModel", "订单已更新，打印状态: ${it.isPrinted}")
                        _selectedOrder.value = it
                    }
                    // 刷新订单列表
                    refreshOrders()
                }
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
                    // 刷新选中的订单，确保UI显示更新
                    val updatedOrder = orderRepository.getOrderById(orderId)
                    updatedOrder?.let {
                        _selectedOrder.value = it
                    }
                    // 刷新订单列表
                    refreshOrders()
                } else {
                    Log.e("OrdersViewModel", "打印订单失败: $orderId")
                }
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "打印订单时出错: ${e.message}", e)
            }
        }
    }

    /**
     * 注册接收刷新订单的广播接收器
     */
    private fun registerRefreshOrdersBroadcastReceiver() {
        viewModelScope.launch {
            try {
                // 使用已注入的context替代Application
                
                // 创建广播接收器
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        Log.d("OrdersViewModel", "【轮询通知】收到刷新订单广播")
                        refreshOrders()
                    }
                }
                
                // 注册广播接收器
                LocalBroadcastManager.getInstance(context).registerReceiver(
                    receiver,
                    IntentFilter("com.example.wooauto.REFRESH_ORDERS")
                )
                
                Log.d("OrdersViewModel", "已注册刷新订单广播接收器")
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "注册刷新订单广播接收器失败: ${e.message}")
            }
        }
    }

    /**
     * 清理数据库中不存在的未读订单
     * @return 清理的记录数
     */
    private suspend fun cleanupNonExistentUnreadOrders(): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始清理数据库中不存在但标记为未读的订单")
            
            val orderDao = orderRepository.getOrderDao()
            
            // 获取所有未读订单ID
            val unreadIds = orderDao.getUnreadOrderIds()
            if (unreadIds.isEmpty()) {
                Log.d(TAG, "没有未读订单ID，无需清理")
                return@withContext 0
            }
            
            Log.d(TAG, "找到 ${unreadIds.size} 个未读订单ID")
            
            // 获取所有订单ID
            val allOrderIds = orderDao.getAllOrderIds()
            Log.d(TAG, "数据库中共有 ${allOrderIds.size} 个订单")
            
            // 找出不存在的订单ID
            val nonExistentIds = unreadIds.filter { it !in allOrderIds }
            
            if (nonExistentIds.isNotEmpty()) {
                Log.d(TAG, "发现 ${nonExistentIds.size} 个不存在但标记为未读的订单ID: $nonExistentIds")
                
                // 删除这些不存在的订单ID
                try {
                    orderDao.deleteOrdersByIds(nonExistentIds)
                    Log.d(TAG, "成功清理不存在的未读订单ID")
                    return@withContext nonExistentIds.size
                } catch (e: Exception) {
                    Log.e(TAG, "清理不存在的未读订单ID失败: ${e.message}")
                    return@withContext 0
                }
            } else {
                Log.d(TAG, "未发现需要清理的订单ID")
                return@withContext 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理不存在的未读订单时出错: ${e.message}")
            return@withContext 0
        }
    }
    
    // 加载未读订单
    private fun loadUnreadOrders() {
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "开始从数据库加载未读订单")
                
                // 使用dao直接获取未读订单的ID列表
                val unreadOrderIds = withContext(Dispatchers.IO) {
                    try {
                        val orderDao = orderRepository.getOrderDao()
                        val ids = orderDao.getUnreadOrderIds()
                        Log.d("OrdersViewModel", "数据库中找到 ${ids.size} 个未读订单ID: $ids")
                        ids
                    } catch (e: Exception) {
                        Log.e("OrdersViewModel", "获取未读订单ID时发生错误", e)
                        emptyList<Long>()
                    }
                }
                
                if (unreadOrderIds.isEmpty()) {
                    // 如果没有未读订单，直接清空未读订单列表和计数
                    _unreadOrders.value = emptyList()
                    _unreadOrdersCount.value = 0
                    Log.d("OrdersViewModel", "没有未读订单，已清空未读订单列表和计数")
                    return@launch
                }
                
                // 根据未读ID列表获取完整的未读订单
                val unreadOrdersList = orderRepository.getOrdersByIds(unreadOrderIds)
                
                Log.d("OrdersViewModel", "获取到 ${unreadOrdersList.size} 个未读订单（应为 ${unreadOrderIds.size} 个）")
                
                // 如果未读订单列表数量与ID列表不一致，可能有订单已被删除
                if (unreadOrdersList.size != unreadOrderIds.size) {
                    Log.w("OrdersViewModel", "警告：未读订单数量与未读ID列表不一致")
                    
                    // 更新数据库中的未读状态，将不存在订单的ID标记为已读
                    val existingIds = unreadOrdersList.map { it.id }
                    val nonExistingIds = unreadOrderIds.filter { it !in existingIds }
                    
                    if (nonExistingIds.isNotEmpty()) {
                        Log.d("OrdersViewModel", "存在 ${nonExistingIds.size} 个ID无对应订单，将清理这些ID: $nonExistingIds")
                        
                        withContext(Dispatchers.IO) {
                            try {
                                val orderDao = orderRepository.getOrderDao()
                                // 将这些ID标记为已读，防止再次出现问题
                                orderDao.markOrdersAsRead(nonExistingIds)
                                Log.d("OrdersViewModel", "成功将不存在的订单ID标记为已读")
                            } catch (e: Exception) {
                                Log.e("OrdersViewModel", "标记不存在订单ID为已读失败: ${e.message}")
                            }
                        }
                    }
                }
                
                // 严格过滤出有效的未读订单：
                // 1. 确保订单ID有效
                // 2. 订单状态不为空
                // 3. 订单显示名称不为空
                // 4. 订单状态应为有效状态 (比如processing)
                val validUnreadOrders = unreadOrdersList.filter { order ->
                    val validId = order.id > 0
                    val validStatus = order.status.isNotEmpty()
                    val validName = order.customerName.isNotEmpty() || order.number.isNotEmpty()
                    val validOrderStatus = isValidOrderStatus(order.status)
                    
                    val isValid = validId && validStatus && validName && validOrderStatus
                    
                    if (!isValid) {
                        Log.d("OrdersViewModel", "过滤无效未读订单: ID=${order.id}, 状态=${order.status}, 名称=${order.customerName}")
                    }
                    
                    isValid
                }
                
                if (validUnreadOrders.size != unreadOrdersList.size) {
                    Log.d("OrdersViewModel", "过滤掉 ${unreadOrdersList.size - validUnreadOrders.size} 个无效未读订单")
                    
                    // 标记这些无效订单为已读
                    val invalidOrderIds = unreadOrdersList
                        .filter { order -> !validUnreadOrders.any { it.id == order.id } }
                        .map { it.id }
                    
                    if (invalidOrderIds.isNotEmpty()) {
                        Log.d("OrdersViewModel", "将 ${invalidOrderIds.size} 个无效订单标记为已读: $invalidOrderIds")
                        withContext(Dispatchers.IO) {
                            try {
                                val orderDao = orderRepository.getOrderDao()
                                orderDao.markOrdersAsRead(invalidOrderIds)
                            } catch (e: Exception) {
                                Log.e("OrdersViewModel", "标记无效订单为已读失败: ${e.message}")
                            }
                        }
                    }
                }
                
                _unreadOrders.value = validUnreadOrders
                _unreadOrdersCount.value = validUnreadOrders.size
                
                Log.d("OrdersViewModel", "未读订单加载完成，最终数量: ${_unreadOrdersCount.value}")
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "获取未读订单时发生错误", e)
                _unreadOrders.value = emptyList()
                _unreadOrdersCount.value = 0
            }
        }
    }
    
    /**
     * 检查订单状态是否有效
     */
    private fun isValidOrderStatus(status: String): Boolean {
        val validStatuses = setOf(
            "processing", "pending", "completed", "cancelled", "refunded", "failed", "on-hold",
            "处理中", "待付款", "已完成", "已取消", "已退款", "失败", "暂挂"
        )
        return status.isNotEmpty() && (validStatuses.contains(status) || status.length > 2)
    }
    
    // 标记订单为已读
    fun markOrderAsRead(orderId: Long) {
        viewModelScope.launch {
            try {
                orderRepository.markOrderAsRead(orderId)
                // 更新未读订单列表
                _unreadOrders.value = _unreadOrders.value.filter { it.id != orderId }
                _unreadOrdersCount.value = _unreadOrders.value.size
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "标记订单已读时发生错误", e)
                _errorMessage.value = e.localizedMessage ?: "标记订单已读失败"
            }
        }
    }
    
    // 标记所有订单为已读
    fun markAllOrdersAsRead() {
        viewModelScope.launch {
            try {
                orderRepository.markAllOrdersAsRead()
                _unreadOrders.value = emptyList()
                _unreadOrdersCount.value = 0
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "标记所有订单已读时发生错误", e)
                _errorMessage.value = e.localizedMessage ?: "标记所有订单已读失败"
            }
        }
    }
    
    /**
     * 刷新未读订单列表
     * 专门用于未读订单对话框打开时调用
     */
    fun refreshUnreadOrders() {
        viewModelScope.launch {
            try {
                Log.d("OrdersViewModel", "手动刷新未读订单列表")
                loadUnreadOrders()
            } catch (e: Exception) {
                Log.e("OrdersViewModel", "刷新未读订单时发生错误", e)
            }
        }
    }

    /**
     * 强制重置所有订单为已读状态
     */
    private suspend fun resetAllUnreadStatus() {
        withContext(Dispatchers.IO) {
            try {
                val orderDao = orderRepository.getOrderDao()
                Log.d(TAG, "开始将所有订单标记为已读")
                
                // 获取所有未读订单ID
                val unreadIds = orderDao.getUnreadOrderIds()
                
                if (unreadIds.isNotEmpty()) {
                    Log.d(TAG, "发现 ${unreadIds.size} 个未读订单ID，将全部标记为已读: $unreadIds")
                    orderDao.markAllOrdersAsRead()
                    Log.d(TAG, "成功将所有订单标记为已读")
                } else {
                    Log.d(TAG, "没有未读订单，无需重置")
                }
                
                // 重置内存中的状态
                _unreadOrders.value = emptyList()
                _unreadOrdersCount.value = 0
            } catch (e: Exception) {
                Log.e(TAG, "重置所有订单为已读状态时出错: ${e.message}")
            }
        }
    }

    /**
     * 验证未读订单的有效性
     * @return 如果所有未读订单都有效返回true，否则返回false
     */
    private suspend fun verifyUnreadOrdersValid(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始验证未读订单有效性")
            
            val orderDao = orderRepository.getOrderDao()
            
            // 获取所有未读订单ID
            val unreadIds = orderDao.getUnreadOrderIds()
            if (unreadIds.isEmpty()) {
                Log.d(TAG, "没有未读订单ID，无需验证")
                return@withContext true
            }
            
            Log.d(TAG, "找到 ${unreadIds.size} 个未读订单ID: $unreadIds")
            
            // 获取这些ID对应的订单
            val unreadOrders = orderRepository.getOrdersByIds(unreadIds)
            
            // 如果未读订单数量与未读ID数量不匹配，表示有无效的未读订单
            if (unreadOrders.size != unreadIds.size) {
                Log.w(TAG, "未读订单数量 (${unreadOrders.size}) 与未读ID数量 (${unreadIds.size}) 不匹配")
                return@withContext false
            }
            
            // 计算30天前的时间戳，用于过滤过老的订单
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -30)  // 30天前的时间
            val thirtyDaysAgo = calendar.timeInMillis
            
            // 检查所有未读订单是否有效
            val invalidOrders = unreadOrders.filter { order ->
                // 检查订单是否无效或者是否太老
                !isValidOrder(order) || order.dateCreated.time < thirtyDaysAgo
            }
            
            if (invalidOrders.isNotEmpty()) {
                Log.w(TAG, "发现 ${invalidOrders.size} 个无效的未读订单")
                invalidOrders.forEach { order ->
                    val reason = if (!isValidOrder(order)) "无效订单" else "订单过期(超过30天)"
                    Log.d(TAG, "无效未读订单: ID=${order.id}, 状态=${order.status}, 名称=${order.customerName}, 原因=$reason")
                }
                
                // 将这些无效订单标记为已读
                val invalidOrderIds = invalidOrders.map { it.id }
                if (invalidOrderIds.isNotEmpty()) {
                    Log.d(TAG, "将 ${invalidOrderIds.size} 个无效订单标记为已读: $invalidOrderIds")
                    orderDao.markOrdersAsRead(invalidOrderIds)
                }
                
                return@withContext false
            }
            
            Log.d(TAG, "所有未读订单验证通过")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "验证未读订单有效性时出错: ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * 判断订单是否有效
     */
    private fun isValidOrder(order: Order): Boolean {
        val validId = order.id > 0
        val validStatus = order.status.isNotEmpty()
        val validName = order.customerName.isNotEmpty() || order.number.isNotEmpty()
        val validOrderStatus = isValidOrderStatus(order.status)
        
        return validId && validStatus && validName && validOrderStatus
    }
} 