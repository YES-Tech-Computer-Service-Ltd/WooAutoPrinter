package com.example.wooauto.data.repository

import android.util.Log
import com.example.wooauto.data.remote.WooCommerceApi
import com.example.wooauto.data.remote.WooCommerceApiFactory
import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.data.remote.dto.toOrder
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.example.wooauto.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
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
        if (!isOrdersCached) {
            refreshOrders(status)
        }
        
        return@withContext if (status != null) {
            cachedOrders.filter { it.status == status }
        } else {
            cachedOrders
        }
    }

    override suspend fun getOrder(id: Long): Order? = withContext(Dispatchers.IO) {
        Log.d("OrderRepositoryImpl", "按ID获取订单: $id")
        try {
            // 先从缓存中查找
            cachedOrders.find { it.id == id }?.let { return@withContext it }
            
            // 如果缓存中没有，从API获取
            val api = getApi()
            val response = api.getOrder(id)
            return@withContext response.toOrder()
        } catch (e: Exception) {
            Log.e("OrderRepositoryImpl", "获取订单失败: $id", e)
            return@withContext null
        }
    }

    override suspend fun updateOrderStatus(id: Long, status: String): Order = withContext(Dispatchers.IO) {
        Log.d("OrderRepositoryImpl", "更新订单状态: $id -> $status")
        try {
            val api = getApi()
            val response = api.updateOrder(id, mapOf("status" to status))
            val updatedOrder = response.toOrder()
            
            // 更新缓存
            val index = cachedOrders.indexOfFirst { it.id == id }
            if (index != -1) {
                cachedOrders = cachedOrders.toMutableList().apply {
                    set(index, updatedOrder)
                }
            }
            
            return@withContext updatedOrder
        } catch (e: Exception) {
            Log.e("OrderRepositoryImpl", "更新订单状态失败", e)
            throw e
        }
    }

    override suspend fun refreshOrders(status: String?): Boolean = withContext(Dispatchers.IO) {
        Log.d("OrderRepositoryImpl", "刷新订单列表，状态: ${status ?: "全部"}")
        try {
            val api = getApi()
            val params = if (status != null) mapOf("status" to status) else emptyMap()
            val orders = api.getOrders(status = status).map { it.toOrder() }
            
            if (status == null) {
                // 只有获取全部订单时才完全替换缓存
                cachedOrders = orders
                isOrdersCached = true
            } else {
                // 获取特定状态订单时，只替换相应状态的订单
                val nonFilteredOrders = cachedOrders.filter { it.status != status }
                cachedOrders = nonFilteredOrders + orders
            }
            
            Log.d("OrderRepositoryImpl", "刷新订单成功: ${orders.size}个订单")
            return@withContext true
        } catch (e: Exception) {
            Log.e("OrderRepositoryImpl", "刷新订单失败", e)
            return@withContext false
        }
    }

    override suspend fun markOrderAsPrinted(orderId: Long): Boolean = withContext(Dispatchers.IO) {
        Log.d("OrderRepositoryImpl", "标记订单为已打印: $orderId")
        try {
            // 更新缓存中的订单打印状态
            val index = cachedOrders.indexOfFirst { it.id == orderId }
            if (index != -1) {
                val order = cachedOrders[index]
                cachedOrders = cachedOrders.toMutableList().apply {
                    set(index, order.copy(isPrinted = true))
                }
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e("OrderRepositoryImpl", "标记订单为已打印失败", e)
            return@withContext false
        }
    }

    override suspend fun searchOrders(query: String): List<Order> = withContext(Dispatchers.IO) {
        Log.d("OrderRepositoryImpl", "搜索订单: $query")
        if (query.isBlank()) {
            return@withContext getOrders()
        }
        
        val normalizedQuery = query.trim().lowercase()
        return@withContext getOrders().filter { order ->
            order.number.lowercase().contains(normalizedQuery) ||
            order.customerName.lowercase().contains(normalizedQuery) ||
            order.billingInfo.lowercase().contains(normalizedQuery) ||
            order.contactInfo.lowercase().contains(normalizedQuery) ||
            order.items.any { it.name.lowercase().contains(normalizedQuery) }
        }
    }

    override suspend fun testConnection(config: WooCommerceConfig?): Boolean = withContext(Dispatchers.IO) {
        Log.d("OrderRepositoryImpl", "测试API连接")
        try {
            val api = getApi(config)
            // 简单地尝试获取一个订单列表，如果成功就说明连接正常
            val response = api.getOrders(perPage = 1)
            Log.d("OrderRepositoryImpl", "连接测试成功")
            return@withContext true
        } catch (e: Exception) {
            Log.e("OrderRepositoryImpl", "连接测试失败", e)
            return@withContext false
        }
    }

    // 实现DomainOrderRepository接口的方法
    override fun getAllOrdersFlow(): Flow<List<Order>> {
        return orderDao.getAllOrders().map { entities ->
            entities.map { OrderMapper.mapEntityToDomain(it) }
        }
    }

    override fun getOrdersByStatusFlow(status: String): Flow<List<Order>> {
        return orderDao.getOrdersByStatus(status).map { entities ->
            entities.map { OrderMapper.mapEntityToDomain(it) }
        }
    }

    override suspend fun getOrderById(orderId: Long): Order? {
        return getOrder(orderId)
    }

    override suspend fun refreshOrders(status: String?, afterDate: Date?): Result<List<Order>> {
        return try {
            val result = refreshOrders(status)
            if (result) {
                Result.success(cachedOrders.filter { order ->
                    (afterDate == null || order.dateCreated.after(afterDate)) &&
                    (status == null || order.status == status)
                })
            } else {
                Result.failure(Exception("刷新订单失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateOrderStatus(orderId: Long, newStatus: String): Result<Order> {
        return try {
            val order = updateOrderStatus(orderId, newStatus)
            Result.success(order)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markOrderAsPrinted(orderId: Long): Result<Unit> {
        return try {
            val success = markOrderAsPrinted(orderId)
            if (success) Result.success(Unit) else Result.failure(Exception("标记订单为已打印失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markOrderNotificationShown(orderId: Long): Result<Unit> {
        // 这个方法在原接口中可能没有实现，需要添加
        return try {
            // 实际实现可能需要调用数据库或其他服务
            // 这里简单返回成功
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}