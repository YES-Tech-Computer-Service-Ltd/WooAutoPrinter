package com.example.wooauto.ui.orders

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.data.api.RetrofitClient
import com.example.wooauto.data.api.models.Order
import com.example.wooauto.data.database.AppDatabase
import com.example.wooauto.data.database.entities.OrderEntity
import com.example.wooauto.data.repositories.OrderRepository
import com.example.wooauto.utils.PreferencesManager
import com.example.wooauto.utils.PrintService
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class OrderViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    private val printService = PrintService(application)

    // Repository instance
    private lateinit var orderRepository: OrderRepository

    companion object {
        private var instance: OrderViewModel? = null
        
        fun getInstance(): OrderViewModel? = instance
    }

    // 跟踪当前查看的订单ID
    private var currentViewingOrderId: Long? = null

    // UI states
    private val _uiState = MutableStateFlow<OrdersUiState>(OrdersUiState.Loading)
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedStatusFilter = MutableStateFlow<String?>(null)
    val selectedStatusFilter = _selectedStatusFilter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    // Order detail state
    private val _orderDetailState = MutableStateFlow<OrderDetailState>(OrderDetailState.Loading)
    val orderDetailState: StateFlow<OrderDetailState> = _orderDetailState.asStateFlow()

    // Print all unprinted orders
    private val _printAllState = MutableStateFlow<PrintAllState>(PrintAllState.Idle)
    val printAllState: StateFlow<PrintAllState> = _printAllState.asStateFlow()

    // API配置状态
    private val _apiConfigured = MutableStateFlow(false)
    val apiConfigured: StateFlow<Boolean> = _apiConfigured.asStateFlow()

    private val TAG = "OrderVM_DEBUG"

    init {
        instance = this
        checkApiCredentials()
    }

    override fun onCleared() {
        super.onCleared()
        if (instance == this) {
            instance = null
        }
    }

    private fun checkApiCredentials() {
        viewModelScope.launch {
            try {
                // 检查API凭证是否已配置
                val websiteUrl = preferencesManager.websiteUrl.first()
                val apiKey = preferencesManager.apiKey.first()
                val apiSecret = preferencesManager.apiSecret.first()

                if (websiteUrl.isNotEmpty() && apiKey.isNotEmpty() && apiSecret.isNotEmpty()) {
                    _apiConfigured.value = true
                    initializeRepository()
                    refreshOrders() // 只刷新一次订单，不启动轮询
                } else {
                    _apiConfigured.value = false
                    // 如果API未配置，显示友好的提示，不显示重试按钮
                    _uiState.value = OrdersUiState.ApiNotConfigured
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查API凭证时出错", e)
                _uiState.value = OrdersUiState.Error(e.message ?: "未知错误")
            }
        }
    }

    private fun initializeRepository() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "初始化订单仓库")
                // 初始化仓库
                val websiteUrl = preferencesManager.websiteUrl.first()
                val apiKey = preferencesManager.apiKey.first()
                val apiSecret = preferencesManager.apiSecret.first()

                val apiService = RetrofitClient.getWooCommerceApiService(websiteUrl)
                val orderDao = AppDatabase.getInstance(getApplication()).orderDao()
                orderRepository = OrderRepository(
                    orderDao,
                    apiService,
                    apiKey,
                    apiSecret
                )

                // 观察基于筛选器的数据库中的订单
                combine(
                    _searchQuery,
                    _selectedStatusFilter,
                    orderRepository.getAllOrdersFlow()
                ) { query, statusFilter, orders ->
                    val filteredOrders = orders.filter { order ->
                        val matchesQuery = if (query.isBlank()) {
                            true
                        } else {
                            order.number.contains(query, ignoreCase = true) ||
                                    order.customerName.contains(query, ignoreCase = true)
                        }

                        val matchesStatus = if (statusFilter == null) {
                            true
                        } else {
                            order.status == statusFilter
                        }

                        matchesQuery && matchesStatus
                    }

                    // 修改判断逻辑，只在非刷新状态且列表为空时显示 Empty
                    when {
                        filteredOrders.isNotEmpty() -> OrdersUiState.Success(filteredOrders)
                        _isRefreshing.value -> OrdersUiState.Loading
                        else -> OrdersUiState.Empty
                    }
                }.catch { e ->
                    Log.e(TAG, "获取订单时发生错误", e)
                    _uiState.value = OrdersUiState.Error(e.message ?: "未知错误")
                }.collect { state ->
                    _uiState.value = state
                }

                // 立即加载初始数据
                refreshOrders()
            } catch (e: Exception) {
                Log.e(TAG, "初始化 OrderViewModel 时发生错误", e)
                _uiState.value = OrdersUiState.Error(e.message ?: "未知错误")
            }
        }

        refreshOrders()
    }

    /**
     * 刷新来自API的订单
     */
    fun refreshOrders() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "===== 开始刷新订单(ViewModel) =====")

                // 检查API是否已配置
                if (!_apiConfigured.value) {
                    checkApiCredentials()
                    return@launch
                }

                // 检查仓库是否已初始化
                if (!::orderRepository.isInitialized) {
                    Log.e(TAG, "订单仓库尚未初始化")
                    _uiState.value = OrdersUiState.Error("正在初始化数据，请稍候...")
                    return@launch
                }

                _isRefreshing.value = true
                Log.d(TAG, "设置刷新状态为true")

                // 使用getAllHistoricalOrders获取所有订单
                val result = orderRepository.getAllHistoricalOrders()

                when {
                    result.isSuccess -> {
                        val orders = result.getOrNull()
                        Log.d(TAG, "刷新订单成功, 获取订单数: ${orders?.size ?: 0}")
                        
                        // 更新数据库并立即触发UI更新
                        orders?.let { orderList ->
                            // 将订单转换为实体并更新数据库
                            val entities = orderList.map { it.toOrderEntity() }
                            // 使用 Room DAO 的 insertAll 方法
                            orderRepository.updateOrders(entities)
                            
                            // 立即更新UI状态
                            val filteredOrders = entities.filter { order ->
                                val matchesQuery = if (_searchQuery.value.isBlank()) {
                                    true
                                } else {
                                    order.number.contains(_searchQuery.value, ignoreCase = true) ||
                                            order.customerName.contains(_searchQuery.value, ignoreCase = true)
                                }

                                val matchesStatus = if (_selectedStatusFilter.value == null) {
                                    true
                                } else {
                                    order.status == _selectedStatusFilter.value
                                }

                                matchesQuery && matchesStatus
                            }

                            _uiState.value = if (filteredOrders.isNotEmpty()) {
                                OrdersUiState.Success(filteredOrders)
                            } else {
                                OrdersUiState.Empty
                            }
                        }

                        // 如果当前正在查看某个订单，刷新其详情
                        currentViewingOrderId?.let { orderId ->
                            Log.d(TAG, "正在查看订单 $orderId，刷新其详情")
                            loadOrderDetails(orderId)
                        }
                    }
                    result.isFailure -> {
                        val exception = result.exceptionOrNull()
                        Log.e(TAG, "刷新订单失败", exception)
                        _uiState.value = OrdersUiState.Error(exception?.message ?: "刷新订单失败")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "刷新订单过程中发生异常", e)
                e.printStackTrace()
                _uiState.value = OrdersUiState.Error(e.message ?: "刷新订单时发生未知错误")
            } finally {
                Log.d(TAG, "设置刷新状态为false")
                _isRefreshing.value = false
                Log.d(TAG, "===== 刷新订单(ViewModel)完成 =====")
            }
        }
    }

    // 扩展函数：将 Order 转换为 OrderEntity
    private fun Order.toOrderEntity(): OrderEntity {
        return OrderEntity(
            id = id,
            number = number,
            status = status,
            dateCreated = dateCreated,
            total = total,
            customerId = customerId,
            customerName = billing.getFullName(),
            billingAddress = "${billing.address1}, ${billing.city}, ${billing.state}",
            shippingAddress = "${shipping.address1}, ${shipping.city}, ${shipping.state}",
            paymentMethod = paymentMethod,
            paymentMethodTitle = paymentMethodTitle,
            lineItemsJson = "",  // 这将由仓库填充
            isPrinted = this.isPrinted,
            notificationShown = this.notificationShown
        )
    }

    /**
     * 更新搜索查询
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * 更新状态筛选器
     */
    fun updateStatusFilter(status: String?) {
        _selectedStatusFilter.value = status
    }

    /**
     * 加载订单详情
     */
    fun loadOrderDetails(orderId: Long) {
        currentViewingOrderId = orderId
        viewModelScope.launch {
            _orderDetailState.value = OrderDetailState.Loading

            try {
                // 检查API是否已配置
                if (!_apiConfigured.value) {
                    _orderDetailState.value = OrderDetailState.Error("请先在设置中配置API")
                    return@launch
                }

                // 检查仓库是否已初始化
                if (!::orderRepository.isInitialized) {
                    _orderDetailState.value = OrderDetailState.Error("正在初始化数据，请稍候...")
                    return@launch
                }

                // 从API获取最新数据
                val result = orderRepository.getOrder(orderId)

                if (result.isSuccess) {
                    val order = result.getOrNull()
                    if (order != null) {
                        // API订单可用，更新UI
                        _orderDetailState.value = OrderDetailState.Success(
                            OrderEntity(
                                id = order.id,
                                number = order.number,
                                status = order.status,
                                dateCreated = order.dateCreated,
                                total = order.total,
                                customerId = order.customerId,
                                customerName = order.billing.getFullName(),
                                billingAddress = "${order.billing.address1}, ${order.billing.city}, ${order.billing.state}",
                                shippingAddress = "${order.shipping.address1}, ${order.shipping.city}, ${order.shipping.state}",
                                paymentMethod = order.paymentMethod,
                                paymentMethodTitle = order.paymentMethodTitle,
                                lineItemsJson = "",  // 这将由仓库填充
                                isPrinted = order.isPrinted,
                                notificationShown = order.notificationShown
                            )
                        )
                    }
                } else {
                    // 如果API请求失败，尝试从本地数据库获取
                    val localOrder = orderRepository.getOrderById(orderId)
                    if (localOrder != null) {
                        _orderDetailState.value = OrderDetailState.Success(localOrder)
                    } else {
                        _orderDetailState.value = OrderDetailState.Error(
                            result.exceptionOrNull()?.message ?: "无法加载订单"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("OrderViewModel", "加载订单详情时出错", e)
                _orderDetailState.value = OrderDetailState.Error(e.message ?: "未知错误")
            }
        }
    }

    /**
     * 将订单标记为已完成
     */
    fun markOrderAsComplete(orderId: Long) {
        viewModelScope.launch {
            try {
                // 检查API是否已配置
                if (!_apiConfigured.value || !::orderRepository.isInitialized) {
                    return@launch
                }

                val result = orderRepository.updateOrderStatus(orderId, "completed")
                if (result.isSuccess) {
                    // 刷新订单详情以显示更新的状态
                    loadOrderDetails(orderId)
                } else {
                    Log.e("OrderViewModel", "将订单标记为已完成时出错", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e("OrderViewModel", "将订单标记为已完成时出错", e)
            }
        }
    }

    /**
     * 打印订单
     */
    fun printOrder(orderId: Long) {
        viewModelScope.launch {
            try {
                // 检查API是否已配置
                if (!_apiConfigured.value || !::orderRepository.isInitialized) {
                    return@launch
                }

                // 从API获取订单以确保我们拥有最新的数据
                val result = orderRepository.getOrder(orderId)

                if (result.isSuccess) {
                    val order = result.getOrNull()
                    if (order != null) {
                        val success = printService.printOrder(order)
                        if (success) {
                            // 在本地数据库中标记为已打印
                            orderRepository.markOrderAsPrinted(orderId)

                            // 刷新订单详情以显示打印状态
                            loadOrderDetails(orderId)
                        } else {
                            // 处理打印失败
                            Log.e("OrderViewModel", "打印订单失败")
                        }
                    }
                } else {
                    Log.e("OrderViewModel", "获取要打印的订单时出错", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e("OrderViewModel", "打印订单时出错", e)
            }
        }
    }

    /**
     * 打印所有未打印的订单
     */
    fun printAllUnprintedOrders() {
        viewModelScope.launch {
            _printAllState.value = PrintAllState.Printing

            try {
                // 检查API是否已配置
                if (!_apiConfigured.value || !::orderRepository.isInitialized) {
                    _printAllState.value = PrintAllState.Error("请先在设置中配置API")
                    return@launch
                }

                val unprintedOrders = orderRepository.getUnprintedOrders()

                if (unprintedOrders.isEmpty()) {
                    _printAllState.value = PrintAllState.NoOrdersToPrint
                    return@launch
                }

                var totalPrinted = 0
                var totalFailed = 0

                for (orderEntity in unprintedOrders) {
                    // 从API获取完整订单
                    val result = orderRepository.getOrder(orderEntity.id)

                    if (result.isSuccess) {
                        val order = result.getOrNull()
                        if (order != null) {
                            val success = printService.printOrder(order)
                            if (success) {
                                orderRepository.markOrderAsPrinted(order.id)
                                totalPrinted++
                            } else {
                                totalFailed++
                            }
                        } else {
                            totalFailed++
                        }
                    } else {
                        totalFailed++
                    }
                }

                _printAllState.value = PrintAllState.Completed(totalPrinted, totalFailed)

                // 刷新订单以更新UI中的打印状态
                refreshOrders()
            } catch (e: Exception) {
                Log.e("OrderViewModel", "打印所有未打印订单时出错", e)
                _printAllState.value = PrintAllState.Error(e.message ?: "未知错误")
            }
        }
    }

    fun resetPrintAllState() {
        _printAllState.value = PrintAllState.Idle
    }

    // 清理当前查看的订单ID
    fun clearCurrentViewingOrder() {
        currentViewingOrderId = null
    }

}

// UI States
sealed class OrdersUiState {
    object Loading : OrdersUiState()
    object Empty : OrdersUiState()
    data class Success(val orders: List<OrderEntity>) : OrdersUiState()
    data class Error(val message: String) : OrdersUiState()
    object ApiNotConfigured : OrdersUiState() // 添加一个新状态，表示API未配置
}

sealed class OrderDetailState {
    object Loading : OrderDetailState()
    data class Success(val order: OrderEntity) : OrderDetailState()
    data class Error(val message: String) : OrderDetailState()
}

sealed class PrintAllState {
    object Idle : PrintAllState()
    object Printing : PrintAllState()
    object NoOrdersToPrint : PrintAllState()
    data class Completed(val printed: Int, val failed: Int) : PrintAllState()
    data class Error(val message: String) : PrintAllState()
}