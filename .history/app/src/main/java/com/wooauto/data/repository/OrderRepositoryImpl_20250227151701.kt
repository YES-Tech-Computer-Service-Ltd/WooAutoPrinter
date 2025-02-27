package com.wooauto.data.repository

import com.wooauto.data.local.dao.OrderDao
import com.wooauto.data.remote.api.WooCommerceApiService
import com.wooauto.domain.models.Order
import com.wooauto.domain.repositories.OrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 订单仓库实现类
 * 实现了领域层定义的OrderRepository接口
 */
@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val orderDao: OrderDao,
    private val apiService: WooCommerceApiService
) : OrderRepository {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

    /**
     * 获取所有订单
     * @return 订单列表流
     */
    override fun getAllOrders(): Flow<List<Order>> {
        return orderDao.getAllOrders().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    /**
     * 根据ID获取订单
     * @param orderId 订单ID
     * @return 订单对象
     */
    override suspend fun getOrderById(orderId: Long): Order? {
        return orderDao.getOrderById(orderId)?.toDomainModel()
    }

    /**
     * 从远程API刷新订单
     * @param page 页码
     */
    override suspend fun refreshOrders(page: Int) {
        val orders = apiService.getOrders(page)
        orders.forEach { orderResponse ->
            orderDao.insertOrder(orderResponse.toEntity())
        }
    }

    /**
     * 更新订单状态
     * @param orderId 订单ID
     * @param status 新状态
     */
    override suspend fun updateOrderStatus(orderId: Long, status: String) {
        val response = apiService.updateOrderStatus(orderId, mapOf("status" to status))
        orderDao.insertOrder(response.toEntity())
    }
} 