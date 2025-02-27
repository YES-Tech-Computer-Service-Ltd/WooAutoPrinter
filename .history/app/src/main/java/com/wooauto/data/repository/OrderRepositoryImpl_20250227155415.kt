package com.wooauto.data.repository

import com.google.gson.Gson
import com.wooauto.data.local.dao.OrderDao
import com.wooauto.data.local.entities.OrderEntity
import com.wooauto.data.remote.api.WooCommerceApiService
import com.wooauto.data.remote.models.OrderResponse
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
    private val gson = Gson()

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

    /**
     * 将OrderResponse转换为OrderEntity
     */
    private fun OrderResponse.toEntity(): OrderEntity {
        return OrderEntity(
            id = id,
            number = number,
            status = status,
            dateCreated = dateFormat.parse(dateCreated) ?: Date(),
            total = total,
            customerId = customerId,
            customerName = "${billing.firstName} ${billing.lastName}",
            billingAddress = formatAddress(billing),
            shippingAddress = formatAddress(shipping),
            paymentMethod = paymentMethod,
            paymentMethodTitle = paymentMethodTitle,
            lineItemsJson = gson.toJson(lineItems),
            customerNote = customerNote,
            isPrinted = false,
            notificationShown = false,
            lastUpdated = Date(),
            deliveryDate = deliveryDate,
            deliveryTime = deliveryTime,
            orderMethod = orderMethod,
            tip = tip,
            deliveryFee = deliveryFee
        )
    }

    /**
     * 将OrderEntity转换为Order领域模型
     */
    private fun OrderEntity.toDomainModel(): Order {
        return Order(
            id = id,
            number = number,
            status = status,
            dateCreated = dateCreated,
            total = total,
            customerId = customerId,
            customerName = customerName,
            billingAddress = billingAddress,
            shippingAddress = shippingAddress,
            paymentMethod = paymentMethod,
            paymentMethodTitle = paymentMethodTitle,
            lineItemsJson = lineItemsJson,
            customerNote = customerNote,
            isPrinted = isPrinted,
            notificationShown = notificationShown,
            lastUpdated = lastUpdated,
            deliveryDate = deliveryDate,
            deliveryTime = deliveryTime,
            orderMethod = orderMethod,
            tip = tip,
            deliveryFee = deliveryFee
        )
    }

    /**
     * 格式化地址信息
     */
    private fun formatAddress(address: Any): String {
        return when (address) {
            is com.wooauto.data.remote.models.BillingResponse -> {
                buildString {
                    append("${address.firstName} ${address.lastName}\n")
                    append("${address.address1}\n")
                    if (!address.address2.isNullOrBlank()) append("${address.address2}\n")
                    append("${address.city}, ${address.state} ${address.postcode}\n")
                    append(address.country)
                }
            }
            is com.wooauto.data.remote.models.ShippingResponse -> {
                buildString {
                    append("${address.firstName} ${address.lastName}\n")
                    append("${address.address1}\n")
                    if (!address.address2.isNullOrBlank()) append("${address.address2}\n")
                    append("${address.city}, ${address.state} ${address.postcode}\n")
                    append(address.country)
                }
            }
            else -> ""
        }
    }
} 