package com.wooauto.domain.usecases.orders

import com.wooauto.domain.models.Order
import com.wooauto.domain.repositories.DomainOrderRepository

/**
 * 订单管理用例
 *
 * 该用例负责处理与订单管理相关的业务逻辑，包括：
 * - 获取单个订单详情
 * - 更新订单状态（根据指定id）
 */
class ManageOrderUseCase(
    private val orderRepository: DomainOrderRepository
) {
    /**
     * 获取指定ID的订单详情
     * @param orderId 订单ID
     * @return 订单详情，如果未找到则返回null
     */
    suspend fun getOrderById(orderId: Long): Order? {
        return orderRepository.getOrderById(orderId)
    }

    /**
     * 更新订单状态（根据指定id）
     * @param orderId 订单ID
     * @param newStatus 新的订单状态
     * @return 更新结果，成功返回更新后的订单，失败返回错误信息
     */
    suspend fun updateOrderStatus(orderId: Long, newStatus: String): Result<Order> {
        return orderRepository.updateOrderStatus(orderId, newStatus)
    }
} 