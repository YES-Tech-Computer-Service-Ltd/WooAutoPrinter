package com.example.wooauto.data.repository

import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.wooauto.data.local.dao.OrderDao
import com.wooauto.data.mappers.OrderMapper
import com.wooauto.data.remote.api.WooCommerceApiService
import com.wooauto.domain.models.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val orderDao: OrderDao,
    private val apiService: WooCommerceApiService
) : DomainOrderRepository {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

    override fun getAllOrdersFlow(): Flow<List<Order>> {
        return orderDao.getAllOrders().map { entities ->
            entities.map { OrderMapper.mapEntityToDomain(it) }
        }
    }

    override fun getOrdersByStatusFlow(status: String): Flow<List<Order>> {
        return orderDao.getOrdersByStatus(status).map { entities ->
            entities.map { OrderMapper.mapEntityToDomain(it) }
        }
    }

    override suspend fun getOrderById(orderId: Long): Order? {
        return orderDao.getOrderById(orderId)?.let { OrderMapper.mapEntityToDomain(it) }
    }

    override suspend fun refreshOrders(status: String?, afterDate: Date?): Result<List<Order>> {
        return try {
            val params = mutableMapOf<String, String>()
            status?.let { params["status"] = it }
            afterDate?.let { params["after"] = dateFormat.format(it) }

            val response = apiService.getOrders(1, params = params)
            val entities = response.map { OrderMapper.mapResponseToEntity(it) }
            entities.forEach { orderDao.insertOrder(it) }

            Result.success(entities.map { OrderMapper.mapEntityToDomain(it) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateOrderStatus(orderId: Long, newStatus: String): Result<Order> {
        return try {
            val response = apiService.updateOrderStatus(orderId, mapOf("status" to newStatus))
            val entity = OrderMapper.mapResponseToEntity(response)
            orderDao.insertOrder(entity)
            Result.success(OrderMapper.mapEntityToDomain(entity))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markOrderAsPrinted(orderId: Long): Result<Unit> {
        return try {
            val order = orderDao.getOrderById(orderId) ?: throw IllegalArgumentException("Order not found")
            orderDao.updateOrder(order.copy(isPrinted = true))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markOrderNotificationShown(orderId: Long): Result<Unit> {
        return try {
            val order = orderDao.getOrderById(orderId) ?: throw IllegalArgumentException("Order not found")
            orderDao.updateOrder(order.copy(notificationShown = true))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}