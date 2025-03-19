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
        // 提取元数据信息并构建元数据字符串
        val metadataBuilder = StringBuilder()
        
        // 处理元数据中的订单方式和配送时间
        response.metaData.forEach { meta ->
            when (meta.key) {
                "exwfood_order_method" -> {
                    val orderMethod = meta.value.toString()
                    metadataBuilder.appendLine("exwfood_order_method: $orderMethod")
                }
                "exwfood_time_deli" -> {
                    val orderTime = meta.value.toString()
                    metadataBuilder.appendLine("exwfood_time_deli: $orderTime")
                }
                // 添加其他可能有用的元数据
                else -> {
                    if (meta.key.contains("woofood", ignoreCase = true) || 
                        meta.key.contains("delivery", ignoreCase = true) || 
                        meta.key.contains("shipping", ignoreCase = true) ||
                        meta.key.contains("tip", ignoreCase = true) ||
                        meta.key.contains("exwfood", ignoreCase = true)) {
                        
                        metadataBuilder.appendLine("${meta.key}: ${meta.value}")
                    }
                }
            }
        }
        
        // 处理fee_lines中的小费信息
        response.feeLines?.forEach { fee ->
            val feeName = fee.name.lowercase()
            // 检查是否包含小费相关关键词
            if (feeName.contains("tip") || 
                feeName.contains("小费") || 
                feeName.contains("感谢") || 
                feeName.contains("gratuity") ||
                feeName.contains("appreciation") ||
                feeName.contains("show your appreciation")) {
                
                metadataBuilder.appendLine("Show Your Appreciation: $${fee.total}")
            }
            // 检查是否包含配送费关键词
            else if (feeName.contains("shipping") || 
                     feeName.contains("delivery") ||
                     feeName.contains("外卖费") || 
                     feeName.contains("配送费")) {
                
                metadataBuilder.appendLine("Delivery Fee: $${fee.total}")
            }
        }
        
        // 合并customerNote和元数据
        val customerNote = StringBuilder()
        if (response.customerNote != null && response.customerNote.isNotEmpty()) {
            customerNote.appendLine(response.customerNote)
        }
        
        // 添加元数据到customerNote
        if (metadataBuilder.isNotEmpty()) {
            customerNote.appendLine()
            customerNote.appendLine("--- 元数据 ---")
            customerNote.append(metadataBuilder.toString())
        }
        
        // 映射订单项
        val lineItems = response.lineItems.map { item ->
            OrderLineItemEntity(
                productId = item.productId,
                name = item.name,
                quantity = item.quantity,
                price = item.price,
                total = item.total,
                sku = "" // 如果需要可以添加sku
            )
        }
        
        return OrderEntity(
            id = response.id,
            number = response.number.toIntOrNull() ?: 0,
            status = response.status,
            dateCreated = parseDate(response.dateCreated).time,
            dateModified = System.currentTimeMillis(),
            total = response.total,
            totalTax = response.taxLines.sumOf { it.taxTotal.toDoubleOrNull() ?: 0.0 }.toString(),
            customerName = formatCustomerName(response.billing),
            customerNote = customerNote.toString(),
            contactInfo = formatContactInfo(response.billing),
            lineItems = lineItems,
            billingAddress = formatAddress(response.billing),
            shippingAddress = formatAddress(response.shipping),
            paymentMethod = response.paymentMethod,
            paymentMethodTitle = response.paymentMethodTitle,
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
            isPrinted = false,
            notificationShown = false,
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
        // 优先从元数据中提取订单方式
        var orderMethod: String? = null
        var isDelivery = false
        
        // 查找元数据标记
        if (entity.customerNote.contains("exwfood_order_method", ignoreCase = true)) {
            val orderMethodPattern = "exwfood_order_method[：:]*\\s*([\\w]+)".toRegex(RegexOption.IGNORE_CASE)
            val match = orderMethodPattern.find(entity.customerNote)
            if (match != null && match.groupValues.size > 1) {
                orderMethod = match.groupValues[1].trim()
                isDelivery = orderMethod.equals("delivery", ignoreCase = true)
            }
        }
        
        // 如果元数据中没有找到，则通过shipping地址判断
        if (orderMethod == null) {
            isDelivery = entity.shippingAddress.isNotEmpty()
            orderMethod = if (isDelivery) "delivery" else "takeaway"
        }
        
        // 如果没有任何配送或自取相关信息，且无法确定是自取订单，则返回null
        if (!isDelivery && entity.customerNote.isEmpty()) {
            // 检查是否通过其他方式可以确定是自取订单
            val isPickup = entity.customerNote.contains("自取") || 
                          entity.customerNote.lowercase().contains("pickup") ||
                          entity.customerNote.lowercase().contains("takeaway") ||
                          entity.paymentMethodTitle.contains("自取")
            
            if (!isPickup) {
                return null
            }
        }
        
        // 提取时间信息
        val timeInfo = extractTimeInfoFromMetadata(entity.customerNote)
        
        // 从订单的customerNote中提取外卖费和小费信息
        var deliveryFee: String? = null
        var tipAmount: String? = null
        
        // 从元数据提取外卖费
        try {
            // 外卖费可能出现在元数据中
            val deliveryFeePattern = "(?:外卖费|配送费|运费|Shipping fee|Delivery fee)[：:]*\\s*([¥￥$]?\\s*\\d+(\\.\\d+)?)".toRegex(RegexOption.IGNORE_CASE)
            val deliveryFeeMatch = deliveryFeePattern.find(entity.customerNote)
            
            if (deliveryFeeMatch != null && deliveryFeeMatch.groupValues.size > 1) {
                deliveryFee = deliveryFeeMatch.groupValues[1].replace("[¥￥$\\s]".toRegex(), "")
            } else if (isDelivery) {
                // 如果是外卖订单但没有找到费用，检查是否有默认外卖费
                val defaultFeePattern = "外卖费[:：]\\s*默认\\s*([¥￥$]?\\s*\\d+(\\.\\d+)?)".toRegex(RegexOption.IGNORE_CASE)
                val defaultMatch = defaultFeePattern.find(entity.customerNote)
                if (defaultMatch != null && defaultMatch.groupValues.size > 1) {
                    deliveryFee = defaultMatch.groupValues[1].replace("[¥￥$\\s]".toRegex(), "")
                }
            }
        } catch (e: Exception) {
            // 出错时记录错误但不使用默认值
        }
        
        // 从元数据提取小费信息
        try {
            // 小费可能以"小费:"、"Show Your Appreciation"等形式存在于元数据中
            val tipPattern = "(?:小费|感谢费|Tip|gratuity|Show Your Appreciation)[：:]*\\s*([¥￥$]?\\s*\\d+(\\.\\d+)?)".toRegex(RegexOption.IGNORE_CASE)
            val tipMatch = tipPattern.find(entity.customerNote)
            
            if (tipMatch != null && tipMatch.groupValues.size > 1) {
                tipAmount = tipMatch.groupValues[1].replace("[¥￥$\\s]".toRegex(), "")
            }
        } catch (e: Exception) {
            // 忽略错误
        }
        
        return com.example.wooauto.domain.models.WooFoodInfo(
            orderMethod = orderMethod,
            deliveryTime = timeInfo,
            deliveryAddress = if (isDelivery) entity.shippingAddress else null,
            deliveryFee = deliveryFee,
            tip = tipAmount,
            isDelivery = isDelivery
        )
    }
    
    /**
     * 从元数据中提取时间信息
     */
    private fun extractTimeInfoFromMetadata(note: String): String? {
        try {
            // 首先检查是否有exwfood_time_deli元数据
            val wooFoodTimePattern = "exwfood_time_deli[：:]*\\s*([\\d:\\s\\-]+(?:\\s*[AaPp][Mm])?)".toRegex()
            val wooFoodTimeMatch = wooFoodTimePattern.find(note)
            
            if (wooFoodTimeMatch != null && wooFoodTimeMatch.groupValues.size > 1) {
                return wooFoodTimeMatch.groupValues[1].trim()
            }
            
            // 如果没有找到元数据时间，尝试常规的时间提取
            return extractTimeInfo(note)
        } catch (e: Exception) {
            // 发生错误时使用常规提取
            return extractTimeInfo(note)
        }
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
            
            // 匹配时间范围格式，如"19:20 - 19:40"
            val timeRangeRegex = "(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})".toRegex()
            val timeRangeMatch = timeRangeRegex.find(note)
            
            if (timeRangeMatch != null) {
                return "${timeRangeMatch.groupValues[1]} - ${timeRangeMatch.groupValues[2]}"
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
        
        // 检查是否有WooFood信息
        val woofoodInfo = parseWooFoodInfo(entity)
        
        // 添加配送费（如果有）
        woofoodInfo?.deliveryFee?.let { fee ->
            if (fee.isNotEmpty() && fee != "0" && fee != "0.00") {
                feeLines.add(com.example.wooauto.domain.models.FeeLine(
                    id = 1,
                    name = "配送费",
                    total = fee,
                    totalTax = "0.00"
                ))
            }
        }
        
        // 添加小费（如果有）
        woofoodInfo?.tip?.let { tip ->
            if (tip.isNotEmpty() && tip != "0" && tip != "0.00") {
                feeLines.add(com.example.wooauto.domain.models.FeeLine(
                    id = 2,
                    name = "小费",
                    total = tip,
                    totalTax = "0.00"
                ))
            }
        }
        
        // 从订单备注中查找其他可能的费用项
        if (entity.customerNote.isNotEmpty()) {
            // 查找Show Your Appreciation小费
            if (entity.customerNote.contains("Show Your Appreciation", ignoreCase = true)) {
                val regex = "Show Your Appreciation[:：]?\\s*\\$?([0-9]+\\.?[0-9]*)".toRegex(RegexOption.IGNORE_CASE)
                val matchResult = regex.find(entity.customerNote)
                
                if (matchResult != null && matchResult.groupValues.size > 1) {
                    val tipAmount = matchResult.groupValues[1]
                    // 确保不重复添加小费
                    if (!feeLines.any { it.name == "小费" }) {
                        feeLines.add(com.example.wooauto.domain.models.FeeLine(
                            id = 2,
                            name = "小费",
                            total = tipAmount,
                            totalTax = "0.00"
                        ))
                    }
                }
            }
            
            // 查找Delivery Fee配送费
            if (entity.customerNote.contains("Delivery Fee", ignoreCase = true)) {
                val regex = "Delivery Fee[:：]?\\s*\\$?([0-9]+\\.?[0-9]*)".toRegex(RegexOption.IGNORE_CASE)
                val matchResult = regex.find(entity.customerNote)
                
                if (matchResult != null && matchResult.groupValues.size > 1) {
                    val feeAmount = matchResult.groupValues[1]
                    // 确保不重复添加配送费
                    if (!feeLines.any { it.name == "配送费" }) {
                        feeLines.add(com.example.wooauto.domain.models.FeeLine(
                            id = 1,
                            name = "配送费",
                            total = feeAmount,
                            totalTax = "0.00"
                        ))
                    }
                }
            }
        }
        
        return feeLines
    }
    
    /**
     * 计算订单商品小计
     * 注意：小计 = 总计 - 税费 - 配送费 - 小费
     */
    private fun calculateSubtotal(entity: OrderEntity): String {
        try {
            // 获取总金额
            val total = entity.total.toDoubleOrNull() ?: 0.0
            
            // 获取税费
            val tax = entity.totalTax.toDoubleOrNull() ?: 0.0
            
            // 获取WooFood信息
            val woofoodInfo = parseWooFoodInfo(entity)
            
            // 获取配送费
            var deliveryFee = 0.0
            woofoodInfo?.deliveryFee?.let {
                if (it.isNotEmpty()) {
                    deliveryFee = it.toDoubleOrNull() ?: 0.0
                }
            }
            
            // 获取小费
            var tip = 0.0
            woofoodInfo?.tip?.let {
                if (it.isNotEmpty()) {
                    tip = it.toDoubleOrNull() ?: 0.0
                }
            }
            
            // 如果在WooFood信息中没有找到配送费或小费，尝试从订单备注中提取
            if (deliveryFee == 0.0 && entity.customerNote.contains("Delivery Fee", ignoreCase = true)) {
                val regex = "Delivery Fee[:：]?\\s*\\$?([0-9]+\\.?[0-9]*)".toRegex(RegexOption.IGNORE_CASE)
                val matchResult = regex.find(entity.customerNote)
                
                if (matchResult != null && matchResult.groupValues.size > 1) {
                    deliveryFee = matchResult.groupValues[1].toDoubleOrNull() ?: 0.0
                }
            }
            
            if (tip == 0.0 && entity.customerNote.contains("Show Your Appreciation", ignoreCase = true)) {
                val regex = "Show Your Appreciation[:：]?\\s*\\$?([0-9]+\\.?[0-9]*)".toRegex(RegexOption.IGNORE_CASE)
                val matchResult = regex.find(entity.customerNote)
                
                if (matchResult != null && matchResult.groupValues.size > 1) {
                    tip = matchResult.groupValues[1].toDoubleOrNull() ?: 0.0
                }
            }
            
            // 计算小计
            val subtotal = total - tax - deliveryFee - tip
            
            // 确保小计不小于0
            return String.format("%.2f", if (subtotal < 0) 0.0 else subtotal)
        } catch (e: Exception) {
            // 如果计算失败，返回lineItems的总和作为小计
            return entity.lineItems.sumOf { it.total.toDoubleOrNull() ?: 0.0 }.toString()
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
     * 解析日期字符串为Date对象
     */
    private fun parseDate(dateString: String): Date {
        return try {
            dateFormat.parse(dateString) ?: Date()
        } catch (e: Exception) {
            Date()
        }
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
     * 将实体列表转换为领域模型列表
     */
    fun mapEntityListToDomainList(entities: List<OrderEntity>): List<Order> {
        return entities.map { mapEntityToDomain(it) }
    }

    /**
     * 格式化客户名称
     */
    private fun formatCustomerName(billing: BillingResponse): String {
        return "${billing.firstName} ${billing.lastName}"
    }
}