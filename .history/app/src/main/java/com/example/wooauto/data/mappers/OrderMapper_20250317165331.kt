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
        
        // 处理fee_lines中的费用信息
        var tipAmount = "0.00"
        var deliveryFeeAmount = "0.00"
        
        // 直接使用OrderResponse对象的扩展方法获取值
        val directTip = response.tip
        val directDeliveryFee = response.deliveryFee
        
        // 打印原始feeLines，方便调试
        metadataBuilder.appendLine("===API原始feeLines===")
        response.feeLines?.forEach { fee ->
            metadataBuilder.appendLine("${fee.name}: $${fee.total}")
        }
        metadataBuilder.appendLine("==================")
        
        // 使用OrderResponse扩展方法获取的值 
        if (!directTip.isNullOrEmpty()) {
            tipAmount = directTip
            metadataBuilder.appendLine("扩展方法提取的小费: $$tipAmount")
        }
        
        if (!directDeliveryFee.isNullOrEmpty()) {
            deliveryFeeAmount = directDeliveryFee
            metadataBuilder.appendLine("扩展方法提取的配送费: $$deliveryFeeAmount")
        }
        
        // 如果扩展方法未提取到，则遍历所有feeLines
        if (tipAmount == "0.00" || deliveryFeeAmount == "0.00") {
            response.feeLines?.forEach { fee ->
                val feeName = fee.name.lowercase()
                
                // 检查是否包含小费相关关键词
                if (tipAmount == "0.00" && (
                    feeName.equals("show your appreciation", ignoreCase = true) || 
                    feeName.contains("tip", ignoreCase = true) || 
                    feeName.contains("小费", ignoreCase = true) || 
                    feeName.contains("感谢", ignoreCase = true) || 
                    feeName.contains("gratuity", ignoreCase = true) ||
                    feeName.contains("appreciation", ignoreCase = true))) {
                    
                    tipAmount = fee.total
                    metadataBuilder.appendLine("从feeLines直接提取的小费: $${fee.total}")
                }
                // 检查是否包含配送费关键词
                else if (deliveryFeeAmount == "0.00" && (
                    feeName.equals("shipping fee", ignoreCase = true) ||
                    feeName.contains("shipping", ignoreCase = true) || 
                    feeName.contains("delivery", ignoreCase = true) ||
                    feeName.contains("外卖费", ignoreCase = true) || 
                    feeName.contains("配送费", ignoreCase = true))) {
                    
                    deliveryFeeAmount = fee.total
                    metadataBuilder.appendLine("从feeLines直接提取的配送费: $${fee.total}")
                }
            }
        }
        
        // 添加最终提取到的值
        metadataBuilder.appendLine("API最终提取的小费: $${tipAmount}")
        metadataBuilder.appendLine("API最终提取的配送费: $${deliveryFeeAmount}")
        
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
        
        // 检查是否有小费，如果没有，添加一个默认的小费项目
        val hasFeeTip = feeLines.any { it.name.contains("小费", ignoreCase = true) }
        val finalFeeLines = if (hasFeeTip) {
            feeLines
        } else {
            // 添加默认小费项（即使金额为0）
            val tipFeeLine = com.example.wooauto.domain.models.FeeLine(
                id = feeLines.size + 1L,
                name = "小费",
                total = woofoodInfo?.tip ?: "0.00",
                totalTax = "0.00"
            )
            feeLines + tipFeeLine
        }
        
        // 如果是外卖订单，检查是否有配送费
        val isDelivery = woofoodInfo?.isDelivery ?: false
        val hasDeliveryFee = feeLines.any { 
            it.name.contains("配送费", ignoreCase = true) || 
            it.name.contains("外卖费", ignoreCase = true) 
        }
        
        val finalFeeLines2 = if (isDelivery && !hasDeliveryFee) {
            // 添加默认配送费项（即使金额为0）
            val deliveryFeeLine = com.example.wooauto.domain.models.FeeLine(
                id = finalFeeLines.size + 1L,
                name = "配送费",
                total = woofoodInfo?.deliveryFee ?: "0.00",
                totalTax = "0.00"
            )
            finalFeeLines + deliveryFeeLine
        } else {
            finalFeeLines
        }
        
        // 打印所有费用行，帮助调试
        println("订单#${entity.number}的所有费用行:")
        finalFeeLines2.forEach { feeLine ->
            println("  - ${feeLine.name}: ${feeLine.total}")
        }
        
        // 计算商品小计
        val subtotal = calculateSubtotal(entity)
        
        return Order(
            id = entity.id,
            number = entity.number.toString(),
            status = entity.status,
            dateCreated = Date(entity.dateCreated),
            total = entity.total,
            totalTax = entity.totalTax,
            subtotal = subtotal,
            customerName = entity.customerName,
            contactInfo = entity.contactInfo,
            billingInfo = entity.billingAddress,
            paymentMethod = entity.paymentMethodTitle,
            items = orderItems,
            isPrinted = entity.isPrinted,
            notificationShown = entity.notificationShown,
            notes = entity.customerNote,
            woofoodInfo = woofoodInfo,
            feeLines = finalFeeLines2,
            discountTotal = "0.00" // 目前没有折扣信息，使用默认值
        )
    }

    /**
     * 解析WooFood信息
     */
    private fun parseWooFoodInfo(entity: OrderEntity): com.example.wooauto.domain.models.WooFoodInfo? {
        // 先检查明确的自取关键词
        val isPickupByKeywords = entity.customerNote.contains("自取", ignoreCase = true) || 
                         entity.customerNote.lowercase().contains("pickup") ||
                         entity.customerNote.lowercase().contains("pick up") ||
                         entity.customerNote.lowercase().contains("collected") ||
                         entity.customerNote.lowercase().contains("collection") ||
                         entity.customerNote.lowercase().contains("takeaway") ||
                         entity.customerNote.lowercase().contains("take away") ||
                         entity.customerNote.lowercase().contains("take-away") ||
                         entity.paymentMethodTitle.contains("自取", ignoreCase = true) ||
                         (entity.customerNote.lowercase().contains("asap") && 
                          !entity.customerNote.lowercase().contains("deliver"))
        
        // 如果确定是自取订单，直接返回自取信息
        if (isPickupByKeywords) {
            return com.example.wooauto.domain.models.WooFoodInfo(
                orderMethod = "takeaway",
                deliveryTime = extractTimeInfoFromMetadata(entity.customerNote),
                deliveryAddress = null,
                deliveryFee = null,  // 自取订单没有配送费
                tip = extractTipAmount(entity.customerNote),
                isDelivery = false
            )
        }
        
        // 其他情况下继续使用原有逻辑
        var orderMethod: String? = null
        var isDelivery = false
        
        // 查找元数据标记
        if (entity.customerNote.contains("exwfood_order_method", ignoreCase = true)) {
            val orderMethodPattern = "exwfood_order_method[：:]*\\s*([\\w]+)".toRegex(RegexOption.IGNORE_CASE)
            val match = orderMethodPattern.find(entity.customerNote)
            if (match != null && match.groupValues.size > 1) {
                val extractedMethod = match.groupValues[1].trim().lowercase()
                // 严格区分自取和外卖
                isDelivery = extractedMethod == "delivery"
                orderMethod = if (isDelivery) "delivery" else "takeaway"
            }
        }
        
        // 如果元数据中没有找到，则通过其他信息判断
        if (orderMethod == null) {
            // 检查是否有shipping地址 - 有地址通常意味着配送订单
            val hasShippingAddress = entity.shippingAddress.isNotEmpty() 
                && !entity.shippingAddress.equals(entity.billingAddress)
            
            // 检查备注中是否有自取相关关键词 - 前面已经检查过了，这里是额外确认
            val hasPickupKeywords = entity.customerNote.contains("自取", ignoreCase = true) || 
                          entity.customerNote.lowercase().contains("pickup") ||
                          entity.customerNote.lowercase().contains("takeaway") ||
                          entity.customerNote.lowercase().contains("pick up") ||
                          entity.paymentMethodTitle.contains("自取", ignoreCase = true)
            
            // 检查备注中是否有外卖相关关键词
            val hasDeliveryKeywords = entity.customerNote.contains("外卖", ignoreCase = true) || 
                           entity.customerNote.contains("配送", ignoreCase = true) || 
                           entity.customerNote.lowercase().contains("delivery") ||
                           entity.customerNote.lowercase().contains("deliver to") ||
                           entity.paymentMethodTitle.contains("外卖", ignoreCase = true)
            
            // 确定订单类型：如果明确有自取关键词，则为自取；如果有配送地址或配送关键词，则为外卖
            isDelivery = if (hasPickupKeywords) false else (hasShippingAddress || hasDeliveryKeywords)
            orderMethod = if (isDelivery) "delivery" else "takeaway"
        }
        
        // 提取时间信息
        val timeInfo = extractTimeInfoFromMetadata(entity.customerNote)
        
        // 从订单的customerNote中提取外卖费和小费信息
        var deliveryFee: String? = null
        var tipAmount: String? = null
        
        // 从元数据提取外卖费 - 只有是外卖订单时才提取
        if (isDelivery) {
            try {
                // 外卖费可能出现在元数据中
                val deliveryFeePattern = "(?:外卖费|配送费|运费|Shipping fee|Delivery fee)[：:]*\\s*([¥￥$]?\\s*\\d+(\\.\\d+)?)".toRegex(RegexOption.IGNORE_CASE)
                val deliveryFeeMatch = deliveryFeePattern.find(entity.customerNote)
                
                if (deliveryFeeMatch != null && deliveryFeeMatch.groupValues.size > 1) {
                    deliveryFee = deliveryFeeMatch.groupValues[1].replace("[¥￥$\\s]".toRegex(), "")
                }
                
                // 如果没找到，查找Delivery Fee:格式
                if (deliveryFee == null || deliveryFee.isEmpty()) {
                    val directFeePattern = "Delivery Fee[:：]\\s*\\$?([0-9.]+)".toRegex(RegexOption.IGNORE_CASE) 
                    val directMatch = directFeePattern.find(entity.customerNote)
                    if (directMatch != null && directMatch.groupValues.size > 1) {
                        deliveryFee = directMatch.groupValues[1]
                    }
                }
            } catch (e: Exception) {
                // 忽略错误，不设置默认值
            }
        }
        
        // 提取小费信息
        tipAmount = extractTipAmount(entity.customerNote)
        
        // 如果是自取订单，确保deliveryFee为null
        if (!isDelivery) {
            deliveryFee = null
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
     * 提取小费金额 - 先检查fee_lines，然后检查备注
     */
    private fun extractTipAmount(note: String): String? {
        try {
            // 先从备注中查找"API中提取的小费"
            val apiTipPattern = "API中提取的小费:\\s*\\$?([0-9.]+)".toRegex()
            val apiTipMatch = apiTipPattern.find(note)
            if (apiTipMatch != null && apiTipMatch.groupValues.size > 1) {
                val tipAmount = apiTipMatch.groupValues[1].replace("[¥￥$\\s]".toRegex(), "")
                if (tipAmount.isNotEmpty() && tipAmount != "0" && tipAmount != "0.00") {
                    return tipAmount
                }
            }
            
            // 小费可能以多种形式存在于备注中
            val tipPatterns = listOf(
                "(?:小费|感谢费|Tip|gratuity|Show Your Appreciation)[：:]*\\s*\\$?([0-9.]+)".toRegex(RegexOption.IGNORE_CASE),
                "Show Your Appreciation[:：]?\\s*\\$?([0-9.]+)".toRegex(RegexOption.IGNORE_CASE),
                "Tip[:：]?\\s*\\$?([0-9.]+)".toRegex(RegexOption.IGNORE_CASE)
            )
            
            for (pattern in tipPatterns) {
                val tipMatch = pattern.find(note)
                if (tipMatch != null && tipMatch.groupValues.size > 1) {
                    val tipAmount = tipMatch.groupValues[1].replace("[¥￥$\\s]".toRegex(), "")
                    if (tipAmount.isNotEmpty() && tipAmount != "0" && tipAmount != "0.00") {
                        return tipAmount
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略错误
        }
        return null
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
        val woofoodInfo = parseWooFoodInfo(entity)
        val isDelivery = woofoodInfo?.isDelivery ?: false
        
        // 先检查是否有备注中的API提取的配送费
        var deliveryFeeFromApi: String? = null
        var tipFromApi: String? = null
        
        if (entity.customerNote.isNotEmpty()) {
            val deliveryFeePattern = "API中提取的配送费:\\s*\\$?([0-9.]+)".toRegex()
            val deliveryFeeMatch = deliveryFeePattern.find(entity.customerNote)
            if (deliveryFeeMatch != null && deliveryFeeMatch.groupValues.size > 1) {
                deliveryFeeFromApi = deliveryFeeMatch.groupValues[1].replace("[¥￥$\\s]".toRegex(), "")
            }
            
            val tipPattern = "API中提取的小费:\\s*\\$?([0-9.]+)".toRegex()
            val tipMatch = tipPattern.find(entity.customerNote)
            if (tipMatch != null && tipMatch.groupValues.size > 1) {
                tipFromApi = tipMatch.groupValues[1].replace("[¥￥$\\s]".toRegex(), "")
            }
        }
        
        // 添加配送费（仅配送订单）
        if (isDelivery) {
            val fee = deliveryFeeFromApi ?: woofoodInfo?.deliveryFee ?: "0.00"
            // 无论金额是多少，都添加配送费（对于配送订单）
            feeLines.add(com.example.wooauto.domain.models.FeeLine(
                id = 1,
                name = "配送费",
                total = fee,
                totalTax = "0.00"
            ))
        }
        
        // 添加小费（无论订单类型）
        val tip = tipFromApi ?: woofoodInfo?.tip ?: "0.00"
        // 无论金额是多少，都添加小费
        feeLines.add(com.example.wooauto.domain.models.FeeLine(
            id = 2,
            name = "小费",
            total = tip,
            totalTax = "0.00"
        ))
        
        // 额外检查：从订单备注中查找其他可能的费用项
        if (entity.customerNote.isNotEmpty()) {
            // 查找其他费用（除配送费和小费外）
            val otherFeeRegex = "(?!外卖费|配送费|小费|运费|Shipping|Delivery|Tip|gratuity)([\\w\\s]+)[:：]\\s*([¥￥$]?\\s*[0-9.]+)".toRegex(RegexOption.IGNORE_CASE)
            val matches = otherFeeRegex.findAll(entity.customerNote)
            
            matches.forEach { matchResult ->
                if (matchResult.groupValues.size > 2) {
                    val feeName = matchResult.groupValues[1].trim()
                    val feeAmount = matchResult.groupValues[2].replace("[¥￥$\\s]".toRegex(), "")
                    
                    if (feeName.isNotEmpty() && feeAmount.isNotEmpty() && feeAmount != "0" && feeAmount != "0.00") {
                        // 确保不重复添加配送费或小费
                        if (feeName != "配送费" && feeName != "外卖费" && feeName != "小费") {
                            feeLines.add(com.example.wooauto.domain.models.FeeLine(
                                id = feeLines.size + 1L,
                                name = feeName,
                                total = feeAmount,
                                totalTax = "0.00"
                            ))
                        }
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
            
            // 解析费用行项目
            val feeLines = parseFeeLines(entity)
            
            // 获取配送费和小费
            var deliveryFee = 0.0
            var tip = 0.0
            
            // 从费用行中提取配送费和小费
            feeLines.forEach { feeLine ->
                when (feeLine.name) {
                    "配送费" -> deliveryFee = feeLine.total.toDoubleOrNull() ?: 0.0
                    "小费" -> tip = feeLine.total.toDoubleOrNull() ?: 0.0
                }
            }
            
            // 如果费用行中没有配送费或小费，尝试从WooFood信息中获取
            if (deliveryFee == 0.0 || tip == 0.0) {
                val woofoodInfo = parseWooFoodInfo(entity)
                
                // 获取配送费
                if (deliveryFee == 0.0 && woofoodInfo?.isDelivery == true) {
                    woofoodInfo.deliveryFee?.let {
                        if (it.isNotEmpty()) {
                            deliveryFee = it.toDoubleOrNull() ?: 0.0
                        }
                    }
                }
                
                // 获取小费
                if (tip == 0.0) {
                    woofoodInfo?.tip?.let {
                        if (it.isNotEmpty()) {
                            tip = it.toDoubleOrNull() ?: 0.0
                        }
                    }
                }
            }
            
            // 计算小计
            val subtotal = total - tax - deliveryFee - tip
            
            // 确保小计不小于0
            return String.format("%.2f", if (subtotal < 0) 0.0 else subtotal)
        } catch (e: Exception) {
            // 如果计算失败，直接使用lineItems的价格*数量总和作为小计
            return try {
                val itemsTotal = entity.lineItems.sumOf { lineItem ->
                    (lineItem.price.toDoubleOrNull() ?: 0.0) * lineItem.quantity
                }
                String.format("%.2f", itemsTotal)
            } catch (e2: Exception) {
                entity.total // 如果还是失败，直接返回总价
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