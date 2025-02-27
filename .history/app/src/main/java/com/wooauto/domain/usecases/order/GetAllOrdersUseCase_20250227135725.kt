package com.wooauto.domain.usecases.order

import com.wooauto.domain.models.Order
import com.wooauto.domain.repositories.OrderRepository_domain
import kotlinx.coroutines.flow.Flow

/**
 * 获取所有订单的用例
 *
 * 该用例负责从仓储层获取所有订单的数据流。
 * 使用 Flow 来实现响应式数据流，当订单数据发生变化时，
 * 订阅者会自动收到更新。
 *
 * 用例场景：
 * 1. 在订单列表页面展示所有订单
 * 2. 在仪表盘中监控订单总体情况
 * 3. 用于数据分析和统计
 *
 * @property orderRepository 订单仓储接口
 */
class GetAllOrdersUseCase(
    private val orderRepository: OrderRepository_domain
) {
    /**
     * 执行用例，获取所有订单的数据流
     *
     * @return Flow<List<Order>> 订单列表的数据流
     */
    operator fun invoke(): Flow<List<Order>> {
        return orderRepository.getAllOrdersFlow()
    }
} 