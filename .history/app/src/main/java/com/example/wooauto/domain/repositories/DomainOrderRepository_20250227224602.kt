package com.example.wooauto.domain.repositories

import com.example.wooauto.domain.models.Order
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
     * @return 更新结果
     */
    suspend fun markOrderAsPrinted(orderId: Long): Result<Unit>

    /**
     * 标记订单通知为已显示
     * @param orderId 订单ID
     * @return 更新结果
     */
    suspend fun markOrderNotificationShown(orderId: Long): Result<Unit>
}