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
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

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
    
    // 添加缺失的单个订单流映射
    private val _orderByIdFlow = mutableMapOf<Long, MutableStateFlow<Order?>>()
    
    // 添加协程作用域
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
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
        
        // 首先检查缓存
        if (!isOrdersCached) {
            refreshOrders()
        }
        
        // 尝试从数据库获取最新数据
        val orderEntity = orderDao.getOrderById(orderId)
        val order = orderEntity?.let { mapToOrderModel(it) } ?: cachedOrders.find { it.id == orderId }
        
        // 如果找到订单，更新或创建对应的Flow
        order?.let {
            // 如果Flow不存在，创建一个新的
            if (!_orderByIdFlow.containsKey(orderId)) {
                _orderByIdFlow[orderId] = MutableStateFlow(it)
            } else {
                // 如果Flow已存在，更新它的值
                _orderByIdFlow[orderId]?.value = it
            }
        }
        
        return@withContext order
    }

    /**
     * 获取特定订单的状态流，允许组件监听单个订单的变化
     * @param orderId 订单ID
     * @return 订单状态流
     */
    override fun getOrderByIdFlow(orderId: Long): Flow<Order?> {
        // 如果Flow不存在，创建一个新的空Flow
        if (!_orderByIdFlow.containsKey(orderId)) {
            _orderByIdFlow[orderId] = MutableStateFlow(null)
            
            // 异步加载订单数据
            repositoryScope.launch {
                val order = getOrderById(orderId)
                _orderByIdFlow[orderId]?.value = order
            }
        }
        
        return _orderByIdFlow[orderId]!!.asStateFlow()
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

    /**
     * 专门用于后台轮询服务的方法，查询处理中的订单但不会影响UI显示
     * 此方法不会修改_ordersFlow，因此不会干扰用户当前的筛选状态
     */
    override suspend fun refreshProcessingOrdersForPolling(afterDate: Date?): Result<List<Order>> = withContext(Dispatchers.IO) {
        Log.d("OrderRepositoryImpl", "【轮询刷新】开始查询处理中订单(UI安全)")
        
        try {
            val config = settingsRepository.getWooCommerceConfig()
            if (!config.isValid()) {
                Log.e("OrderRepositoryImpl", "API配置无效，请检查设置")
                return@withContext Result.failure(ApiError.fromHttpCode(401, "API配置无效，请检查设置"))
            }
            
            val api = getApi(config)
            
            try {
                // 使用固定的"processing"状态，确保只查询处理中的订单
                Log.d("OrderRepositoryImpl", "【轮询刷新】调用API: getOrders(page=1, perPage=100, status=processing)")
                
                // 调用API获取处理中订单
                val response = api.getOrders(1, 100, "processing")
                
                Log.d("OrderRepositoryImpl", "【轮询刷新】API返回 ${response.size} 个处理中订单")
                
                // 记录返回订单的状态分布
                val statusCounts = response.groupBy { order -> order.status }.mapValues { entry -> entry.value.size }
                Log.d("OrderRepositoryImpl", "【轮询刷新】状态分布: $statusCounts")
                
                // 转换为领域模型
                val orders = response.map { orderDto -> orderDto.toOrder() }
                
                // 更新数据库中的处理中订单，但不影响当前缓存
                val entities = orders.map { order -> OrderMapper.mapDomainToEntity(order) }
                
                // 注意：不同于普通刷新，这里只删除和添加processing状态的订单
                orderDao.deleteOrdersByStatus("processing") 
                orderDao.insertOrders(entities)
                
                // 注意：这里特意不更新_ordersFlow，以避免影响UI显示
                Log.d("OrderRepositoryImpl", "【轮询刷新】成功获取处理中订单，不影响UI状态")
                
                return@withContext Result.success(orders)
            } catch (e: Exception) {
                Log.e("OrderRepositoryImpl", "【轮询刷新】API调用异常: ${e.message}", e)
                throw e
            }
        } catch (e: Exception) {
            val error = when (e) {
                is ApiError -> e
                else -> ApiError.fromException(e)
            }
            
            Log.e("OrderRepositoryImpl", "【轮询刷新】刷新订单数据失败: ${error.message}", e)
            return@withContext Result.failure(error)
        }
    }

    override suspend fun markOrderAsPrinted(orderId: Long): Boolean = withContext(Dispatchers.IO) {
        Log.d("OrderRepositoryImpl", "标记订单为已打印: $orderId")
        
        try {
            // 更新数据库
            orderDao.updateOrderPrintStatus(orderId, true)
            
            // 更新缓存和流
            val updatedList = cachedOrders.map { 
                if (it.id == orderId) it.copy(isPrinted = true) else it 
            }
            cachedOrders = updatedList
            _ordersFlow.value = updatedList
            
            // 更新状态分组
            updateOrdersByStatus()
            
            // 获取并更新具体订单
            val orderEntity = orderDao.getOrderById(orderId)
            orderEntity?.let {
                // 单独发送更新事件
                _orderByIdFlow[orderId]?.emit(mapToOrderModel(it))
            }
            
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

    // 添加辅助函数：将OrderEntity转换为Order模型
    private fun mapToOrderModel(entity: com.example.wooauto.data.local.entities.OrderEntity): Order {
        return Order(
            id = entity.id,
            number = entity.number.toString(),
            status = entity.status,
            dateCreated = Date(entity.dateCreated),
            customerName = entity.customerName,
            contactInfo = entity.contactInfo,
            billingInfo = entity.billingAddress,
            paymentMethod = entity.paymentMethod,
            total = entity.total,
            totalTax = entity.totalTax,
            items = entity.lineItems.map { item ->
                OrderItem(
                    id = 0, // 行项目ID不可用，使用默认值
                    productId = item.productId,
                    name = item.name,
                    quantity = item.quantity,
                    price = item.price,
                    total = item.total,
                    subtotal = item.price, // 单项价格作为小计
                    image = "", // 图片URL不可用，使用空字符串
                    options = emptyList() // 选项不可用，使用空列表
                )
            },
            isPrinted = entity.isPrinted,
            notificationShown = entity.notificationShown,
            notes = entity.customerNote
        )
    }
}