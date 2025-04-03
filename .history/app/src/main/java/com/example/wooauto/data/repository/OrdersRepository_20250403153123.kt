package com.example.wooauto.data.repository

import android.util.Log
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.templates.TemplateType
import com.example.wooauto.data.local.dao.OrderDao
import com.example.wooauto.data.local.dao.ReadStatusDao
import com.example.wooauto.data.remote.service.WooCommerceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface OrdersRepository {
    suspend fun getOrders(): List<Order>
    suspend fun getOrderDetails(id: Long): Order?
    suspend fun updateOrderStatus(id: Long, status: String): Boolean
    suspend fun printOrder(id: Long, template: TemplateType): Boolean
    
    // 添加未读订单相关方法
    suspend fun getUnreadOrders(): List<Order>
    suspend fun markOrderAsRead(orderId: Long): Boolean
    suspend fun markAllOrdersAsRead(): Boolean
}

class OrdersRepositoryImpl @Inject constructor(
    private val wooCommerceService: WooCommerceService,
    private val orderDao: OrderDao,
    private val readStatusDao: ReadStatusDao // 假设你有一个存储读取状态的DAO
) : OrdersRepository {
    
    // 实现获取所有订单的方法
    override suspend fun getOrders(): List<Order> = withContext(Dispatchers.IO) {
        try {
            // 从数据库获取所有订单
            return@withContext orderDao.getAllOrders()
        } catch (e: Exception) {
            Log.e("OrdersRepository", "获取订单时发生错误", e)
            return@withContext emptyList()
        }
    }
    
    // 实现获取订单详情的方法
    override suspend fun getOrderDetails(id: Long): Order? = withContext(Dispatchers.IO) {
        try {
            return@withContext orderDao.getOrderById(id)
        } catch (e: Exception) {
            Log.e("OrdersRepository", "获取订单详情时发生错误", e)
            return@withContext null
        }
    }
    
    // 实现更新订单状态的方法
    override suspend fun updateOrderStatus(id: Long, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 更新订单状态
            orderDao.updateOrderStatus(id, status)
            return@withContext true
        } catch (e: Exception) {
            Log.e("OrdersRepository", "更新订单状态时发生错误", e)
            return@withContext false
        }
    }
    
    // 实现打印订单的方法
    override suspend fun printOrder(id: Long, template: TemplateType): Boolean = withContext(Dispatchers.IO) {
        try {
            // 打印订单的逻辑，这里需要根据实际情况实现
            // 可能需要获取订单信息，然后使用打印服务进行打印
            val order = orderDao.getOrderById(id) ?: return@withContext false
            // 打印实现...
            return@withContext true
        } catch (e: Exception) {
            Log.e("OrdersRepository", "打印订单时发生错误", e)
            return@withContext false
        }
    }
    
    // 实现获取未读订单的方法
    override suspend fun getUnreadOrders(): List<Order> = withContext(Dispatchers.IO) {
        try {
            // 获取未读订单
            val unreadIds = readStatusDao.getUnreadOrderIds()
            // 根据ID获取具体订单信息
            return@withContext orderDao.getOrdersByIds(unreadIds)
        } catch (e: Exception) {
            Log.e("OrdersRepository", "获取未读订单时发生错误", e)
            return@withContext emptyList()
        }
    }
    
    // 实现标记订单为已读的方法
    override suspend fun markOrderAsRead(orderId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            // 标记订单为已读
            readStatusDao.markOrderAsRead(orderId)
            return@withContext true
        } catch (e: Exception) {
            Log.e("OrdersRepository", "标记订单已读时发生错误", e)
            return@withContext false
        }
    }
    
    // 实现标记所有订单为已读的方法
    override suspend fun markAllOrdersAsRead(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 标记所有订单为已读
            readStatusDao.markAllOrdersAsRead()
            return@withContext true
        } catch (e: Exception) {
            Log.e("OrdersRepository", "标记所有订单已读时发生错误", e)
            return@withContext false
        }
    }
} 