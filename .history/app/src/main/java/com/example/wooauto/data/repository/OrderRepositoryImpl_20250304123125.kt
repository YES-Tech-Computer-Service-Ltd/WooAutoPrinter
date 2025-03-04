package com.example.wooauto.data.repository

import android.util.Log
import com.example.wooauto.data.mappers.OrderMapper
import com.example.wooauto.data.remote.WooCommerceApi
import com.example.wooauto.data.remote.WooCommerceApiFactory
import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.data.remote.dto.OrderDto
import com.example.wooauto.data.remote.dto.toOrder
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.example.wooauto.domain.repositories.DomainSettingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import com.example.wooauto.data.remote.ApiError
import com.example.wooauto.data.local.dao.OrderDao

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val orderDao: OrderDao,
    private val settingsRepository: DomainSettingRepository,
    private val apiFactory: WooCommerceApiFactory
) : DomainOrderRepository {

    private var cachedOrders: List<Order> = emptyList()
    private var isOrdersCached = false
    private val _ordersFlow = MutableStateFlow<List<Order>>(emptyList())
    private val _ordersByStatusFlow = MutableStateFlow<Map<String, List<Order>>>(emptyMap())
    
    private suspend fun getApi(config: WooCommerceConfig? = null): WooCommerceApi {
        val actualConfig = config ?: settingsRepository.getWooCommerceConfig()
        return apiFactory.createApi(actualConfig)
    }

    override suspend fun getOrders(status: String?): List<Order> = withContext(Dispatchers.IO) {
        Log.d("OrderRepositoryImpl", "获取订单列表，状态: ${status ?: "全部"}")
        
        // 尝试从数据库获取数据
        val localOrders = if (status != null) {
            orderDao.getOrdersByStatus(status).first()
        } else {
            orderDao.getAllOrders().first()
        }
        
        // 如果本地数据为空，则尝试从API获取
        if (localOrders.isEmpty()) {
            try {
                val result = refreshOrders(status)
                if (result.isSuccess) {
                    return@withContext result.getOrDefault(emptyList())
                }
            } catch (e: Exception) {
                Log.e("OrderRepositoryImpl", "刷新订单失败: ${e.message}")
            }
        }
        
        // 返回本地数据（可能是刚刚刷新的或原有的）
        return@withContext OrderMapper.mapEntityListToDomainList(
            if (status != null) {
                orderDao.getOrdersByStatus(status).first()
            } else {
                orderDao.getAllOrders().first()
            }
        )
    }

    override suspend fun getOrderById(orderId: Long): Order? = withContext(Dispatchers.IO) {
        Log.d("OrderRepositoryImpl", "按ID获取订单: $orderId")
        
        if (!isOrdersCached) {
            refreshOrders()
        }
        
        return@withContext cachedOrders.find { it.id == orderId }
    }

    override suspend fun updateOrderStatus(orderId: Long, newStatus: String): Result<Order> = withContext(Dispatchers.IO) {
        Log.d("OrderRepositoryImpl", "更新订单状态: $orderId -> $newStatus")
        
        try {
            val api = getApi()
            val statusMap = mapOf("status" to newStatus)
            val response = api.updateOrder(orderId, statusMap)
            val updatedOrder = response.toOrder()
            
            // 更新缓存
            val updatedList = cachedOrders.map { 
                if (it.id == orderId) updatedOrder else it 
            }
            cachedOrders = updatedList
            _ordersFlow.value = updatedList
            
            // 更新状态分组
            updateOrdersByStatus()
            
            return@withContext Result.success(updatedOrder)
        } catch (e: Exception) {
            Log.e("OrderRepositoryImpl", "更新订单状态失败", e)
            return@withContext Result.failure(e)
        }
    }

    override suspend fun refreshOrders(status: String?, afterDate: Date?): Result<List<Order>> = withContext(Dispatchers.IO) {
        Log.d("OrderRepositoryImpl", "【状态刷新】开始刷新订单，状态: ${status ?: "全部"}")
        
        try {
            val config = settingsRepository.getWooCommerceConfig()
            if (!config.isValid()) {
                Log.e("OrderRepositoryImpl", "API配置无效，请检查设置")
                return@withContext Result.failure(ApiError.fromHttpCode(401, "API配置无效，请检查设置"))
            }
            
            val api = getApi(config)
            
            // 状态映射表 - 定义在方法级别，使其在整个方法中可用
            val statusMap = mapOf(
                "处理中" to "processing",
                "待付款" to "pending",
                "已完成" to "completed",
                "已取消" to "cancelled",
                "已退款" to "refunded",
                "失败" to "failed",
                "暂挂" to "on-hold",
                // 反向映射（便于双向检查）
                "processing" to "处理中",
                "pending" to "待付款",
                "completed" to "已完成",
                "cancelled" to "已取消",
                "refunded" to "已退款",
                "failed" to "失败",
                "on-hold" to "暂挂"
            )
            
            // WooCommerce API支持的所有状态
            val validApiStatuses = listOf(
                "pending", "processing", "on-hold", "completed", 
                "cancelled", "refunded", "failed", "trash", "any", 
                "auto-draft", "checkout-draft"
            )
            
            // 准备API需要的状态参数 - 确保使用英文状态名
            val apiStatus = if (status != null) {
                val cleanStatus = status.trim().lowercase()
                
                // 直接判断输入是否已经是有效的API状态
                if (validApiStatuses.contains(cleanStatus)) {
                    // 如果已经是有效的API状态，直接使用
                    Log.d("OrderRepositoryImpl", "【状态刷新】直接使用有效状态: '$cleanStatus'")
                    cleanStatus
                } else {
                    // 如果不是有效API状态，尝试从中文映射到英文
                    val mappedStatus = statusMap[cleanStatus]
                    
                    if (mappedStatus != null && validApiStatuses.contains(mappedStatus)) {
                        // 映射成功且是有效状态
                        Log.d("OrderRepositoryImpl", "【状态刷新】状态映射成功: '$cleanStatus' -> '$mappedStatus'")
                        mappedStatus
                    } else {
                        // 无法映射或映射结果不是有效状态，使用"any"
                        Log.w("OrderRepositoryImpl", "【状态刷新】警告：无法将 '$cleanStatus' 映射为有效的API状态，使用 'any'")
                        "any"
                    }
                }
            } else {
                null
            }
            
            try {
                // 清晰地记录完整的API调用信息
                Log.d("OrderRepositoryImpl", "【状态刷新】调用API: getOrders(page=1, perPage=100, status=$apiStatus)")
                
                // 使用API状态值调用API
                val response = api.getOrders(1, 100, apiStatus)
                
                Log.d("OrderRepositoryImpl", "【状态刷新】API返回 ${response.size} 个订单")
                
                // 记录返回订单的状态分布
                val statusCounts = response.groupBy { order -> order.status }.mapValues { entry -> entry.value.size }
                Log.d("OrderRepositoryImpl", "【状态刷新】状态分布: $statusCounts")
                
                // 如果请求了特定状态，但返回的订单中没有该状态的订单，记录警告
                if (apiStatus != null && !statusCounts.containsKey(apiStatus)) {
                    Log.w("OrderRepositoryImpl", "【状态刷新】警告：API没有返回状态为 '$apiStatus' 的订单")
                }
                
                // 转换为领域模型
                val orders = response.map { orderDto -> orderDto.toOrder() }
                
                // 更新缓存
                cachedOrders = orders
                isOrdersCached = true
                _ordersFlow.value = orders
                
                // 更新数据库
                val entities = orders.map { order -> OrderMapper.mapDomainToEntity(order) }
                orderDao.deleteAllOrders() // 清除旧数据
                orderDao.insertOrders(entities) // 插入新数据
                
                // 如果API没有正确过滤状态，在本地进行过滤
                val finalOrders = if (status != null) {
                    // 再次进行本地过滤，确保只返回匹配状态的订单
                    val filteredOrders = orders.filter { 
                        val orderStatus = it.status
                        val matches = orderStatus == status || 
                                       (statusMap[status] == orderStatus) || 
                                       (statusMap.entries.find { entry -> entry.value == status }?.key == orderStatus)
                        
                        if (!matches) {
                            Log.d("OrderRepositoryImpl", "【状态过滤】订单 ${it.id} 状态 '$orderStatus' 与请求状态 '$status' 不匹配")
                        }
                        
                        matches
                    }
                    
                    Log.d("OrderRepositoryImpl", "【状态过滤】本地过滤后，符合状态 '$status' 的订单数量: ${filteredOrders.size}")
                    filteredOrders
                } else {
                    orders
                }
                
                // 更新状态分组
                updateOrdersByStatus()
                
                return@withContext Result.success(finalOrders)
            } catch (e: Exception) {
                Log.e("OrderRepositoryImpl", "API调用异常: ${e.message}", e)
                
                // 提供一个更具体的错误消息
                val errorMessage = when {
                    e.message?.contains("status[0] is not one of") == true -> 
                        "状态参数'$apiStatus'格式不正确或不在API支持的有效状态列表中"
                    e.message?.contains("Invalid parameter") == true -> 
                        "API参数无效: ${e.message}"
                    else -> e.message
                }
                Log.e("OrderRepositoryImpl", "更具体的错误: $errorMessage")
                
                throw e
            }
        } catch (e: Exception) {
            val error = when (e) {
                is ApiError -> e
                else -> ApiError.fromException(e)
            }
            
            Log.e("OrderRepositoryImpl", "刷新订单数据失败: ${error.message}", e)
            return@withContext Result.failure(error)
        }
    }

    override suspend fun markOrderAsPrinted(orderId: Long): Boolean = withContext(Dispatchers.IO) {
        Log.d("OrderRepositoryImpl", "标记订单为已打印: $orderId")
        
        try {
            // 更新数据库
            orderDao.updateOrderPrintStatus(orderId, true)
            
            // 更新缓存
            val updatedList = cachedOrders.map { 
                if (it.id == orderId) it.copy(isPrinted = true) else it 
            }
            cachedOrders = updatedList
            _ordersFlow.value = updatedList
            
            // 更新状态分组
            updateOrdersByStatus()
            
            return@withContext true
        } catch (e: Exception) {
            Log.e("OrderRepositoryImpl", "标记订单为已打印失败", e)
            return@withContext false
        }
    }

    override suspend fun markOrderNotificationShown(orderId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d("OrderRepositoryImpl", "标记订单通知为已显示: $orderId")
        
        try {
            // 更新数据库
            orderDao.updateOrderNotificationStatus(orderId, true)
            
            // 更新缓存
            val updatedList = cachedOrders.map { 
                if (it.id == orderId) it.copy(notificationShown = true) else it 
            }
            cachedOrders = updatedList
            _ordersFlow.value = updatedList
            
            // 更新状态分组
            updateOrdersByStatus()
            
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            val error = when (e) {
                is ApiError -> e
                else -> ApiError.fromException(e)
            }
            
            Log.e("OrderRepositoryImpl", "标记订单通知为已显示失败: ${error.message}")
            return@withContext Result.failure(error)
        }
    }

    override fun getAllOrdersFlow(): Flow<List<Order>> {
        return _ordersFlow.asStateFlow()
    }

    override fun getOrdersByStatusFlow(status: String): Flow<List<Order>> {
        Log.d("OrderRepositoryImpl", "【状态调试】获取状态为 '$status' 的订单流")
        
        // 创建中英文状态映射表
        val statusMap = mapOf(
            "处理中" to "processing",
            "待付款" to "pending",
            "已完成" to "completed",
            "已取消" to "cancelled",
            "已退款" to "refunded",
            "失败" to "failed",
            "暂挂" to "on-hold",
            "processing" to "处理中",
            "pending" to "待付款",
            "completed" to "已完成",
            "cancelled" to "已取消",
            "refunded" to "已退款",
            "failed" to "失败",
            "on-hold" to "暂挂"
        )
        
        return _ordersFlow.asStateFlow().map { allOrders ->
            // 严格过滤，确保状态完全匹配
            val filteredOrders = allOrders.filter { order ->
                // 检查订单状态是否与请求的状态匹配
                // 1. 直接匹配
                // 2. 订单状态的中文名与请求的状态匹配
                // 3. 订单状态的英文名与请求的状态匹配
                val matchesDirect = order.status == status
                val matchesViaChinese = statusMap[order.status] == status
                val matchesViaEnglish = statusMap[status] == order.status
                
                val matches = matchesDirect || matchesViaChinese || matchesViaEnglish
                
                if (!matches) {
                    Log.d("OrderRepositoryImpl", "【状态过滤】订单 ${order.id} 状态 '${order.status}' 与请求状态 '$status' 不匹配")
                }
                
                matches
            }
            
            Log.d("OrderRepositoryImpl", "【状态调试】过滤后找到 ${filteredOrders.size} 个 '$status' 状态的订单")
            filteredOrders
        }
    }

    override suspend fun searchOrders(query: String): List<Order> = withContext(Dispatchers.IO) {
        Log.d("OrderRepositoryImpl", "搜索订单: $query")
        
        if (!isOrdersCached) {
            refreshOrders()
        }
        
        val searchTerms = query.lowercase().split(" ")
        return@withContext cachedOrders.filter { order ->
            searchTerms.all { term ->
                order.customerName.lowercase().contains(term) ||
                order.contactInfo.lowercase().contains(term) ||
                order.number.toString().contains(term)
            }
        }
    }

    override suspend fun testConnection(config: WooCommerceConfig?): Boolean = withContext(Dispatchers.IO) {
        Log.d("OrderRepositoryImpl", "测试API连接")
        
        return@withContext try {
            val api = getApi(config)
            // 尝试获取一个订单作为连接测试
            api.getOrders(page = 1, perPage = 1)
            true // 如果没有异常，则认为连接成功
        } catch (e: Exception) {
            Log.e("OrderRepositoryImpl", "API连接测试失败", e)
            false
        }
    }

    private fun updateOrdersByStatus() {
        val groupedOrders = cachedOrders.groupBy { it.status }
        Log.d("OrderRepositoryImpl", "【状态分组】所有状态: ${groupedOrders.keys.joinToString()}")
        
        // 记录每个状态的订单数量
        groupedOrders.forEach { (status, orders) ->
            Log.d("OrderRepositoryImpl", "【状态分组】'$status' 状态有 ${orders.size} 个订单")
        }
        
        _ordersByStatusFlow.value = groupedOrders
    }

    override suspend fun clearCache() {
        Log.d("OrderRepositoryImpl", "清除订单缓存")
        isOrdersCached = false
        cachedOrders = emptyList()
        _ordersFlow.value = emptyList()
        _ordersByStatusFlow.value = emptyMap()
    }
}