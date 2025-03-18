package com.example.wooauto.data.mappers

import com.google.gson.Gson
import com.example.wooauto.data.local.entities.OrderEntity
import com.example.wooauto.data.local.entities.OrderLineItemEntity
import com.example.wooauto.data.remote.models.BillingResponse
import com.example.wooauto.data.remote.models.OrderResponse
import com.example.wooauto.data.remote.models.ShippingResponse
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.OrderItem
import java.text.SimpleDateFormat
import java.util.*
import com.google.gson.reflect.TypeToken

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

        // 转换订单项
        val lineItems = response.lineItems.map { item ->
            OrderLineItemEntity(
                productId = item.productId,
                name = item.name,
                quantity = item.quantity,
                price = item.price,
                total = item.total,
                sku = ""  // OrderItemResponse可能没有sku字段，使用空字符串
            )
        }

        return OrderEntity(
            id = response.id,
            number = response.number.toInt(),  // 将String转换为Int
            status = response.status,
            dateCreated = parseDate(dateString = response.dateCreated).time,
            dateModified = System.currentTimeMillis(),
            customerName = "${response.billing.firstName} ${response.billing.lastName}",
            customerNote = response.customerNote ?: "",
            contactInfo = formatContactInfo(response.billing),
            total = response.total,
            totalTax = response.taxLines.sumOf { it.taxTotal.toDoubleOrNull() ?: 0.0 }.toString(),  // 计算总税费
            lineItems = lineItems,
            billingAddress = formatAddress(response.billing),
            shippingAddress = shippingAddress,
            paymentMethod = response.paymentMethod ?: "",
            paymentMethodTitle = response.paymentMethodTitle ?: "",
            isPrinted = false,
            notificationShown = false,
            lastUpdated = System.currentTimeMillis()
        )
    }

    /**
     * 格式化联系信息
     */
    private fun formatContactInfo(billing: BillingResponse): String {
        return billing.phone ?: ""
    }

    /**
     * 将本地数据库实体转换为领域模型
     * @param entity 数据库订单实体
     * @return 领域订单模型
     */
    fun mapEntityToDomain(entity: OrderEntity): Order {
        // 将OrderLineItemEntity列表转换为OrderItem列表
        val orderItems = entity.lineItems.map { lineItem ->
            OrderItem(
                id = 0, // OrderLineItemEntity没有id字段
                productId = lineItem.productId,
                name = lineItem.name,
                quantity = lineItem.quantity,
                price = lineItem.price,
                subtotal = lineItem.price, // 假设小计等于单价
                total = lineItem.total,
                image = ""
            )
        }
        
        return Order(
            id = entity.id,
            number = entity.number.toString(),  // 将Int转换为String
            status = entity.status,
            dateCreated = Date(entity.dateCreated),
            total = entity.total,
            totalTax = entity.totalTax, // 添加税费字段
            subtotal = calculateSubtotal(entity), // 计算小计金额
            customerName = entity.customerName,
            contactInfo = entity.contactInfo,
            billingInfo = entity.billingAddress,
            paymentMethod = entity.paymentMethodTitle,
            items = orderItems,
            isPrinted = entity.isPrinted,
            notificationShown = entity.notificationShown,
            notes = entity.customerNote
        )
    }

    /**
     * 计算订单小计金额
     * 如果订单实体中没有小计字段，则从订单项中计算
     */
    private fun calculateSubtotal(entity: OrderEntity): String {
        // 尝试从订单行项目计算小计
        val itemsSubtotal = entity.lineItems.sumOf { 
            it.price.toDoubleOrNull()?.times(it.quantity) ?: 0.0 
        }
        
        // 如果能够计算出小计，则使用计算结果
        // 否则使用订单总价减去税费作为小计
        return if (itemsSubtotal > 0.0) {
            String.format("%.2f", itemsSubtotal)
        } else {
            val total = entity.total.toDoubleOrNull() ?: 0.0
            val tax = entity.totalTax.toDoubleOrNull() ?: 0.0
            val subtotal = total - tax
            String.format("%.2f", subtotal)
        }
    }

    /**
     * 将领域模型转换为本地数据库实体
     * @param domain 领域订单模型
     * @return 数据库订单实体
     */
    fun mapDomainToEntity(domain: Order): OrderEntity {
        // 将OrderItem列表转换为OrderLineItemEntity列表
        val lineItems = domain.items.map { item ->
            OrderLineItemEntity(
                productId = item.productId,
                name = item.name,
                quantity = item.quantity,
                price = item.price,
                total = item.total,
                sku = ""
            )
        }
        
        return OrderEntity(
            id = domain.id,
            number = domain.number.toIntOrNull() ?: 0,  // 将String转换为Int，如果转换失败则使用0
            status = domain.status,
            dateCreated = domain.dateCreated.time,
            dateModified = System.currentTimeMillis(),
            customerName = domain.customerName,
            customerNote = domain.notes,
            contactInfo = domain.contactInfo,
            total = domain.total,
            totalTax = domain.totalTax, // 使用领域模型中的税费
            lineItems = lineItems,
            billingAddress = domain.billingInfo,
            shippingAddress = domain.contactInfo,
            paymentMethod = domain.paymentMethod,
            paymentMethodTitle = domain.paymentMethod,
            isPrinted = domain.isPrinted,
            notificationShown = domain.notificationShown,
            lastUpdated = System.currentTimeMillis()
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

    /**
     * 将实体列表转换为领域模型列表
     */
    fun mapEntityListToDomainList(entities: List<OrderEntity>): List<Order> {
        return entities.map { mapEntityToDomain(it) }
    }
}