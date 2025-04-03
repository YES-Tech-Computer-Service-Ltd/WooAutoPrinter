package com.example.wooauto.data.repository

import android.util.Log
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.templates.TemplateType
import com.example.wooauto.data.local.dao.OrderDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 订单仓库接口
 * 定义了与订单相关的数据操作方法
 */
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

/**
 * 订单仓库实现类
 */
@Singleton
class OrdersRepositoryImpl @Inject constructor(
    private val orderDao: OrderDao,
    private val orderRepository: com.example.wooauto.domain.repositories.DomainOrderRepository
) : OrdersRepository {
    
    /**
     * 获取所有订单
     */
    override suspend fun getOrders(): List<Order> = withContext(Dispatchers.IO) {
        try {
            // 从主订单仓库获取订单
            return@withContext orderRepository.getOrders()
        } catch (e: Exception) {
            Log.e("OrdersRepository", "获取订单时发生错误", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * 获取订单详情
     */
    override suspend fun getOrderDetails(id: Long): Order? = withContext(Dispatchers.IO) {
        try {
            // 从主订单仓库获取订单详情
            return@withContext orderRepository.getOrderById(id)
        } catch (e: Exception) {
            Log.e("OrdersRepository", "获取订单详情时发生错误", e)
            return@withContext null
        }
    }
    
    /**
     * 更新订单状态
     */
    override suspend fun updateOrderStatus(id: Long, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 调用主订单仓库更新状态
            val result = orderRepository.updateOrderStatus(id, status)
            return@withContext result.isSuccess
        } catch (e: Exception) {
            Log.e("OrdersRepository", "更新订单状态时发生错误", e)
            return@withContext false
        }
    }
    
    /**
     * 打印订单
     */
    override suspend fun printOrder(id: Long, template: TemplateType): Boolean = withContext(Dispatchers.IO) {
        try {
            // 这里需要集成打印功能
            // 暂时返回true表示成功
            return@withContext true
        } catch (e: Exception) {
            Log.e("OrdersRepository", "打印订单时发生错误", e)
            return@withContext false
        }
    }
    
    /**
     * 获取未读订单
     */
    override suspend fun getUnreadOrders(): List<Order> = withContext(Dispatchers.IO) {
        try {
            // 从订单数据库中获取未读订单
            // 实际实现可能需要根据项目的具体数据库结构来调整
            val orders = orderRepository.getOrders()
            return@withContext orders.filter { !it.isRead }
        } catch (e: Exception) {
            Log.e("OrdersRepository", "获取未读订单时发生错误", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * 标记订单为已读
     */
    override suspend fun markOrderAsRead(orderId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            // 标记订单为已读
            // 实际实现需要根据项目的数据库结构调整
            orderDao.markOrderAsRead(orderId)
            return@withContext true
        } catch (e: Exception) {
            Log.e("OrdersRepository", "标记订单已读时发生错误", e)
            return@withContext false
        }
    }
    
    /**
     * 标记所有订单为已读
     */
    override suspend fun markAllOrdersAsRead(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 标记所有订单为已读
            // 实际实现需要根据项目的数据库结构调整
            orderDao.markAllOrdersAsRead()
            return@withContext true
        } catch (e: Exception) {
            Log.e("OrdersRepository", "标记所有订单已读时发生错误", e)
            return@withContext false
        }
    }
} 