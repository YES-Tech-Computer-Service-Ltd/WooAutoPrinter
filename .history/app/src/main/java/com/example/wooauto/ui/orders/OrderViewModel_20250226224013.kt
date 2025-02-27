package com.example.wooauto.ui.orders

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.data.api.RetrofitClient
import com.example.wooauto.data.database.AppDatabase
import com.example.wooauto.data.database.entities.OrderEntity
import com.example.wooauto.data.repositories.OrderRepository
import com.example.wooauto.utils.PreferencesManager
import com.example.wooauto.utils.PrintService
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class OrderViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    private val printService = PrintService(application)

    // Repository instance
    private lateinit var orderRepository: OrderRepository

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

    // 添加一个变量来跟踪正在进行的操作
    private val _pendingOperations = MutableStateFlow<Set<Long>>(emptySet())

    // 自动刷新任务
    private var autoRefreshJob: Job? = null

    // 配送方式筛选
    private val _selectedDeliveryMethod = MutableStateFlow<String?>(null)
    val selectedDeliveryMethod = _selectedDeliveryMethod.asStateFlow()

    // 配送日期筛选
    private val _selectedDeliveryDate = MutableStateFlow<String?>(null)
    val selectedDeliveryDate = _selectedDeliveryDate.asStateFlow()

    // 可用的配送日期列表
    private val _availableDeliveryDates = MutableStateFlow<List<String>>(emptyList())
    val availableDeliveryDates: StateFlow<List<String>> = _availableDeliveryDates

    private val TAG = "OrderVM_DEBUG"

    init {
        checkApiCredentials()
        startAutoRefresh()
        loadAvailableDeliveryDates()
    }

    // 启动自动刷新任务
    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(30000) // 每30秒自动刷新一次
                if (_apiConfigured.value && ::orderRepository.isInitialized) {
                    Log.d(TAG, "执行自动刷新...")
                    refreshOrders()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 取消自动刷新任务
        autoRefreshJob?.cancel()
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

                // 修改数据流合并逻辑，加入配送方式和日期筛选
                combine(
                    _searchQuery,
                    _selectedStatusFilter,
                    _selectedDeliveryMethod,
                    _selectedDeliveryDate,
                    orderRepository.getAllOrdersFlow()
                ) { query, statusFilter, deliveryMethod, deliveryDate, orders ->
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

                        val matchesDeliveryMethod = if (deliveryMethod == null) {
                            true
                        } else {
                            order.orderMethod == deliveryMethod
                        }

                        val matchesDeliveryDate = if (deliveryDate == null) {
                            true
                        } else {
                            order.deliveryDate == deliveryDate
                        }

                        matchesQuery && matchesStatus && matchesDeliveryMethod && matchesDeliveryDate
                    }

                    Log.d(TAG, """
                        订单流更新:
                        - 总订单数: ${orders.size}
                        - 过滤后数量: ${filteredOrders.size}
                        - 搜索词: $query
                        - 状态筛选: $statusFilter
                        - 配送方式: $deliveryMethod
                        - 配送日期: $deliveryDate
                    """.trimIndent())

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

                val websiteUrl = preferencesManager.websiteUrl.first()
                val apiKey = preferencesManager.apiKey.first()
                val apiSecret = preferencesManager.apiSecret.first()
                Log.d(TAG, "API配置: URL=${websiteUrl}, Key=${apiKey.take(4)}***, Secret=${apiSecret.take(4)}***")

                val result = orderRepository.refreshOrders()

                when {
                    result.isSuccess -> {
                        val orders = result.getOrNull()
                        Log.d(TAG, "刷新订单成功, 获取订单数: ${orders?.size ?: 0}")
                        orders?.forEach {
                            Log.d(TAG, "订单: ID=${it.id}, 编号=${it.number}, 状态=${it.status}")
                            Log.d(TAG, "配送信息: 方式=${it.orderMethod}, 日期=${it.deliveryDate}, 时间=${it.deliveryTime}")
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
        viewModelScope.launch {
            _orderDetailState.value = OrderDetailState.Loading
            Log.d(TAG, "===== 开始加载订单详情 ID: $orderId =====")

            try {
                // 先从数据库获取基本信息
                val localOrder = orderRepository.getOrderById(orderId)
                Log.d(TAG, """
                    本地数据库订单信息:
                    - ID: ${localOrder?.id}
                    - 订单编号: ${localOrder?.number}
                    - 配送方式: ${localOrder?.orderMethod ?: "未设置"}
                    - 配送日期: ${localOrder?.deliveryDate ?: "未设置"}
                    - 配送时间: ${localOrder?.deliveryTime ?: "未设置"}
                    - 小费: ${localOrder?.tip ?: "未设置"}
                    - 配送费: ${localOrder?.deliveryFee ?: "未设置"}
                """.trimIndent())

                if (localOrder != null) {
                    _orderDetailState.value = OrderDetailState.Success(localOrder)
                    
                    // 尝试从API刷新数据
                    val result = orderRepository.getOrder(orderId)
                    if (result.isSuccess) {
                        Log.d(TAG, "成功从API刷新订单数据")
                        // 数据会自动更新到数据库，UI会通过Flow自动更新
                    } else {
                        Log.e(TAG, "从API刷新订单数据失败: ${result.exceptionOrNull()?.message}")
                    }
                } else {
                    // 如果本地没有数据，尝试从API获取
                    val result = orderRepository.getOrder(orderId)
                    if (result.isSuccess) {
                        val order = result.getOrNull()
                        if (order != null) {
                            Log.d(TAG, "成功从API获取订单数据")
                            // 数据会自动保存到数据库并更新UI
                        } else {
                            _orderDetailState.value = OrderDetailState.Error("订单不存在")
                        }
                    } else {
                        _orderDetailState.value = OrderDetailState.Error(
                            result.exceptionOrNull()?.message ?: "加载订单失败"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载订单详情时发生错误", e)
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

                // 检查订单是否已经在处理中
                val currentOperations = _pendingOperations.value
                if (orderId in currentOperations) {
                    Log.d(TAG, "订单 $orderId 已经在处理中，忽略重复操作")
                    return@launch
                }

                // 将此订单添加到正在处理的操作集合中
                _pendingOperations.value = currentOperations + orderId

                // 更新前先将UI显示为刷新状态
                _isRefreshing.value = true

                Log.d(TAG, "开始将订单 $orderId 标记为已完成")
                val result = orderRepository.updateOrderStatus(orderId, "completed")

                if (result.isSuccess) {
                    Log.d(TAG, "订单状态更新成功，刷新UI")

                    // 从数据库重新加载该订单以确保UI更新
                    val updatedOrder = orderRepository.getOrderById(orderId)
                    Log.d(TAG, "从数据库获取更新后的订单: ${updatedOrder?.status}")

                    // 刷新所有订单以更新UI
                    refreshOrders()

                    // 如果正在查看订单详情，也更新详情页
                    if (_orderDetailState.value is OrderDetailState.Success) {
                        val currentOrder = (_orderDetailState.value as OrderDetailState.Success).order
                        if (currentOrder.id == orderId) {
                            loadOrderDetails(orderId)
                        }
                    }
                } else {
                    // 处理错误情况
                    Log.e(TAG, "将订单标记为已完成时出错", result.exceptionOrNull())
                    _isRefreshing.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "将订单标记为已完成时出错", e)
                _isRefreshing.value = false
            } finally {
                // 从正在处理的操作集合中移除此订单
                _pendingOperations.value = _pendingOperations.value - orderId
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

                            // 刷新订单列表以更新UI
                            refreshOrders()
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

    /**
     * 验证API数据
     * 用于调试
     */
    fun validateOrderData(orderId: Long) {
        viewModelScope.launch {
            try {
                if (!::orderRepository.isInitialized) {
                    Log.e(TAG, "仓库未初始化，无法验证数据")
                    return@launch
                }

                val result = orderRepository.validateOrderData(orderId)
                if (result.isSuccess) {
                    val data = result.getOrNull()
                    Log.d(TAG, "API数据验证结果: $data")
                    // 可以在这里添加UI提示
                } else {
                    Log.e(TAG, "API数据验证失败: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "验证时发生异常", e)
            }
        }
    }

    fun resetPrintAllState() {
        _printAllState.value = PrintAllState.Idle
    }

    private fun loadAvailableDeliveryDates() {
        viewModelScope.launch {
            try {
                orderRepository.getDistinctDeliveryDatesFlow()
                    .collect { dates ->
                        _availableDeliveryDates.value = dates
                    }
            } catch (e: Exception) {
                Log.e(TAG, "加载配送日期时出错", e)
            }
        }
    }

    // 更新配送方式筛选
    fun updateDeliveryMethodFilter(method: String?) {
        _selectedDeliveryMethod.value = method
    }

    // 更新配送日期筛选
    fun updateDeliveryDateFilter(date: String?) {
        _selectedDeliveryDate.value = date
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