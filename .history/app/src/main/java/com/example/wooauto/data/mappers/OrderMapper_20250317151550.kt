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
        
        // 解析WooFood信息，包括外卖费和小费
        val woofoodInfo = parseWooFoodInfo(entity)
        
        // 解析额外费用项
        val feeLines = parseFeeLines(entity)
        
        // 计算商品小计
        val subtotal = calculateSubtotal(entity)
        
        return Order(
            id = entity.id,
            number = entity.number.toString(),  // 将Int转换为String
            status = entity.status,
            dateCreated = Date(entity.dateCreated),
            total = entity.total,
            totalTax = entity.totalTax, // 添加税费字段
            subtotal = subtotal, // 计算小计金额
            customerName = entity.customerName,
            contactInfo = entity.contactInfo,
            billingInfo = entity.billingAddress,
            paymentMethod = entity.paymentMethodTitle,
            items = orderItems,
            isPrinted = entity.isPrinted,
            notificationShown = entity.notificationShown,
            notes = entity.customerNote,
            woofoodInfo = woofoodInfo,
            feeLines = feeLines,
            discountTotal = "0.00" // 目前没有折扣信息，使用默认值
        )
    }

    /**
     * 解析WooFood信息
     */
    private fun parseWooFoodInfo(entity: OrderEntity): com.example.wooauto.domain.models.WooFoodInfo? {
        // 判断是否为外卖订单 - 检查shipping地址
        val isDelivery = entity.shippingAddress.isNotEmpty()
        
        // 如果没有任何配送或自取相关信息，返回null
        if (!isDelivery && entity.customerNote.isEmpty()) {
            // 检查是否通过其他方式可以确定是自取订单
            val isPickup = entity.customerNote.contains("自取") || 
                          entity.customerNote.lowercase().contains("pickup") ||
                          entity.paymentMethodTitle.contains("自取")
            
            if (!isPickup) {
                return null
            }
        }
        
        // 提取时间信息
        val timeInfo = extractTimeInfo(entity.customerNote)
        
        // 尝试从订单备注中提取外卖费和小费信息
        var deliveryFee: String? = null
        var tipAmount: String? = null
        
        try {
            // 外卖费正则表达式
            val deliveryFeePattern = "(?:外卖费|配送费|运费)[:：]?\\s*([¥￥]?\\d+(\\.\\d+)?)".toRegex()
            val match = deliveryFeePattern.find(entity.customerNote)
            if (match != null && match.groupValues.size > 1) {
                deliveryFee = match.groupValues[1].replace("[¥￥]".toRegex(), "")
            } else if (isDelivery) {
                // 如果是外卖订单且找不到具体金额，设置一个默认值
                deliveryFee = "10.00"
            }
        } catch (e: Exception) {
            // 出错时使用默认值
            if (isDelivery) deliveryFee = "10.00"
        }
        
        try {
            // 小费正则表达式
            val tipPattern = "(?:小费|感谢费|tip)[:：]?\\s*([¥￥]?\\d+(\\.\\d+)?)".toRegex()
            val match = tipPattern.find(entity.customerNote)
            if (match != null && match.groupValues.size > 1) {
                tipAmount = match.groupValues[1].replace("[¥￥]".toRegex(), "")
            }
        } catch (e: Exception) {
            // 忽略错误
        }
        
        return com.example.wooauto.domain.models.WooFoodInfo(
            orderMethod = if (isDelivery) "delivery" else "pickup",
            deliveryTime = timeInfo,
            deliveryAddress = if (isDelivery) entity.shippingAddress else null,
            deliveryFee = deliveryFee,
            tip = tipAmount,
            isDelivery = isDelivery
        )
    }
    
    /**
     * 从订单备注中提取时间信息
     */
    private fun extractTimeInfo(note: String): String? {
        try {
            // 匹配常见的时间格式
            val timeRegex = "(\\d{1,2}:\\d{2}(\\s*[AaPp][Mm])?)".toRegex()
            val timeMatch = timeRegex.find(note)
            
            if (timeMatch != null) {
                return timeMatch.groupValues[1]
            }
            
            // 匹配中文时间表示
            val chineseTimeRegex = "([上下]午\\s*\\d{1,2}\\s*[点时]\\s*(\\d{1,2}\\s*分钟?)?)".toRegex()
            val chineseTimeMatch = chineseTimeRegex.find(note)
            
            if (chineseTimeMatch != null) {
                return chineseTimeMatch.groupValues[1]
            }
        } catch (e: Exception) {
            // 忽略错误
        }
        
        return null
    }
    
    /**
     * 解析额外费用项
     */
    private fun parseFeeLines(entity: OrderEntity): List<com.example.wooauto.domain.models.FeeLine> {
        val feeLines = mutableListOf<com.example.wooauto.domain.models.FeeLine>()
        
        // 如果有外卖费，添加作为费用项
        val woofoodInfo = parseWooFoodInfo(entity)
        if (woofoodInfo?.isDelivery == true && woofoodInfo.deliveryFee != null) {
            feeLines.add(
                com.example.wooauto.domain.models.FeeLine(
                    id = 1,
                    name = "外卖费",
                    total = woofoodInfo.deliveryFee,
                    totalTax = "0.00"
                )
            )
        }
        
        // 如果有小费，添加作为费用项
        if (woofoodInfo?.tip != null) {
            feeLines.add(
                com.example.wooauto.domain.models.FeeLine(
                    id = 2,
                    name = "小费",
                    total = woofoodInfo.tip,
                    totalTax = "0.00"
                )
            )
        }
        
        return feeLines
    }
    
    /**
     * 计算商品小计
     */
    private fun calculateSubtotal(entity: OrderEntity): String {
        try {
            // 尝试从订单行项目计算小计
            val itemsSubtotal = entity.lineItems.sumOf { lineItem ->
                val price = lineItem.price.toDoubleOrNull() ?: 0.0
                val quantity = lineItem.quantity
                price * quantity
            }
            
            return String.format("%.2f", itemsSubtotal)
        } catch (e: Exception) {
            // 如果计算失败，使用总价减去税费和其他费用
            try {
                val total = entity.total.toDoubleOrNull() ?: 0.0
                val tax = entity.totalTax.toDoubleOrNull() ?: 0.0
                
                // 减去外卖费和小费
                val woofoodInfo = parseWooFoodInfo(entity)
                var otherFees = 0.0
                
                if (woofoodInfo?.deliveryFee != null) {
                    otherFees += woofoodInfo.deliveryFee.toDoubleOrNull() ?: 0.0
                }
                
                if (woofoodInfo?.tip != null) {
                    otherFees += woofoodInfo.tip.toDoubleOrNull() ?: 0.0
                }
                
                val subtotal = total - tax - otherFees
                return String.format("%.2f", subtotal)
            } catch (e: Exception) {
                // 如果还是失败，直接返回总价
                return entity.total
            }
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