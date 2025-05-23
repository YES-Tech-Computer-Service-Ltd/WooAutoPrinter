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
        return@withContext OrderMapper.mapEntityListToDomain(
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
        Log.d("OrderRepositoryImpl", "刷新订单数据，状态: ${status ?: "全部"}")
        
        try {
            val api = getApi()
            val response = api.getOrders(status = status)
            // 转换为领域模型
            val orders = response.map { it.toOrder() }
            
            // 更新缓存
            cachedOrders = orders
            isOrdersCached = true
            _ordersFlow.value = orders
            
            // 更新数据库
            val entities = orders.map { OrderMapper.mapDomainToEntity(it) }
            orderDao.deleteAllOrders() // 清除旧数据
            orderDao.insertOrders(entities) // 插入新数据
            
            // 更新状态分组
            updateOrdersByStatus()
            
            return@withContext Result.success(orders)
        } catch (e: Exception) {
            val error = when (e) {
                is ApiError -> e
                else -> ApiError.fromException(e)
            }
            
            Log.e("OrderRepositoryImpl", "刷新订单数据失败: ${error.code} - ${error.message}")
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
        return _ordersByStatusFlow.asStateFlow().map { statusMap ->
            statusMap[status] ?: emptyList()
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