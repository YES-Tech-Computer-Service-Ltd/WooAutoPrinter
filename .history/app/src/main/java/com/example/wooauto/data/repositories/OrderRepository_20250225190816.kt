package com.example.wooauto.data.repositories

import android.util.Log
import com.example.wooauto.data.api.WooCommerceApiService
import com.example.wooauto.data.api.models.Order
import com.example.wooauto.data.api.requests.OrderUpdateRequest
import com.example.wooauto.data.database.dao.OrderDao
import com.example.wooauto.data.database.entities.OrderEntity
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class OrderRepository(
    private val orderDao: OrderDao,
    private val apiService: WooCommerceApiService,
    private val apiKey: String,
    private val apiSecret: String
) {
    private val TAG = "OrderRepository"
    private val gson = Gson()

    // Local data access
    fun getAllOrdersFlow(): Flow<List<OrderEntity>> {
        return orderDao.getAllOrdersFlow()
    }

    fun getOrdersByStatusFlow(status: String): Flow<List<OrderEntity>> {
        return orderDao.getOrdersByStatusFlow(status)
    }

    fun searchOrdersFlow(query: String): Flow<List<OrderEntity>> {
        return orderDao.searchOrdersFlow(query)
    }

    suspend fun getOrderById(orderId: Long): OrderEntity? {
        return orderDao.getOrderById(orderId)
    }

    suspend fun getUnprintedOrders(): List<OrderEntity> {
        return orderDao.getUnprintedOrders()
    }

    suspend fun markOrderAsPrinted(orderId: Long) {
        orderDao.markOrderAsPrinted(orderId)
    }

    suspend fun markOrderNotificationShown(orderId: Long) {
        orderDao.markOrderNotificationShown(orderId)
    }

    // Network operations
    suspend fun refreshOrders(
        status: String? = null,
        afterDate: Date? = null
    ): Result<List<Order>> {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            val afterDateString = afterDate?.let { dateFormat.format(it) }

            val response = apiService.getOrders(
                consumerKey = apiKey,
                consumerSecret = apiSecret,
                status = status,
                after = afterDateString,
                perPage = 50
            )

            if (response.isSuccessful) {
                val orders = response.body() ?: emptyList()

                // Save to database
                val orderEntities = orders.map { it.toOrderEntity() }
                orderDao.insertOrders(orderEntities)

                Result.success(orders)
            } else {
                Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing orders", e)
            Result.failure(e)
        }
    }

    suspend fun fetchNewOrders(lastCheckedDate: Date): Result<List<Order>> {
        return refreshOrders(afterDate = lastCheckedDate)
    }

    suspend fun getOrder(orderId: Long): Result<Order> {
        return try {
            val response = apiService.getOrder(
                orderId = orderId,
                consumerKey = apiKey,
                consumerSecret = apiSecret
            )

            if (response.isSuccessful) {
                val order = response.body()
                    ?: return Result.failure(Exception("Order not found"))

                // Update local database
                orderDao.insertOrder(order.toOrderEntity())

                Result.success(order)
            } else {
                Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching order", e)
            Result.failure(e)
        }
    }

    suspend fun updateOrderStatus(orderId: Long, newStatus: String): Result<Order> {
        return try {
            val request = OrderUpdateRequest(status = newStatus)

            val response = apiService.updateOrder(
                orderId = orderId,
                orderUpdateRequest = request,
                consumerKey = apiKey,
                consumerSecret = apiSecret
            )

            if (response.isSuccessful) {
                val updatedOrder = response.body()
                    ?: return Result.failure(Exception("Failed to update order"))

                // Update local database
                orderDao.updateOrderStatus(orderId, newStatus)

                Result.success(updatedOrder)
            } else {
                Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating order status", e)
            Result.failure(e)
        }
    }

    // Helper methods
    private fun Order.toOrderEntity(): OrderEntity {
        return OrderEntity(
            id = id,
            number = number,
            status = status,
            dateCreated = dateCreated,
            total = total,
            customerId = customerId,
            customerName = billing.getFullName(),
            billingAddress = "${billing.address1}, ${billing.city}, ${billing.state}",
            shippingAddress = "${shipping.address1}, ${shipping.city}, ${shipping.state}",
            paymentMethod = paymentMethod,
            paymentMethodTitle = paymentMethodTitle,
            lineItemsJson = gson.toJson(lineItems),
            isPrinted = this.isPrinted,
            notificationShown = this.notificationShown
        )
    }
}