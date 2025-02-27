package com.example.wooauto.data.repositories

import android.util.Log
import com.example.wooauto.data.api.WooCommerceApiService
import com.example.wooauto.data.api.models.Order
import com.example.wooauto.data.api.models.MetaData
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
                    Log.d(TAG, "订单[$index] - 配送方式: ${order.orderMethod}, 配送日期: ${order.deliveryDate}, 配送时间: ${order.deliveryTime}")
                    Log.d(TAG, "订单[$index] - 小费: ${order.tip}, 配送费: ${order.deliveryFee}")
                    Log.d(TAG, "订单[$index] - 元数据数量: ${order.metaData.size}")
                    order.metaData.forEach { meta ->
                        Log.d(TAG, "订单[$index] - 元数据: ${meta.key} = ${meta.value}")
                    }
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
                    Log.d(TAG, "新订单[$index] - 配送方式: ${order.orderMethod}, 配送日期: ${order.deliveryDate}, 配送时间: ${order.deliveryTime}")
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
        // Add detailed logging for debugging
        Log.d(TAG, "转换订单 ${id} 到数据库实体")
        Log.d(TAG, "订单元数据数量: ${metaData.size}")
        Log.d(TAG, "订单配送信息: 方式=${orderMethod}, 日期=${deliveryDate}, 时间=${deliveryTime}")

        // Process line items to clean up metadata before serialization
        val processedLineItems = lineItems.map { item ->
            // Create a copy of the line item with processed metadata
            // This ensures we store formatted metadata in the database
            val processedMetadata = item.metaData?.map { meta ->
                when (meta.key) {
                    "_exceptions" -> {
                        // Parse exceptions data for better display
                        val value = meta.value.toString()
                        val formattedValue = parseWooFoodOptions(value)
                        MetaData(meta.id, "options", formattedValue)
                    }
                    else -> meta
                }
            }
            // Return modified line item with processed metadata
            item.copy(metaData = processedMetadata)
        }

        // Use the processed line items for serialization
        val itemsJson = try {
            gson.toJson(processedLineItems)
        } catch (e: Exception) {
            Log.e(TAG, "序列化订单项目失败", e)
            "[]" // 提供一个空数组作为备选
        }

        Log.d(TAG, "订单项目JSON长度: ${itemsJson.length}")

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
            lineItemsJson = itemsJson,
            isPrinted = this.isPrinted,
            notificationShown = this.notificationShown,
            // WooCommerce Food字段
            deliveryDate = this.deliveryDate,
            deliveryTime = this.deliveryTime,
            orderMethod = this.orderMethod,
            tip = this.tip,
            deliveryFee = this.deliveryFee
        )
    }
    /**
     * 将OrderEntity转换回完整的Order对象
     * 用于OrderViewModel获取完整的Order数据结构
     */
    fun orderEntityToFullOrder(entity: OrderEntity): Order? {
        return try {
            // 还原订单项目
            val lineItemType = object : com.google.gson.reflect.TypeToken<List<com.example.wooauto.data.api.models.LineItem>>() {}.type
            val lineItems = gson.fromJson<List<com.example.wooauto.data.api.models.LineItem>>(entity.lineItemsJson, lineItemType) ?: emptyList()

            // 创建一个简化的Order对象，仅包含显示所需的字段
            // 因为我们没有完整的Order构造函数，这里只是示意
            // 在实际应用中，可能需要更全面的转换或使用Builder模式
            Log.d(TAG, "从实体还原Order对象 ID: ${entity.id}")

            // 注意：这里在实际应用中可能需要补充更多字段
            null
        } catch (e: Exception) {
            Log.e(TAG, "还原Order对象失败", e)
            null
        }
    }

    private fun parseWooFoodOptions(exceptionData: String): String {
        try {
            // Remove curly braces and other formatting
            val cleaned = exceptionData.replace("{", "").replace("}", "").replace("(", "").replace(")", "")

            // Extract key parts
            val valuePart = cleaned.split("value=").getOrElse(1) { "" }.split(",").firstOrNull() ?: ""
            val typePart = if (cleaned.contains("_type=")) {
                cleaned.split("_type=").getOrElse(1) { "" }.split(",").firstOrNull() ?: ""
            } else ""

            // Format for better display
            return "Selected: $valuePart (Type: $typePart)"
        } catch (e: Exception) {
            Log.e(TAG, "解析WooFood选项时出错", e)
            return exceptionData // Return original if parsing fails
        }
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
            Log.d(TAG, "===== 开始获取订单 ID: $orderId =====")
            val response = apiService.getOrder(
                orderId = orderId,
                consumerKey = apiKey,
                consumerSecret = apiSecret
            )

            Log.d(TAG, "API响应码: ${response.code()}")
            
            if (response.isSuccessful) {
                val order = response.body()
                if (order != null) {
                    Log.d(TAG, """
                        API返回订单数据:
                        - ID: ${order.id}
                        - 编号: ${order.number}
                        - 状态: ${order.status}
                        - 元数据数量: ${order.metaData.size}
                        - 元数据内容: ${order.metaData.joinToString("\n") { "  ${it.key}=${it.value}" }}
                        - 费用行数量: ${order.feeLines.size}
                        - 费用内容: ${order.feeLines.joinToString("\n") { "  ${it.name}=${it.total}" }}
                    """.trimIndent())

                    // 转换为实体并保存到数据库
                    val entity = order.toOrderEntity()
                    Log.d(TAG, """
                        转换后的实体数据:
                        - 配送方式: ${entity.orderMethod ?: "未设置"}
                        - 配送日期: ${entity.deliveryDate ?: "未设置"}
                        - 配送时间: ${entity.deliveryTime ?: "未设置"}
                        - 小费: ${entity.tip ?: "未设置"}
                        - 配送费: ${entity.deliveryFee ?: "未设置"}
                    """.trimIndent())
                    
                    orderDao.insertOrder(entity)
                    Log.d(TAG, "订单数据已保存到数据库")
                }
                Result.success(response.body())
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "获取订单失败: ${response.code()} - ${response.message()}")
                Log.e(TAG, "错误响应体: $errorBody")
                Result.failure(Exception("获取订单失败: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取订单时发生异常", e)
            Result.failure(e)
        } finally {
            Log.d(TAG, "===== 获取订单完成 =====")
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

    /**
     * 验证API返回的订单数据
     * 用于诊断API数据问题
     */
    suspend fun validateOrderData(orderId: Long): Result<Map<String, Any>> {
        return try {
            Log.d(TAG, "开始验证订单 $orderId 的API数据")

            val response = apiService.getOrder(
                orderId = orderId,
                consumerKey = apiKey,
                consumerSecret = apiSecret
            )

            if (response.isSuccessful) {
                val order = response.body()
                if (order != null) {
                    // 收集诊断信息
                    val diagnosticInfo = mutableMapOf<String, Any>()

                    // 基本信息
                    diagnosticInfo["orderId"] = order.id
                    diagnosticInfo["orderNumber"] = order.number
                    diagnosticInfo["status"] = order.status

                    // 元数据分析
                    val metaDataInfo = order.metaData.associate { it.key to it.value.toString() }
                    diagnosticInfo["metaData"] = metaDataInfo

                    // 检查WooCommerce Food特定字段
                    val wooFoodFields = listOf(
                        "exwfood_date_deli",
                        "exwfood_time_deli",
                        "exwfood_timeslot",
                        "exwfood_order_method",
                        "woofood_order_type"
                    )

                    val foundWooFoodFields = metaDataInfo.keys.filter { key ->
                        wooFoodFields.any { field -> key.contains(field, ignoreCase = true) }
                    }

                    diagnosticInfo["foundWooFoodFields"] = foundWooFoodFields

                    // 费用行分析
                    val feeLines = order.feeLines.map { fee ->
                        mapOf(
                            "name" to fee.name,
                            "total" to fee.total
                        )
                    }
                    diagnosticInfo["feeLines"] = feeLines

                    // 解析后的WooFood字段
                    diagnosticInfo["parsedDeliveryDate"] = order.deliveryDate ?: "null"
                    diagnosticInfo["parsedDeliveryTime"] = order.deliveryTime ?: "null"
                    diagnosticInfo["parsedOrderMethod"] = order.orderMethod ?: "null"
                    diagnosticInfo["parsedTip"] = order.tip ?: "null"
                    diagnosticInfo["parsedDeliveryFee"] = order.deliveryFee ?: "null"

                    // 订单项目分析
                    diagnosticInfo["lineItemsCount"] = order.lineItems.size

                    Log.d(TAG, "订单验证完成: $diagnosticInfo")
                    Result.success(diagnosticInfo)
                } else {
                    Log.e(TAG, "订单为空")
                    Result.failure(Exception("订单数据为空"))
                }
            } else {
                Log.e(TAG, "API响应失败: ${response.code()} - ${response.message()}")
                Result.failure(Exception("API错误: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "验证订单数据时发生异常", e)
            Result.failure(e)
        }
    }
}