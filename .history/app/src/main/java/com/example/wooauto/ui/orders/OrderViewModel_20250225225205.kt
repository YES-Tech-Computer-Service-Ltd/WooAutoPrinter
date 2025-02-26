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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

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

    init {
        initializeRepository()
    }

    private fun initializeRepository() {
        viewModelScope.launch {
            try {
                // Initialize repository with API credentials
                val websiteUrl = preferencesManager.websiteUrl.first()
                val apiKey = preferencesManager.apiKey.first()
                val apiSecret = preferencesManager.apiSecret.first()

                if (websiteUrl.isNotEmpty() && apiKey.isNotEmpty() && apiSecret.isNotEmpty()) {
                    val apiService = RetrofitClient.getWooCommerceApiService(websiteUrl)
                    val orderDao = AppDatabase.getInstance(getApplication()).orderDao()
                    orderRepository = OrderRepository(
                        orderDao,
                        apiService,
                        apiKey,
                        apiSecret
                    )

                    // Observe orders from database based on filters
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
                        Log.e("OrderViewModel", "获取订单时发生错误", e)
                        emit(OrdersUiState.Error(e.message ?: "未知错误"))
                    }.collect { state ->
                        _uiState.value = state
                    }

                    // Initial data load
                    refreshOrders()
                } else {
                    _uiState.value = OrdersUiState.Error("API配置缺失，请在设置中配置API信息")
                }
            } catch (e: Exception) {
                Log.e("OrderViewModel", "初始化 OrderViewModel 时发生错误", e)
                _uiState.value = OrdersUiState.Error(e.message ?: "未知错误")
            }
        }
    }

    /**
     * Refresh orders from the API
     */
    fun refreshOrders() {
        viewModelScope.launch {
            try {
                if (!::orderRepository.isInitialized) {
                    _uiState.value = OrdersUiState.Error("正在初始化，请稍后再试")
                    return@launch
                }

                _isRefreshing.value = true
                val result = orderRepository.refreshOrders()
                result.onFailure { exception ->
                    Log.e("OrderViewModel", "刷新订单失败", exception)
                    _uiState.value = OrdersUiState.Error(exception.message ?: "刷新订单失败")
                }
            } catch (e: Exception) {
                Log.e("OrderViewModel", "刷新订单时发生错误", e)
                _uiState.value = OrdersUiState.Error(e.message ?: "刷新订单时发生错误")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Update search query
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Update status filter
     */
    fun updateStatusFilter(status: String?) {
        _selectedStatusFilter.value = status
    }

    /**
     * Load order details
     */
    fun loadOrderDetails(orderId: Long) {
        viewModelScope.launch {
            _orderDetailState.value = OrderDetailState.Loading

            try {
                // Try to get from local database first
                val localOrder = orderRepository.getOrderById(orderId)

                if (localOrder != null) {
                    _orderDetailState.value = OrderDetailState.Success(localOrder)
                }

                // Then refresh from API to get the latest data
                val result = orderRepository.getOrder(orderId)

                if (result.isSuccess) {
                    val order = result.getOrNull()
                    if (order != null) {
                        // API order available, overwrite local order in the UI
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
                                lineItemsJson = "",  // This will be filled by the repository
                                isPrinted = order.isPrinted,
                                notificationShown = order.notificationShown
                            )
                        )
                    }
                } else {
                    // If we don't have the order locally either, show error
                    if (localOrder == null) {
                        _orderDetailState.value = OrderDetailState.Error(
                            result.exceptionOrNull()?.message ?: "Failed to load order"
                        )
                    }
                    // Otherwise keep showing the local order
                }
            } catch (e: Exception) {
                Log.e("OrderViewModel", "Error loading order details", e)
                _orderDetailState.value = OrderDetailState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Mark order as complete
     */
    fun markOrderAsComplete(orderId: Long) {
        viewModelScope.launch {
            try {
                val result = orderRepository.updateOrderStatus(orderId, "completed")
                if (result.isSuccess) {
                    // Refresh order details to show updated status
                    loadOrderDetails(orderId)
                } else {
                    Log.e("OrderViewModel", "Error marking order as complete", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e("OrderViewModel", "Error marking order as complete", e)
            }
        }
    }

    /**
     * Print order
     */
    fun printOrder(orderId: Long) {
        viewModelScope.launch {
            try {
                // Get order from API to ensure we have the latest data
                val result = orderRepository.getOrder(orderId)

                if (result.isSuccess) {
                    val order = result.getOrNull()
                    if (order != null) {
                        val success = printService.printOrder(order)
                        if (success) {
                            // Mark as printed in local database
                            orderRepository.markOrderAsPrinted(orderId)

                            // Refresh order details to show printed status
                            loadOrderDetails(orderId)
                        } else {
                            // Handle printing failure
                            Log.e("OrderViewModel", "Failed to print order")
                        }
                    }
                } else {
                    Log.e("OrderViewModel", "Error getting order for printing", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e("OrderViewModel", "Error printing order", e)
            }
        }
    }

    /**
     * Print all unprinted orders
     */
    fun printAllUnprintedOrders() {
        viewModelScope.launch {
            _printAllState.value = PrintAllState.Printing

            try {
                val unprintedOrders = orderRepository.getUnprintedOrders()

                if (unprintedOrders.isEmpty()) {
                    _printAllState.value = PrintAllState.NoOrdersToPrint
                    return@launch
                }

                var totalPrinted = 0
                var totalFailed = 0

                for (orderEntity in unprintedOrders) {
                    // Get full order from API
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

                // Refresh orders to update UI with printed status
                refreshOrders()
            } catch (e: Exception) {
                Log.e("OrderViewModel", "Error printing all unprinted orders", e)
                _printAllState.value = PrintAllState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetPrintAllState() {
        _printAllState.value = PrintAllState.Idle
    }
}

// UI States
sealed class OrdersUiState {
    object Loading : OrdersUiState()
    object Empty : OrdersUiState()
    data class Success(val orders: List<OrderEntity>) : OrdersUiState()
    data class Error(val message: String) : OrdersUiState()
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