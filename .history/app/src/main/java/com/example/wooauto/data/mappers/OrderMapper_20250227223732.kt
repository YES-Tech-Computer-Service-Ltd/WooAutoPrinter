package com.example.wooauto.data.mappers

import com.google.gson.Gson
import com.wooauto.data.local.entities.OrderEntity
import com.wooauto.data.remote.models.BillingResponse
import com.wooauto.data.remote.models.OrderResponse
import com.wooauto.data.remote.models.ShippingResponse
import com.wooauto.domain.models.Order
import java.text.SimpleDateFormat
import java.util.*

/**
 * 订单数据映射器
 * 负责在不同层的订单模型之间进行转换
 */
object OrderMapper {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val gson = Gson()

    /**
     * 将远程API响应模型转换为本地数据库实体
     * @param response API响应的订单模型
     * @return 数据库订单实体
     */
    fun mapResponseToEntity(response: OrderResponse): OrderEntity {
        // 对于配送地址，如果shipping地址为空但是是配送订单，则使用billing地址
        val shippingAddress = if (isEmptyShippingAddress(response.shipping) && response.orderMethod?.equals("delivery", ignoreCase = true) == true) {
            formatAddress(response.billing)
        } else {
            formatAddress(response.shipping)
        }

        return OrderEntity(
            id = response.id,
            number = response.number,
            status = response.status,
            dateCreated = parseDate(response.dateCreated),
            total = response.total,
            customerId = response.customerId,
            customerName = "${response.billing.firstName} ${response.billing.lastName}",
            billingAddress = formatAddress(response.billing),
            shippingAddress = shippingAddress,
            paymentMethod = response.paymentMethod,
            paymentMethodTitle = response.paymentMethodTitle,
            lineItemsJson = gson.toJson(response.lineItems),
            customerNote = response.customerNote,
            isPrinted = false,
            notificationShown = false,
            lastUpdated = Date(),
            deliveryDate = response.deliveryDate,
            deliveryTime = response.deliveryTime,
            orderMethod = response.orderMethod,
            tip = response.tip,
            deliveryFee = response.deliveryFee
        )
    }

    /**
     * 将本地数据库实体转换为领域模型
     * @param entity 数据库订单实体
     * @return 领域订单模型
     */
    fun mapEntityToDomain(entity: OrderEntity): Order {
        return Order(
            id = entity.id,
            number = entity.number,
            status = entity.status,
            dateCreated = entity.dateCreated,
            total = entity.total,
            customerId = entity.customerId,
            customerName = entity.customerName,
            billingAddress = entity.billingAddress,
            shippingAddress = entity.shippingAddress,
            paymentMethod = entity.paymentMethod,
            paymentMethodTitle = entity.paymentMethodTitle,
            lineItemsJson = entity.lineItemsJson,
            customerNote = entity.customerNote,
            isPrinted = entity.isPrinted,
            notificationShown = entity.notificationShown,
            lastUpdated = entity.lastUpdated,
            deliveryDate = entity.deliveryDate,
            deliveryTime = entity.deliveryTime,
            orderMethod = entity.orderMethod,
            tip = entity.tip,
            deliveryFee = entity.deliveryFee
        )
    }

    /**
     * 将领域模型转换为本地数据库实体
     * @param domain 领域订单模型
     * @return 数据库订单实体
     */
    fun mapDomainToEntity(domain: Order): OrderEntity {
        return OrderEntity(
            id = domain.id,
            number = domain.number,
            status = domain.status,
            dateCreated = domain.dateCreated,
            total = domain.total,
            customerId = domain.customerId,
            customerName = domain.customerName,
            billingAddress = domain.billingAddress,
            shippingAddress = domain.shippingAddress,
            paymentMethod = domain.paymentMethod,
            paymentMethodTitle = domain.paymentMethodTitle,
            lineItemsJson = domain.lineItemsJson,
            customerNote = domain.customerNote,
            isPrinted = domain.isPrinted,
            notificationShown = domain.notificationShown,
            lastUpdated = domain.lastUpdated,
            deliveryDate = domain.deliveryDate,
            deliveryTime = domain.deliveryTime,
            orderMethod = domain.orderMethod,
            tip = domain.tip,
            deliveryFee = domain.deliveryFee
        )
    }

    /**
     * 检查shipping地址是否为空
     */
    private fun isEmptyShippingAddress(shipping: ShippingResponse): Boolean {
        return shipping.firstName.isBlank() &&
                shipping.lastName.isBlank() &&
                shipping.address1.isBlank() &&
                shipping.city.isBlank() &&
                shipping.postcode.isBlank()
    }

    /**
     * 从订单中查找小费信息
     * 通常小费可能在fee_lines中，并且name包含"tip"或"gratuity"等关键词
     */
    private fun findTipFromLineItems(response: OrderResponse): String? {
        // 查找是否有名称包含"Tip"或"Appreciation"的费用项
        val tipFee = response.feeLines?.find {
            it.name?.contains("tip", ignoreCase = true) == true ||
                    it.name?.contains("appreciation", ignoreCase = true) == true
        }
        // 根据日志中的数据，"Show Your Appreciation"似乎是小费项
        return tipFee?.total
    }

    /**
     * 从订单中查找配送费信息
     * 通常配送费在fee_lines中，并且name包含"delivery"或"shipping"等关键词
     */
    private fun findDeliveryFeeFromLineItems(response: OrderResponse): String? {
        // 查找是否有名称包含"Delivery"或"Shipping"的费用项
        val deliveryFee = response.feeLines?.find {
            it.name?.contains("delivery", ignoreCase = true) == true ||
                    it.name?.contains("shipping", ignoreCase = true) == true
        }
        // 根据日志中的数据，"Shipping fee"是配送费项
        return deliveryFee?.total
    }

    /**
     * 格式化地址信息
     */
    private fun formatAddress(address: Any): String {
        return when (address) {
            is BillingResponse -> {
                buildString {
                    append("${address.firstName} ${address.lastName}\n")
                    if (!address.phone.isNullOrBlank()) append("${address.phone}\n")
                    append("${address.address1}\n")
                    if (!address.address2.isNullOrBlank()) append("${address.address2}\n")
                    append("${address.city}, ${address.state} ${address.postcode}\n")
                    append(address.country)
                }
            }
            is ShippingResponse -> {
                buildString {
                    append("${address.firstName} ${address.lastName}\n")
                    if (!address.phone.isNullOrBlank()) append("${address.phone}\n")
                    append("${address.address1}\n")
                    if (!address.address2.isNullOrBlank()) append("${address.address2}\n")
                    append("${address.city}, ${address.state} ${address.postcode}\n")
                    append(address.country)
                }
            }
            else -> ""
        }
    }

    /**
     * 解析日期字符串
     */
    private fun parseDate(dateString: String): Date {
        return try {
            dateFormat.parse(dateString) ?: Date()
        } catch (e: Exception) {
            Date()
        }
    }
}