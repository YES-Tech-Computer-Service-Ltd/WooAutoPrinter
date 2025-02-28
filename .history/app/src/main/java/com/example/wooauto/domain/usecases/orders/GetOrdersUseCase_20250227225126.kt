package com.example.wooauto.domain.usecases.orders

import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.example.wooauto.domain.models.Order
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * 获取订单列表的用例
 *
 * 该用例负责处理与获取订单列表相关的所有业务逻辑，包括：
 * - 获取所有订单
 * - 根据状态筛选订单
 * - 刷新订单列表（所有订单）
 */
class GetOrdersUseCase(
    private val orderRepository: DomainOrderRepository
) {
    /**
     * 获取所有订单的Flow
     * @return 包含所有订单列表的Flow
     */
    fun getAllOrders(): Flow<List<Order>> {
        return orderRepository.getAllOrdersFlow()
    }

    /**
     * 根据状态获取订单的Flow
     * @param status 订单状态
     * @return 符合指定状态的订单列表Flow
     */
    fun getOrdersByStatus(status: String): Flow<List<Order>> {
        return orderRepository.getOrdersByStatusFlow(status)
    }

    /**
     * 刷新订单列表（所有订单）
     * @param status 可选的订单状态过滤
     * @param afterDate 可选的日期过滤，只获取该日期之后的订单
     * @return 刷新结果，成功返回订单列表，失败返回错误信息
     */
    suspend fun refreshOrders(status: String? = null, afterDate: Date? = null): Result<List<Order>> {
        return orderRepository.refreshOrders(status, afterDate)
    }
} 