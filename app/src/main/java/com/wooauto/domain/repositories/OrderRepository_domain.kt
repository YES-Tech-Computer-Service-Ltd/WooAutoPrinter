package com.wooauto.domain.repositories

import com.wooauto.domain.models.Order
import kotlinx.coroutines.flow.Flow
import java.util.Date

interface OrderRepository_domain {
    // 获取所有订单的流
    fun getAllOrdersFlow(): Flow<List<Order>>

    // 根据状态获取订单流
    fun getOrdersByStatusFlow(status: String): Flow<List<Order>>

    // 根据订单ID查询订单
    suspend fun getOrderById(orderId: Long): Order?

    // 刷新订单
    suspend fun refreshOrders(status: String? = null, afterDate: Date? = null): Result<List<Order>>

    // 更新订单状态
    suspend fun updateOrderStatus(orderId: Long, newStatus: String): Result<Order>

}
