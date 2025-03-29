package com.example.wooauto.data.repository

import android.util.Log
import com.example.wooauto.data.local.dao.OrderDao
import com.example.wooauto.data.local.entities.OrderEntity
import com.example.wooauto.data.mappers.OrderMapper
import com.example.wooauto.data.remote.WooCommerceApi
import com.example.wooauto.data.remote.WooCommerceApiFactory
import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.data.remote.dto.OrderDto
import com.example.wooauto.data.remote.dto.toOrder
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.OrderItem
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
//        Log.d("OrderRepositoryImpl", "按ID获取订单: $orderId")
        
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
//        Log.d("OrderRepositoryImpl", "更新订单状态: $orderId -> $newStatus")
        
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
//        Log.d("OrderRepositoryImpl", "【状态刷新】开始刷新订单，状态: ${status ?: "全部"}")
        
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
//                    Log.d("OrderRepositoryImpl", "【状态刷新】直接使用有效状态: '$cleanStatus'")
                    cleanStatus
                } else {
                    // 如果不是有效API状态，尝试从中文映射到英文
                    val mappedStatus = statusMap[cleanStatus]
                    
                    if (mappedStatus != null && validApiStatuses.contains(mappedStatus)) {
                        // 映射成功且是有效状态
//                        Log.d("OrderRepositoryImpl", "【状态刷新】状态映射成功: '$cleanStatus' -> '$mappedStatus'")
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
//                Log.d("OrderRepositoryImpl", "【状态刷新】调用API: getOrders(page=1, perPage=100, status=$apiStatus)")
                
                // 使用API状态值调用API
                val response = api.getOrders(1, 100, apiStatus)
                
//                Log.d("OrderRepositoryImpl", "【状态刷新】API返回 ${response.size} 个订单")
                
                // 记录返回订单的状态分布
                val statusCounts = response.groupBy { order -> order.status }.mapValues { entry -> entry.value.size }
//                Log.d("OrderRepositoryImpl", "【状态刷新】状态分布: $statusCounts")
                
                // 如果请求了特定状态，但返回的订单中没有该状态的订单，记录警告
                if (apiStatus != null && !statusCounts.containsKey(apiStatus)) {
                    Log.w("OrderRepositoryImpl", "【状态刷新】警告：API没有返回状态为 '$apiStatus' 的订单")
                }
                
                // 转换为领域模型
                val orders = response.map { orderDto -> orderDto.toOrder() }
                
                // 获取当前所有订单，创建ID到实体的映射
                val currentOrders = orderDao.getAllOrders().first()
                val currentOrderMap = currentOrders.associateBy { it.id }
                
                // 记录当前数据库中已打印订单的数量
                val printedOrdersInDb = currentOrders.filter { it.isPrinted }
//                Log.d("OrderRepositoryImpl", "【状态刷新】当前数据库中已打印订单数量: ${printedOrdersInDb.size}")
                
                // 记录内存中的订单状态以便对比
                val memoryOrderMap = cachedOrders.associateBy { it.id }
                val printedOrdersInMemory = cachedOrders.filter { it.isPrinted }
//                Log.d("OrderRepositoryImpl", "【状态刷新】当前内存中已打印订单数量: ${printedOrdersInMemory.size}")
                
                // 检查内存和数据库的状态差异
                if (printedOrdersInMemory.size != printedOrdersInDb.size) {
//                    Log.d("OrderRepositoryImpl", "【状态刷新】内存和数据库打印状态不一致，内存: ${printedOrdersInMemory.size}，数据库: ${printedOrdersInDb.size}")
                }
                
                // 更新缓存，以内存状态为准
                val entitiesWithPrintStatus = orders.map { order -> 
                    val entity = OrderMapper.mapDomainToEntity(order)
                    
                    // 首先检查内存中是否存在该订单
                    val memoryOrder = memoryOrderMap[entity.id]
                    
                    if (memoryOrder != null) {
                        // 如果内存中存在，使用内存中的打印状态
                        if (memoryOrder.isPrinted) {
//                            Log.d("OrderRepositoryImpl", "【状态刷新】使用内存中的状态，保留订单 #${entity.number} (ID: ${entity.id}) 的已打印状态")
                            entity.copy(isPrinted = true)
                        } else {
                            entity
                        }
                    } else {
                        // 如果内存中不存在，检查数据库
                        val existingOrder = currentOrderMap[entity.id]
                        if (existingOrder?.isPrinted == true) {
//                            Log.d("OrderRepositoryImpl", "【状态刷新】订单在内存中不存在，但数据库标记为已打印，保留状态: #${entity.number}")
                            entity.copy(isPrinted = true)
                        } else {
                            entity
                        }
                    }
                }
                
                // 使用更新策略而非删除再插入
                // 1. 获取API返回的所有订单ID
                val apiOrderIds = orders.map { it.id }.toSet()
                
                // 2. 如果是全量刷新（无状态过滤），找出需要删除的订单
                if (status == null) {
                    val idsToDelete = currentOrders
                        .filter { !apiOrderIds.contains(it.id) }
                        .map { it.id }
                    
                    if (idsToDelete.isNotEmpty()) {
//                        Log.d("OrderRepositoryImpl", "【状态刷新】删除不再存在的订单: $idsToDelete")
                        // 处理需要删除的订单
                        idsToDelete.forEach { id ->
                            orderDao.deleteOrderById(id)
                        }
                    }
                } else {
                    // 只删除特定状态的订单，保留打印状态标记
//                    Log.d("OrderRepositoryImpl", "【状态刷新】只更新 $status 状态的订单")
                    orderDao.deleteOrdersByStatus(status)
                }
                
                // 插入或更新订单
//                Log.d("OrderRepositoryImpl", "【状态刷新】更新 ${entitiesWithPrintStatus.size} 个订单")
                orderDao.insertOrders(entitiesWithPrintStatus)
                
                // 同样更新缓存，保留已打印状态，但确保以内存状态为准
                val newCachedOrders = orders.map { order ->
                    val memoryOrder = memoryOrderMap[order.id]
                    
                    if (memoryOrder != null) {
                        // 如果内存中存在，保留内存中的打印状态
                        if (memoryOrder.isPrinted != order.isPrinted) {
                            // 只有在状态不同时才记录日志，避免日志过多
//                            Log.d("OrderRepositoryImpl", "【状态刷新】订单 #${order.number} 保留内存中的打印状态: ${memoryOrder.isPrinted}")
                            order.copy(isPrinted = memoryOrder.isPrinted)
                        } else {
                            order
                        }
                    } else {
                        // 内存中不存在，检查数据库
                        val existingOrder = currentOrderMap[order.id]
                        if (existingOrder?.isPrinted == true) {
                            order.copy(isPrinted = true)
                        } else {
                            order
                        }
                    }
                }
                
                // 更新内存缓存
                cachedOrders = newCachedOrders
                isOrdersCached = true
                _ordersFlow.value = cachedOrders
                
                // 如果API没有正确过滤状态，在本地进行过滤
                val finalOrders = if (status != null) {
                    // 再次进行本地过滤，确保只返回匹配状态的订单
                    val filteredOrders = cachedOrders.filter { 
                        val orderStatus = it.status
                        val matches = orderStatus == status || 
                                      (statusMap[status] == orderStatus) || 
                                      (statusMap.entries.find { entry -> entry.value == status }?.key == orderStatus)
                        
                        if (!matches) {
//                            Log.d("OrderRepositoryImpl", "【状态过滤】订单 ${it.id} 状态 '$orderStatus' 与请求状态 '$status' 不匹配")
                        }
                        
                        matches
                    }
                    
//                    Log.d("OrderRepositoryImpl", "【状态过滤】本地过滤后，符合状态 '$status' 的订单数量: ${filteredOrders.size}")
                    filteredOrders
                } else {
                    cachedOrders
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
//        Log.d("OrderRepositoryImpl", "【轮询刷新】开始查询处理中订单(UI安全)")
        
        try {
            val config = settingsRepository.getWooCommerceConfig()
            if (!config.isValid()) {
                Log.e("OrderRepositoryImpl", "API配置无效，请检查设置")
                return@withContext Result.failure(ApiError.fromHttpCode(401, "API配置无效，请检查设置"))
            }
            
            val api = getApi(config)
            
            try {
                // 使用固定的"processing"状态，确保只查询处理中的订单
//                Log.d("OrderRepositoryImpl", "【轮询刷新】调用API: getOrders(page=1, perPage=100, status=processing)")
                
                // 调用API获取处理中订单
                val response = api.getOrders(1, 100, "processing")
                
//                Log.d("OrderRepositoryImpl", "【轮询刷新】API返回 ${response.size} 个处理中订单")
                
                // 记录返回订单的状态分布
                val statusCounts = response.groupBy { order -> order.status }.mapValues { entry -> entry.value.size }
//                Log.d("OrderRepositoryImpl", "【轮询刷新】状态分布: $statusCounts")
                
                // 转换为领域模型
                val orders = response.map { orderDto -> orderDto.toOrder() }
                
                // 获取当前数据库中所有的processing订单
                val currentProcessingOrders = orderDao.getOrdersByStatus("processing").first()
                val dbOrderMap = currentProcessingOrders.associateBy { it.id }
                
                // 获取内存中的processing订单及其打印状态
                val cachedProcessingOrders = cachedOrders.filter { it.status == "processing" }
                val memoryOrderMap = cachedProcessingOrders.associateBy { it.id }
                
//                Log.d("OrderRepositoryImpl", "【轮询刷新】数据库中处理中订单: ${currentProcessingOrders.size}个, 内存中: ${cachedProcessingOrders.size}个")
                
                // 记录内存中已打印的订单
                val printedInMemory = cachedProcessingOrders.filter { it.isPrinted }.map { it.id }
//                Log.d("OrderRepositoryImpl", "【轮询刷新】内存中已打印订单ID: $printedInMemory")
                
                // 创建新的处理中订单实体，以内存状态为准
                val entitiesWithPrintStatus = orders.map { order -> 
                    val entity = OrderMapper.mapDomainToEntity(order)
                    
                    // 检查内存中是否存在该订单并获取其打印状态
                    val cachedOrder = memoryOrderMap[entity.id]
                    
                    if (cachedOrder != null) {
                        // 如果内存中存在，使用内存中的打印状态
//                        Log.d("OrderRepositoryImpl", "【轮询刷新】订单 #${entity.number} 在内存中存在，打印状态: ${cachedOrder.isPrinted}")
                        entity.copy(isPrinted = cachedOrder.isPrinted)
                    } else {
                        // 如果内存中不存在但数据库中存在，使用数据库打印状态
                        val dbOrder = dbOrderMap[entity.id]
                        if (dbOrder?.isPrinted == true) {
//                            Log.d("OrderRepositoryImpl", "【轮询刷新】订单 #${entity.number} 在内存中不存在，但数据库标记为已打印")
                            entity.copy(isPrinted = true)
                        } else {
                            entity
                        }
                    }
                }
                
                // 修改为更新策略，而不是删除再插入
                // 1. 获取API返回的所有订单ID
                val apiOrderIds = orders.map { it.id }.toSet()
                
                // 2. 找出数据库中有但API返回中没有的订单（已经不是processing状态）
                val idsToDelete = currentProcessingOrders
                    .filter { !apiOrderIds.contains(it.id) }
                    .map { it.id }
                
                if (idsToDelete.isNotEmpty()) {
//                    Log.d("OrderRepositoryImpl", "【轮询刷新】删除不再是processing状态的订单: $idsToDelete")
                    // 处理需要删除的订单
                    idsToDelete.forEach { id ->
                        orderDao.deleteOrderById(id)
                    }
                }
                
                // 3. 使用insertOrders进行批量更新（Room的REPLACE策略）
//                Log.d("OrderRepositoryImpl", "【轮询刷新】更新 ${entitiesWithPrintStatus.size} 个processing订单")
                orderDao.insertOrders(entitiesWithPrintStatus)
                
                // 注意：这里特意不更新_ordersFlow，以避免影响UI显示
//                Log.d("OrderRepositoryImpl", "【轮询刷新】成功获取处理中订单，不影响UI状态")
                
                // 返回带有打印状态的订单
                val ordersWithPrintStatus = orders.map { order ->
                    val cachedOrder = memoryOrderMap[order.id]
                    if (cachedOrder != null) {
                        // 使用内存状态
                        if (cachedOrder.isPrinted != order.isPrinted) {
//                            Log.d("OrderRepositoryImpl", "【轮询刷新】使用内存中订单 #${order.number} 的打印状态: ${cachedOrder.isPrinted}")
                            order.copy(isPrinted = cachedOrder.isPrinted)
                        } else {
                            order
                        }
                    } else {
                        // 内存中不存在，检查数据库
                        val dbOrder = dbOrderMap[order.id]
                        if (dbOrder?.isPrinted == true) {
                            order.copy(isPrinted = true)
                        } else {
                            order
                        }
                    }
                }
                
                return@withContext Result.success(ordersWithPrintStatus)
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
//        Log.d("OrderRepositoryImpl", "标记订单为已打印: $orderId")
        
        try {
            // 先获取订单当前状态并记录日志
            val orderBefore = orderDao.getOrderById(orderId)
//            Log.d("OrderRepositoryImpl", "打印前订单状态: ID=$orderId, 已打印=${orderBefore?.isPrinted}, 状态=${orderBefore?.status}")
            
            // 更新数据库
            orderDao.updateOrderPrintStatus(orderId, true)
            
            // 验证更新是否成功
            val orderAfter = orderDao.getOrderById(orderId)
//            Log.d("OrderRepositoryImpl", "打印后订单状态: ID=$orderId, 已打印=${orderAfter?.isPrinted}, 状态=${orderAfter?.status}")
            
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

    override suspend fun markOrderAsUnprinted(orderId: Long): Boolean = withContext(Dispatchers.IO) {
//        Log.d("OrderRepositoryImpl", "标记订单为未打印: $orderId")
        
        try {
            // 先获取订单当前状态并记录日志
            val orderBefore = orderDao.getOrderById(orderId)
//            Log.d("OrderRepositoryImpl", "修改前订单状态: ID=$orderId, 已打印=${orderBefore?.isPrinted}, 状态=${orderBefore?.status}")
            
            // 更新数据库
            orderDao.updateOrderPrintStatus(orderId, false)
            
            // 验证更新是否成功
            val orderAfter = orderDao.getOrderById(orderId)
//            Log.d("OrderRepositoryImpl", "修改后订单状态: ID=$orderId, 已打印=${orderAfter?.isPrinted}, 状态=${orderAfter?.status}")
            
            // 更新缓存和流
            val updatedList = cachedOrders.map { 
                if (it.id == orderId) it.copy(isPrinted = false) else it 
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
            Log.e("OrderRepositoryImpl", "标记订单为未打印失败", e)
            return@withContext false
        }
    }

    override suspend fun markOrderNotificationShown(orderId: Long): Result<Unit> = withContext(Dispatchers.IO) {
//        Log.d("OrderRepositoryImpl", "标记订单通知为已显示: $orderId")
        
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
//        Log.d("OrderRepositoryImpl", "【状态调试】获取状态为 '$status' 的订单流")
        
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
//                    Log.d("OrderRepositoryImpl", "【状态过滤】订单 ${order.id} 状态 '${order.status}' 与请求状态 '$status' 不匹配")
                }
                
                matches
            }
            
//            Log.d("OrderRepositoryImpl", "【状态调试】过滤后找到 ${filteredOrders.size} 个 '$status' 状态的订单")
            filteredOrders
        }
    }

    override suspend fun searchOrders(query: String): List<Order> = withContext(Dispatchers.IO) {
//        Log.d("OrderRepositoryImpl", "搜索订单: $query")
        
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
//        Log.d("OrderRepositoryImpl", "测试API连接")
        
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
//        Log.d("OrderRepositoryImpl", "【状态分组】所有状态: ${groupedOrders.keys.joinToString()}")
        
        // 记录每个状态的订单数量
        groupedOrders.forEach { (status, orders) ->
//            Log.d("OrderRepositoryImpl", "【状态分组】'$status' 状态有 ${orders.size} 个订单")
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
        // 计算小计金额：应该是商品总价，不包含税费和其他费用
        val calculatedSubtotal = try {
            // 尝试从订单行项目计算小计总额
            val itemsSubtotal = entity.lineItems.sumOf { 
                val price = it.price.toDoubleOrNull() ?: 0.0
                val quantity = it.quantity
                price * quantity
            }
            
            // 格式化为保留两位小数
            String.format("%.2f", itemsSubtotal)
        } catch (e: Exception) {
            // 如果计算失败，使用总价减去税费
            try {
                val total = entity.total.toDoubleOrNull() ?: 0.0
                val tax = entity.totalTax.toDoubleOrNull() ?: 0.0
                val subtotal = total - tax
                String.format("%.2f", subtotal)
            } catch (e: Exception) {
                // 如果还是失败，使用total作为备选
                entity.total
            }
        }
        
        // 记录日志用于调试
//        Log.d("OrderRepositoryImpl", "订单#${entity.number} - 映射实体到模型，feeLines数量: ${entity.feeLines.size}")
        entity.feeLines.forEach { feeLine ->
//            Log.d("OrderRepositoryImpl", "订单#${entity.number} - 费用行: ${feeLine.name} = ${feeLine.total}")
        }
        
        // 将实体中的费用行转换为领域模型费用行
        val domainFeeLines = entity.feeLines.map { feeLine ->
            com.example.wooauto.domain.models.FeeLine(
                id = feeLine.id,
                name = feeLine.name,
                total = feeLine.total,
                totalTax = feeLine.totalTax
            )
        }
        
        // 将实体中的税费行转换为领域模型税费行
        val domainTaxLines = entity.taxLines.map { taxLine ->
            com.example.wooauto.domain.models.TaxLine(
                id = taxLine.id,
                label = taxLine.label,
                ratePercent = taxLine.ratePercent,
                taxTotal = taxLine.taxTotal
            )
        }
        
        // 从实体中的WooFoodInfo创建领域模型WooFoodInfo
        val wooFoodInfo = entity.woofoodInfo?.let {
            com.example.wooauto.domain.models.WooFoodInfo(
                orderMethod = it.orderMethod,
                deliveryTime = it.deliveryTime,
                deliveryAddress = it.deliveryAddress,
                deliveryFee = it.deliveryFee,
                tip = it.tip,
                isDelivery = it.isDelivery
            )
        } ?: parseWooFoodInfo(entity) // 如果实体没有WooFoodInfo，尝试从其他字段解析

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
            notes = entity.customerNote,
            subtotal = entity.subtotal.takeIf { it.isNotEmpty() } ?: calculatedSubtotal,
            totalTax = entity.totalTax,
            discountTotal = entity.discountTotal,
            woofoodInfo = wooFoodInfo,
            feeLines = domainFeeLines,
            taxLines = domainTaxLines
        )
    }
    
    /**
     * 解析实体中的WooFood相关信息
     */
    private fun parseWooFoodInfo(entity: com.example.wooauto.data.local.entities.OrderEntity): com.example.wooauto.domain.models.WooFoodInfo? {
        // 判断是否为外卖订单 - 检查shipping地址
        val isDelivery = entity.shippingAddress.isNotEmpty()
        
        // 如果没有任何配送或自取相关信息，返回null
        if (!isDelivery && entity.customerNote.isEmpty()) {
            // 检查是否通过其他方式可以确定是自取订单
            // 例如，检查订单备注中是否有"自取"或"pickup"关键词
            val isPickup = entity.customerNote.contains("自取") || 
                          entity.customerNote.lowercase().contains("pickup") ||
                          entity.paymentMethodTitle.contains("自取")
            
            if (!isPickup) {
                return null
            }
        }
        
        // 提取时间信息
        val timeInfo = extractTimeInfo(entity.customerNote)
        
        // 从订单的customerNote中提取外卖费和小费信息
        var deliveryFee: String? = null
        var tipAmount: String? = null
        
        // 从备注中尝试提取外卖费信息
        try {
            // 外卖费可能以"外卖费:"、"配送费:"等形式存在于备注中
            val deliveryFeeRegex = "(外卖费|配送费|运费|送餐费|Shipping fee|Delivery fee|delivery charge)[:：]?\\s*([¥￥$]?\\s*\\d+(\\.\\d+)?)".toRegex(RegexOption.IGNORE_CASE)
            val deliveryFeeMatch = deliveryFeeRegex.find(entity.customerNote)
            
            if (deliveryFeeMatch != null && deliveryFeeMatch.groupValues.size > 2) {
                // 提取金额并删除货币符号
                deliveryFee = deliveryFeeMatch.groupValues[2].replace("[¥￥$\\s]".toRegex(), "")
//                Log.d("OrderRepositoryImpl", "从备注提取到外卖费: $deliveryFee")
            } else if (isDelivery) {
                // 如果找不到具体的外卖费但确定是外卖订单，尝试从其他方式检测
                // 检查订单备注中是否有与外卖相关的其他信息
                val hasDeliveryInfo = entity.customerNote.contains("送货") || 
                                     entity.customerNote.lowercase().contains("deliver") ||
                                     entity.customerNote.contains("外卖") ||
                                     entity.shippingAddress.contains("送到") ||
                                     entity.paymentMethodTitle.contains("货到付款")
                
                if (hasDeliveryInfo) {
//                    Log.d("OrderRepositoryImpl", "订单备注中包含外卖相关信息，设置默认值")
                    // 仍然设置为0，但记录该订单确实是外卖订单
                    deliveryFee = "0.00"
                }
            }
        } catch (e: Exception) {
            Log.e("OrderRepositoryImpl", "从备注提取外卖费失败: ${e.message}")
            // 出错时使用默认值
            if (isDelivery) deliveryFee = "0.00" 
        }
        
        // 从备注中尝试提取小费信息
        try {
            // 小费可能以"小费:"、"小费金额:"、"Tip:"等形式存在于备注中
            val tipRegex = "(小费|感谢费|Tip|gratuity|Show Your Appreciation)[:：]?\\s*([¥￥$]?\\s*\\d+(\\.\\d+)?)".toRegex(RegexOption.IGNORE_CASE)
            val tipMatch = tipRegex.find(entity.customerNote)
            
            if (tipMatch != null && tipMatch.groupValues.size > 2) {
                // 提取金额并删除货币符号
                tipAmount = tipMatch.groupValues[2].replace("[¥￥$\\s]".toRegex(), "")
            }
        } catch (e: Exception) {
            Log.e("OrderRepositoryImpl", "从备注提取小费失败: ${e.message}")
        }
        
        // 创建WooFood信息对象
        return com.example.wooauto.domain.models.WooFoodInfo(
            orderMethod = if (isDelivery) "delivery" else "pickup",
            deliveryTime = timeInfo,
            deliveryAddress = if (isDelivery) entity.shippingAddress else null,
            deliveryFee = deliveryFee,
            tip = tipAmount,
            isDelivery = isDelivery
        )
    }
    
    /**
     * 从订单备注中提取时间信息
     */
    private fun extractTimeInfo(note: String): String? {
        try {
            // 匹配常见的时间格式
            // 1. HH:MM 格式 (24小时制)
            // 2. HH:MM AM/PM 格式 (12小时制)
            // 3. 中文时间表示，如"下午3点30分"
            
            // 检查24小时制或12小时制时间
            val timeRegex = "(\\d{1,2}:\\d{2}(\\s*[AaPp][Mm])?)".toRegex()
            val timeMatch = timeRegex.find(note)
            
            if (timeMatch != null) {
                return timeMatch.groupValues[1]
            }
            
            // 检查中文时间表示
            val chineseTimeRegex = "([上下]午\\s*\\d{1,2}\\s*[点时]\\s*(\\d{1,2}\\s*分钟?)?)".toRegex()
            val chineseTimeMatch = chineseTimeRegex.find(note)
            
            if (chineseTimeMatch != null) {
                return chineseTimeMatch.groupValues[1]
            }
        } catch (e: Exception) {
            Log.e("OrderRepositoryImpl", "提取时间信息失败: ${e.message}")
        }
        
        return null
    }


    /**
     * 获取缓存的订单数据（不触发API请求）
     * @return 本地缓存的订单列表
     */
    override suspend fun getCachedOrders(): List<Order> = withContext(Dispatchers.IO) {
        Log.d("OrderRepositoryImpl", "获取缓存订单数据")
        
        // 如果已经有缓存，直接返回
        if (isOrdersCached && cachedOrders.isNotEmpty()) {
            return@withContext cachedOrders
        }
        
        // 从数据库读取所有订单
        try {
            val orderEntities = orderDao.getAllOrders().first()
            val orders = OrderMapper.mapEntityListToDomainList(orderEntities)
            
            // 更新缓存
            cachedOrders = orders
            isOrdersCached = true
            
//            Log.d("OrderRepositoryImpl", "从数据库加载了${orders.size}个订单")
            return@withContext orders
        } catch (e: Exception) {
            Log.e("OrderRepositoryImpl", "从数据库加载缓存订单失败: ${e.message}")
            return@withContext emptyList<Order>()
        }
    }
    
    /**
     * 根据状态刷新订单
     * @param status 订单状态
     * @return 刷新结果
     */
    override suspend fun refreshOrdersByStatus(status: String): Result<List<Order>> = withContext(Dispatchers.IO) {
//        Log.d("OrderRepositoryImpl", "刷新特定状态订单: $status")
        
        try {
            // 重用现有的refreshOrders方法，只传入状态参数
            return@withContext refreshOrders(status)
        } catch (e: Exception) {
            Log.e("OrderRepositoryImpl", "刷新状态 '$status' 的订单失败: ${e.message}")
            return@withContext Result.failure(e)
        }
    }
}