package com.example.wooauto.domain.repositories

import com.example.wooauto.domain.models.Order
import com.example.wooauto.data.remote.WooCommerceConfig
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * 订单仓库接口
 * 定义了与订单相关的所有数据操作
 */
interface DomainOrderRepository {
    /**
     * 获取所有订单的流
     * @return 包含所有订单的数据流
     */
    fun getAllOrdersFlow(): Flow<List<Order>>

    /**
     * 根据状态获取订单流
     * @param status 订单状态
     * @return 指定状态的订单数据流
     */
    fun getOrdersByStatusFlow(status: String): Flow<List<Order>>

    /**
     * 根据订单ID查询订单
     * @param orderId 订单ID
     * @return 订单对象，如果不存在则返回null
     */
    suspend fun getOrderById(orderId: Long): Order?

    /**
     * 刷新订单
     * @param status 可选的订单状态过滤条件
     * @param afterDate 可选的日期过滤条件，只获取该日期之后的订单
     * @return 刷新结果，包含刷新的订单列表
     */
    suspend fun refreshOrders(status: String? = null, afterDate: Date? = null): Result<List<Order>>

    /**
     * 更新订单状态
     * @param orderId 订单ID
     * @param newStatus 新的订单状态
     * @return 更新结果，包含更新后的订单
     */
    suspend fun updateOrderStatus(orderId: Long, newStatus: String): Result<Order>

    /**
     * 标记订单为已打印
     * @param orderId 订单ID
     * @return 是否成功标记
     */
    suspend fun markOrderAsPrinted(orderId: Long): Boolean

    /**
     * 标记订单为未打印
     * @param orderId 订单ID
     * @return 是否成功标记
     */
    suspend fun markOrderAsUnprinted(orderId: Long): Boolean

    /**
     * 标记订单为已读
     * @param orderId 订单ID
     * @return 是否成功标记
     */
    suspend fun markOrderAsRead(orderId: Long): Boolean

    /**
     * 标记订单为未读
     * @param orderId 订单ID
     * @return 是否成功标记
     */
    suspend fun markOrderAsUnread(orderId: Long): Boolean

    /**
     * 标记订单通知为已显示
     * @param orderId 订单ID
     * @return 更新结果
     */
    suspend fun markOrderNotificationShown(orderId: Long): Result<Unit>
    
    /**
     * 获取订单列表
     * @param status 可选的订单状态过滤条件
     * @return 订单列表
     */
    suspend fun getOrders(status: String? = null): List<Order>
    
    /**
     * 搜索订单
     * @param query 搜索关键词
     * @return 符合搜索条件的订单列表
     */
    suspend fun searchOrders(query: String): List<Order>
    
    /**
     * 测试API连接
     * @param config 可选的WooCommerce配置
     * @return 连接测试结果
     */
    suspend fun testConnection(config: WooCommerceConfig? = null): Boolean

    /**
     * 清除缓存数据
     */
    suspend fun clearCache()
    
    /**
     * 专门为后台轮询服务刷新处理中订单
     * 此方法不会影响UI显示的订单列表，不会修改_ordersFlow
     * @param afterDate 可选的日期过滤条件，只获取该日期之后的订单
     * @return 刷新结果，包含刷新的订单列表
     */
    suspend fun refreshProcessingOrdersForPolling(afterDate: Date? = null): Result<List<Order>>

    /**
     * 获取特定订单的状态流，允许组件监听单个订单的变化
     * @param orderId 订单ID
     * @return 订单状态流
     */
    fun getOrderByIdFlow(orderId: Long): Flow<Order?>

    /**
     * processing + 未读 的订单流（New orders）
     */
    fun getNewProcessingOrdersFlow(): Flow<List<Order>>

    /**
     * processing + 已读 的订单流（In processing）
     */
    fun getInProcessingOrdersFlow(): Flow<List<Order>>

    /**
     * New orders 计数（processing + 未读）
     */
    fun getNewProcessingCountFlow(): Flow<Int>

    /**
     * 获取缓存的订单数据（不触发API请求）
     * @return 本地缓存的订单列表
     */
    suspend fun getCachedOrders(): List<Order>
    
    /**
     * 根据状态刷新订单
     * @param status 订单状态
     * @return 刷新结果
     */
    suspend fun refreshOrdersByStatus(status: String): Result<List<Order>>

    /**
     * 获取未读订单列表
     * @return 未读订单列表
     */
    suspend fun getUnreadOrders(): List<Order>

    /**
     * 标记所有订单为已读
     * @return 操作是否成功
     */
    suspend fun markAllOrdersAsRead(): Boolean
    
    /**
     * 获取订单DAO对象，用于直接数据库操作
     * @return OrderDao实例
     */
    fun getOrderDao(): com.example.wooauto.data.local.dao.OrderDao
    
    /**
     * 根据ID列表获取订单
     * @param orderIds 订单ID列表
     * @return 订单列表
     */
    suspend fun getOrdersByIds(orderIds: List<Long>): List<Order>

    /**
     * Debug: 获取指定订单的原始REST API元数据（格式化字符串）
     */
    suspend fun getRawOrderMetadata(orderId: Long): String?
} 