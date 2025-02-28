package com.wooauto.domain.usecases.orders

import com.wooauto.domain.models.Order
import com.wooauto.domain.repositories.DomainOrderRepository
import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject

/**
 * 获取订单列表用例
 *
 * 该用例负责处理与获取订单列表相关的业务逻辑，包括：
 * - 获取所有订单
 * - 根据状态获取订单
 * - 刷新订单列表
 */
class GetOrdersUseCase @Inject constructor(
    private val orderRepository: DomainOrderRepository
) {
    /**
     * 获取所有订单的数据流
     * @return 订单列表的数据流
     */
    fun getAllOrdersFlow(): Flow<List<Order>> {
        return orderRepository.getAllOrdersFlow()
    }

    /**
     * 根据状态获取订单的数据流
     * @param status 订单状态
     * @return 指定状态的订单列表数据流
     */
    fun getOrdersByStatusFlow(status: String): Flow<List<Order>> {
        return orderRepository.getOrdersByStatusFlow(status)
    }

    /**
     * 刷新订单列表
     * @param status 可选的订单状态过滤
     * @param afterDate 可选的日期过滤，只获取该日期之后的订单
     * @return 刷新结果，包含刷新后的订单列表
     */
    suspend fun refreshOrders(status: String? = null, afterDate: Date? = null): Result<List<Order>> {
        return orderRepository.refreshOrders(status, afterDate)
    }
} 