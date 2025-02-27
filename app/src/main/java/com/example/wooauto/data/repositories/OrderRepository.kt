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
    private val TAG = "OrderRepo_DEBUG"
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
            Log.d(TAG, "===== 开始刷新订单 =====")
            Log.d(TAG, "参数 - 状态: $status, 日期: $afterDate")
            Log.d(TAG, "API凭证 - Key: $apiKey, Secret: ${apiSecret.take(4)}***")

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            val afterDateString = afterDate?.let { dateFormat.format(it) }
            Log.d(TAG, "格式化后的日期参数: $afterDateString")

            Log.d(TAG, "准备调用订单API...")
            val response = apiService.getOrders(
                consumerKey = apiKey,
                consumerSecret = apiSecret,
                status = status,
                after = afterDateString,
                perPage = 100
            )
            Log.d(TAG, "API响应码：${response.code()}")
            Log.d(TAG, "API响应头：${response.headers()}")

            if (response.isSuccessful) {
                val orders = response.body() ?: emptyList()
                Log.d(TAG, "成功获取订单数量: ${orders.size}")
                orders.forEachIndexed { index, order ->
                    Log.d(TAG, "订单[$index] - ID: ${order.id}, 编号: ${order.number}, 状态: ${order.status}, 日期: ${order.dateCreated}")
                }

                // Save to database
                val orderEntities = orders.map { it.toOrderEntity() }
                Log.d(TAG, "准备保存 ${orderEntities.size} 个订单到数据库")
                orderDao.insertOrders(orderEntities)
                Log.d(TAG, "订单已保存到数据库")

                Result.success(orders)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "API错误：${response.code()} - ${response.message()}")
                Log.e(TAG, "错误响应体：$errorBody")
                Result.failure(Exception("API错误：${response.code()} - ${response.message()} - $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "刷新订单时发生异常", e)
            e.printStackTrace()
            Result.failure(e)
        } finally {
            Log.d(TAG, "===== 刷新订单完成 =====")
        }
    }


    suspend fun fetchNewOrders(lastCheckedDate: Date): Result<List<Order>> {
        Log.d(TAG, "===== 获取新订单 =====")
        Log.d(TAG, "上次检查时间: $lastCheckedDate")

        return try {
            // 使用UTC时区的ISO 8601格式确保与API兼容
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val formattedDate = dateFormat.format(lastCheckedDate)
            Log.d(TAG, "格式化后的日期参数 (UTC): $formattedDate")

            // 调用API获取订单
            val response = apiService.getOrders(
                consumerKey = apiKey,
                consumerSecret = apiSecret,
                status = null,
                after = formattedDate,
                perPage = 100,
                page = 1,
                order = "desc",
                orderBy = "date"
            )

            Log.d(TAG, "API响应码：${response.code()}")
            Log.d(TAG, "API响应头：${response.headers()}")

            if (response.isSuccessful) {
                val orders = response.body() ?: emptyList()
                Log.d(TAG, "成功获取新订单数量: ${orders.size}")

                // 详细记录获取到的订单
                orders.forEachIndexed { index, order ->
                    Log.d(TAG, "新订单[$index] - ID: ${order.id}, 编号: ${order.number}, 状态: ${order.status}, 日期: ${order.dateCreated}")
                }

                if (orders.isNotEmpty()) {
                    // 保存到数据库
                    val orderEntities = orders.map { it.toOrderEntity() }
                    orderDao.insertOrders(orderEntities)
                    Log.d(TAG, "新订单已保存到数据库")
                }

                Result.success(orders)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "API错误：${response.code()} - ${response.message()}")
                Log.e(TAG, "错误响应体：$errorBody")
                Result.failure(Exception("API错误：${response.code()} - ${response.message()} - $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取新订单时发生异常", e)
            e.printStackTrace()
            Result.failure(e)
        } finally {
            Log.d(TAG, "===== 获取新订单完成 =====")
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
            notificationShown = this.notificationShown,
            // 新增字段 for woo food
            deliveryDate = this.deliveryDate,
            deliveryTime = this.deliveryTime,
            orderMethod = this.orderMethod,
            tip = this.tip,
            deliveryFee = this.deliveryFee
        )
    }
    suspend fun getAllHistoricalOrders(): Result<List<Order>> {
        Log.d(TAG, "===== 获取所有历史订单（无日期过滤）=====")
        return try {
            // 明确请求所有订单，不使用日期过滤
            val response = apiService.getOrders(
                consumerKey = apiKey,
                consumerSecret = apiSecret,
                status = null,
                after = null, // 明确设置为null，忽略日期过滤
                perPage = 100
            )

            Log.d(TAG, "历史订单API响应码：${response.code()}")

            if (response.isSuccessful) {
                val orders = response.body() ?: emptyList()
                Log.d(TAG, "成功获取历史订单数量: ${orders.size}")

                // 保存到数据库
                val orderEntities = orders.map { it.toOrderEntity() }
                Log.d(TAG, "准备保存 ${orderEntities.size} 个历史订单到数据库")
                orderDao.insertOrders(orderEntities)
                Log.d(TAG, "历史订单已保存到数据库")

                Result.success(orders)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "获取历史订单API错误：${response.code()} - ${response.message()}")
                Log.e(TAG, "错误响应体：$errorBody")
                Result.failure(Exception("API错误：${response.code()} - ${response.message()} - $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取历史订单时发生异常", e)
            e.printStackTrace()
            Result.failure(e)
        } finally {
            Log.d(TAG, "===== 获取历史订单完成 =====")
        }
    }

    suspend fun getOrder(orderId: Long): Result<Order?> {
        return try {
            val response = apiService.getOrder(
                orderId = orderId,
                consumerKey = apiKey,
                consumerSecret = apiSecret
            )

            if (response.isSuccessful) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("获取订单失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
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

                // Update local database with current timestamp to ensure notification
                orderDao.updateOrderStatus(orderId, newStatus, Date())

                Result.success(updatedOrder)
            } else {
                Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating order status", e)
            Result.failure(e)
        }
    }
}