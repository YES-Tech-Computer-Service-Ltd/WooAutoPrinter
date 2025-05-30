package com.example.wooauto.data.mappers

import com.google.gson.Gson
import com.example.wooauto.data.local.entities.OrderEntity
import com.example.wooauto.data.local.entities.OrderLineItemEntity
import com.example.wooauto.data.local.entities.WooFoodInfoEntity
import com.example.wooauto.data.local.entities.FeeLineEntity
import com.example.wooauto.data.local.entities.TaxLineEntity
import com.example.wooauto.data.remote.models.BillingResponse
import com.example.wooauto.data.remote.models.OrderResponse
import com.example.wooauto.data.remote.models.ShippingResponse
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.OrderItem
import java.text.SimpleDateFormat
import java.util.*
import com.google.gson.reflect.TypeToken
import android.util.Log

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
        
        // 解析额外费用项 - 注意：先解析feeLines再解析WooFoodInfo
        val feeLines = parseFeeLines(entity)
        
        // 记录解析到的费用行
        // println("【数据流】订单#${entity.number} 解析到的费用行:")
        feeLines.forEach { feeLine ->
            // println("  - ${feeLine.name}: ${feeLine.total}")
        }
        
        // 从费用行中查找配送费和小费
        val deliveryFeeFromFeeLine = feeLines.find { 
            it.name.contains("配送费", ignoreCase = true) || 
            it.name.contains("外卖费", ignoreCase = true) ||
            it.name.contains("delivery", ignoreCase = true) ||
            it.name.contains("shipping", ignoreCase = true)
        }?.total
        
        val tipFromFeeLine = feeLines.find { 
            it.name.contains("小费", ignoreCase = true) || 
            it.name.contains("tip", ignoreCase = true) ||
            it.name.contains("gratuity", ignoreCase = true) ||
            it.name.contains("appreciation", ignoreCase = true)
        }?.total
        
        // println("【数据流】订单#${entity.number} 从费用行提取 - 配送费: $deliveryFeeFromFeeLine, 小费: $tipFromFeeLine")
        
        // 解析WooFood信息，包括外卖费和小费
        val woofoodInfo = parseWooFoodInfo(entity)
        
        // 打印WooFoodInfo内容进行调试
        // println("【数据转换】订单#${entity.number} 从parseWooFoodInfo得到 WooFoodInfo: $woofoodInfo")
        
        // 创建一个更新的WooFoodInfo，确保使用费用行中找到的值(如果有)
        val finalWooFoodInfo = if (woofoodInfo != null) {
            // 如果外卖费为空或0，但费用行中找到了，则使用费用行中的值
            val finalDeliveryFee = if ((woofoodInfo.deliveryFee == null || woofoodInfo.deliveryFee == "0.00") && 
                                     deliveryFeeFromFeeLine != null && deliveryFeeFromFeeLine != "0.00") {
                deliveryFeeFromFeeLine
            } else {
                woofoodInfo.deliveryFee
            }
            
            // 如果小费为空或0，但费用行中找到了，则使用费用行中的值
            val finalTip = if ((woofoodInfo.tip == null || woofoodInfo.tip == "0.00") && 
                              tipFromFeeLine != null && tipFromFeeLine != "0.00") {
                tipFromFeeLine
            } else {
                woofoodInfo.tip
            }
            
            // 创建更新后的WooFoodInfo
            if (finalDeliveryFee != woofoodInfo.deliveryFee || finalTip != woofoodInfo.tip) {
                // println("【数据修正】订单#${entity.number} - 更新WooFoodInfo - 配送费: ${woofoodInfo.deliveryFee} -> $finalDeliveryFee, 小费: ${woofoodInfo.tip} -> $finalTip")
                
                com.example.wooauto.domain.models.WooFoodInfo(
                    orderMethod = woofoodInfo.orderMethod,
                    deliveryTime = woofoodInfo.deliveryTime,
                    deliveryAddress = woofoodInfo.deliveryAddress,
                    deliveryFee = finalDeliveryFee,
                    tip = finalTip,
                    isDelivery = woofoodInfo.isDelivery
                )
            } else {
                // 无需更新
                woofoodInfo
            }
        } else {
            // 如果没有WooFoodInfo但费用行中有配送费，则创建一个
            if (deliveryFeeFromFeeLine != null && deliveryFeeFromFeeLine != "0.00") {
                // println("【数据修正】订单#${entity.number} - 从费用行创建WooFoodInfo - 配送费: $deliveryFeeFromFeeLine")
                
                com.example.wooauto.domain.models.WooFoodInfo(
                    orderMethod = "delivery",
                    deliveryTime = null,
                    deliveryAddress = null,
                    deliveryFee = deliveryFeeFromFeeLine,
                    tip = tipFromFeeLine,
                    isDelivery = true
                )
            } else {
                null
            }
        }
        
        // 最终的WooFoodInfo日志
        // println("【数据流】订单#${entity.number} 最终的WooFoodInfo: $finalWooFoodInfo")
        
        // 继续使用更新后的woofoodInfo创建Order对象
        return createOrderWithUpdatedWooFoodInfo(entity, orderItems, finalWooFoodInfo, feeLines)
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
                           entity.customerNote.contains("送餐", ignoreCase = true) ||
                           entity.customerNote.contains("送货", ignoreCase = true) ||
                           entity.customerNote.lowercase().contains("shipping") ||
                           entity.customerNote.lowercase().contains("transport") ||
                           entity.paymentMethodTitle.contains("外卖", ignoreCase = true) ||
                           entity.paymentMethodTitle.contains("配送", ignoreCase = true) ||
                           entity.paymentMethodTitle.contains("到付", ignoreCase = true)
            
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
                // 先尝试匹配"API最终提取的配送费"或"API中提取的配送费"等新旧格式
                val deliveryFeeApiPatterns = listOf(
                    "API中提取的配送费:\\s*\\$?([0-9.]+)".toRegex(),
                    "API最终提取的配送费:\\s*\\$?([0-9.]+)".toRegex(),
                    "扩展方法提取的配送费:\\s*\\$?([0-9.]+)".toRegex(),
                    "从feeLines直接提取的配送费:\\s*\\$?([0-9.]+)".toRegex()
                )
                
                for (pattern in deliveryFeeApiPatterns) {
                    val deliveryApiMatch = pattern.find(entity.customerNote)
                    if (deliveryApiMatch != null && deliveryApiMatch.groupValues.size > 1) {
                        val extracted = deliveryApiMatch.groupValues[1].replace("[¥￥$\\s]".toRegex(), "")
                        if (extracted.isNotEmpty() && extracted != "0" && extracted != "0.00") {
                            deliveryFee = extracted
                            // println("【来源: API记录】配送费: $deliveryFee")
                            break
                        }
                    }
                }
                
                // 1. 首先从fee_lines中提取配送费和小费
                if (deliveryFee == null || deliveryFee == "0.00") {
                    val feeLinePattern = "从feeLines中提取到配送费:\\s*([0-9.]+)".toRegex()
                    val feeLineMatch = feeLinePattern.find(entity.customerNote)
                    if (feeLineMatch != null && feeLineMatch.groupValues.size > 1) {
                        deliveryFee = feeLineMatch.groupValues[1]
                        // println("【来源: fee_lines】配送费: $deliveryFee")
                    }
                }
                
                // 如果没找到，查找传统配送费模式
                if (deliveryFee == null || deliveryFee.isEmpty()) {
                    // 外卖费可能出现在元数据中
                    val deliveryFeePattern = "(?:外卖费|配送费|运费|送餐费|Shipping fee|shipping fee|SHIPPING FEE|Delivery fee|delivery charge)[：:]*\\s*([¥￥$]?\\s*\\d+(\\.\\d+)?)".toRegex(RegexOption.IGNORE_CASE)
                    val deliveryFeeMatch = deliveryFeePattern.find(entity.customerNote)
                    
                    if (deliveryFeeMatch != null && deliveryFeeMatch.groupValues.size > 1) {
                        deliveryFee = deliveryFeeMatch.groupValues[1].replace("[¥￥$\\s]".toRegex(), "")
                        // println("【来源: 常规备注】配送费: $deliveryFee")
                    }
                    
                    // 如果没找到，查找Delivery Fee:格式
                    if (deliveryFee == null || deliveryFee.isEmpty()) {
                        val directFeePattern = "(Delivery Fee|Shipping|Transport fee|送货费)[:：]\\s*\\$?([0-9.]+)".toRegex(RegexOption.IGNORE_CASE) 
                        val directMatch = directFeePattern.find(entity.customerNote)
                        if (directMatch != null && directMatch.groupValues.size > 2) {
                            deliveryFee = directMatch.groupValues[2]
                            // println("【来源: 直接格式】配送费: $deliveryFee")
                        }
                    }
                }
            } catch (e: Exception) {
                // 忽略错误，不设置默认值
                // println("提取配送费出错: ${e.message}")
            }
        }
        
        // 提取小费信息 - 优先从fee_lines提取
        if (tipAmount == null || tipAmount == "0.00") {
            val tipFeeLinePattern = "从feeLines中提取到小费:\\s*([0-9.]+)".toRegex()
            val tipFeeLineMatch = tipFeeLinePattern.find(entity.customerNote)
            if (tipFeeLineMatch != null && tipFeeLineMatch.groupValues.size > 1) {
                tipAmount = tipFeeLineMatch.groupValues[1]
                // println("【来源: fee_lines】小费: $tipAmount")
            } else {
                tipAmount = extractTipAmount(entity.customerNote)
                if (tipAmount != null) {
                    // println("【来源: 提取函数】小费: $tipAmount")
                }
            }
        }
        
        // 如果是自取订单，确保deliveryFee为null
        if (!isDelivery) {
            deliveryFee = null
        } else if (deliveryFee == null || deliveryFee == "0.00") {
            // println("【调试】发现外卖订单但配送费为0: 订单号#${entity.number}")
        }
        
        // println("【WOOFOOD创建】订单#${entity.number} - 配送费: $deliveryFee, 小费: $tipAmount, 是否外卖: $isDelivery")
        
        // 获取配送地址 - WooFood插件可能将配送地址存储在billing中而不是shipping中
        val deliveryAddress = if (isDelivery) {
            // 优先使用shipping地址，如果为空或与billing地址相同，则使用billing地址
            if (entity.shippingAddress.isNotEmpty() && 
                !entity.shippingAddress.equals(entity.billingAddress, ignoreCase = true)) {
                println("【地址来源】订单#${entity.number} 使用shipping地址: ${entity.shippingAddress}")
                entity.shippingAddress
            } else {
                // WooFood插件通常将配送地址存储在billing中
                println("【地址来源】订单#${entity.number} 使用billing地址: ${entity.billingAddress}")
                entity.billingAddress
            }
        } else {
            null
        }
        
        return com.example.wooauto.domain.models.WooFoodInfo(
            orderMethod = orderMethod,
            deliveryTime = timeInfo,
            deliveryAddress = deliveryAddress,
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
            // 先从备注中查找"API中提取的小费"或"API最终提取的小费"
            val apiTipPatterns = listOf(
                "API中提取的小费:\\s*\\$?([0-9.]+)".toRegex(),
                "API最终提取的小费:\\s*\\$?([0-9.]+)".toRegex(),
                "扩展方法提取的小费:\\s*\\$?([0-9.]+)".toRegex(),
                "从feeLines直接提取的小费:\\s*\\$?([0-9.]+)".toRegex()
            )
            
            for (pattern in apiTipPatterns) {
                val apiTipMatch = pattern.find(note)
                if (apiTipMatch != null && apiTipMatch.groupValues.size > 1) {
                    val tipAmount = apiTipMatch.groupValues[1].replace("[¥￥$\\s]".toRegex(), "")
                    if (tipAmount.isNotEmpty() && tipAmount != "0" && tipAmount != "0.00") {
                        return tipAmount
                    }
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
        // 如果实体已经有feeLines字段，直接使用
        if (entity.feeLines.isNotEmpty()) {
            // println("【费用行】订单#${entity.number} - 实体中直接存在${entity.feeLines.size}个费用行")
            return entity.feeLines.map { feeLineEntity ->
                com.example.wooauto.domain.models.FeeLine(
                    id = feeLineEntity.id,
                    name = feeLineEntity.name,
                    total = feeLineEntity.total,
                    totalTax = feeLineEntity.totalTax
                )
            }
        }
        
        // 如果实体中没有费用行，尝试从其他地方提取
        val result = mutableListOf<com.example.wooauto.domain.models.FeeLine>()
        
        // 从订单备注中提取费用行信息
        try {
            // 尝试从备注中识别配送费和小费
            val deliveryFee = extractDeliveryFeeFromNote(entity.customerNote)
            if (deliveryFee != null) {
                result.add(
                    com.example.wooauto.domain.models.FeeLine(
                        id = -1,
                        name = "配送费",
                        total = deliveryFee,
                        totalTax = "0.00"
                    )
                )
                // println("【费用行解析】订单#${entity.number} - 从备注提取配送费: $deliveryFee")
            }
            
            val tip = extractTipAmount(entity.customerNote)
            if (tip != null) {
                result.add(
                    com.example.wooauto.domain.models.FeeLine(
                        id = -2,
                        name = "小费",
                        total = tip,
                        totalTax = "0.00"
                    )
                )
                // println("【费用行解析】订单#${entity.number} - 从备注提取小费: $tip")
            }
        } catch (e: Exception) {
            // println("【费用行解析】订单#${entity.number} - 解析费用行出错: ${e.message}")
        }
        
        return result
    }
    
    /**
     * 从备注中提取配送费
     */
    private fun extractDeliveryFeeFromNote(note: String): String? {
        try {
            // 尝试匹配"API最终提取的配送费"
            val pattern1 = "API最终提取的配送费:\\s*\\$?([0-9.]+)".toRegex()
            val match1 = pattern1.find(note)
            if (match1 != null && match1.groupValues.size > 1) {
                return match1.groupValues[1]
            }
            
            // 尝试匹配"扩展方法提取的配送费"
            val pattern2 = "扩展方法提取的配送费:\\s*\\$?([0-9.]+)".toRegex()
            val match2 = pattern2.find(note)
            if (match2 != null && match2.groupValues.size > 1) {
                return match2.groupValues[1]
            }
            
            // 尝试匹配"从feeLines直接提取的配送费"
            val pattern3 = "从feeLines直接提取的配送费:\\s*\\$?([0-9.]+)".toRegex()
            val match3 = pattern3.find(note)
            if (match3 != null && match3.groupValues.size > 1) {
                return match3.groupValues[1]
            }
            
            // 尝试匹配常规格式
            val pattern4 = "(外卖费|配送费|运费|送餐费|Shipping fee|delivery fee|delivery charge)[：:]*\\s*([¥￥$]?\\s*\\d+(\\.\\d+)?)".toRegex(RegexOption.IGNORE_CASE)
            val match4 = pattern4.find(note)
            if (match4 != null && match4.groupValues.size > 2) {
                return match4.groupValues[2].replace("[¥￥$\\s]".toRegex(), "")
            }
        } catch (e: Exception) {
            // 忽略异常
        }
        
        return null
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
    fun mapDomainToEntity(order: Order): OrderEntity {
        // 将日期转换为时间戳
        val dateCreated = order.dateCreated.time
        
        // 将订单号字符串转换为整数
        val number = order.number.toIntOrNull() ?: 0
        
        // 将领域模型中的OrderItem转换为OrderLineItemEntity
        val lineItems = order.items.map { item ->
            OrderLineItemEntity(
                productId = item.productId,
                name = item.name,
                quantity = item.quantity,
                price = item.price,
                total = item.total,
                sku = "" // 如果OrderItem中有sku字段，应该使用item.sku
            )
        }
        
        // 将WooFoodInfo转换为WooFoodInfoEntity
        val woofoodInfoEntity = order.woofoodInfo?.let {
            WooFoodInfoEntity(
                orderMethod = it.orderMethod,
                deliveryTime = it.deliveryTime,
                deliveryAddress = it.deliveryAddress,
                deliveryFee = it.deliveryFee,
                tip = it.tip,
                isDelivery = it.isDelivery
            )
        }
        
        // 将FeeLine转换为FeeLineEntity
        val feeLines = order.feeLines.map { feeLine ->
            FeeLineEntity(
                id = feeLine.id,
                name = feeLine.name,
                total = feeLine.total,
                totalTax = feeLine.totalTax
            )
        }
        
        // 将TaxLine转换为TaxLineEntity
        val taxLines = order.taxLines.map { taxLine ->
            TaxLineEntity(
                id = taxLine.id,
                label = taxLine.label,
                ratePercent = taxLine.ratePercent,
                taxTotal = taxLine.taxTotal
            )
        }
        
        return OrderEntity(
            id = order.id,
            number = number,
            status = order.status,
            dateCreated = dateCreated,
            dateModified = System.currentTimeMillis(),
            customerName = order.customerName,
            customerNote = order.notes,
            contactInfo = order.contactInfo,
            total = order.total,
            totalTax = order.totalTax,
            lineItems = lineItems,
            shippingAddress = "", // 从woofoodInfo中获取配送地址
            billingAddress = order.billingInfo,
            paymentMethod = order.paymentMethod,
            paymentMethodTitle = order.paymentMethod, // 假设paymentMethod和paymentMethodTitle相同
            isPrinted = order.isPrinted,
            notificationShown = order.notificationShown,
            lastUpdated = System.currentTimeMillis(),
            woofoodInfo = woofoodInfoEntity,
            feeLines = feeLines,
            taxLines = taxLines,
            subtotal = order.subtotal,
            discountTotal = order.discountTotal,
            isRead = order.isRead
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
                // 检查是否有任何实际的地址信息
                val hasName = !address.firstName.isNullOrBlank() || !address.lastName.isNullOrBlank()
                val hasPhone = !address.phone.isNullOrBlank()
                val hasAddress = !address.address1.isNullOrBlank() || !address.address2.isNullOrBlank()
                val hasLocation = !address.city.isNullOrBlank() || !address.state.isNullOrBlank() || !address.postcode.isNullOrBlank()
                val hasCountry = !address.country.isNullOrBlank()
                
                if (!hasName && !hasPhone && !hasAddress && !hasLocation && !hasCountry) {
                    return ""  // 如果所有字段都为空，返回空字符串
                }
                
                buildString {
                    if (hasName) {
                        val firstName = address.firstName?.trim() ?: ""
                        val lastName = address.lastName?.trim() ?: ""
                        val fullName = "$firstName $lastName".trim()
                        if (fullName.isNotEmpty()) {
                            append("$fullName\n")
                        }
                    }
                    
                    if (hasPhone) {
                        append("${address.phone}\n")
                    }
                    
                    if (!address.address1.isNullOrBlank()) {
                        append("${address.address1}\n")
                    }
                    
                    if (!address.address2.isNullOrBlank()) {
                        append("${address.address2}\n")
                    }
                    
                    if (hasLocation) {
                        val locationParts = listOfNotNull(
                            address.city?.takeIf { it.isNotBlank() },
                            address.state?.takeIf { it.isNotBlank() },
                            address.postcode?.takeIf { it.isNotBlank() }
                        )
                        if (locationParts.isNotEmpty()) {
                            append("${locationParts.joinToString(", ")}\n")
                        }
                    }
                    
                    if (hasCountry) {
                        append(address.country)
                    }
                }.trim()  // 移除最后的换行符
            }
            is ShippingResponse -> {
                // 检查是否有任何实际的地址信息
                val hasName = !address.firstName.isNullOrBlank() || !address.lastName.isNullOrBlank()
                val hasAddress = !address.address1.isNullOrBlank() || !address.address2.isNullOrBlank()
                val hasLocation = !address.city.isNullOrBlank() || !address.state.isNullOrBlank() || !address.postcode.isNullOrBlank()
                val hasCountry = !address.country.isNullOrBlank()
                
                if (!hasName && !hasAddress && !hasLocation && !hasCountry) {
                    return ""  // 如果所有字段都为空，返回空字符串
                }
                
                buildString {
                    if (hasName) {
                        val firstName = address.firstName?.trim() ?: ""
                        val lastName = address.lastName?.trim() ?: ""
                        val fullName = "$firstName $lastName".trim()
                        if (fullName.isNotEmpty()) {
                            append("$fullName\n")
                        }
                    }
                    
                    if (!address.address1.isNullOrBlank()) {
                        append("${address.address1}\n")
                    }
                    
                    if (!address.address2.isNullOrBlank()) {
                        append("${address.address2}\n")
                    }
                    
                    if (hasLocation) {
                        val locationParts = listOfNotNull(
                            address.city?.takeIf { it.isNotBlank() },
                            address.state?.takeIf { it.isNotBlank() },
                            address.postcode?.takeIf { it.isNotBlank() }
                        )
                        if (locationParts.isNotEmpty()) {
                            append("${locationParts.joinToString(", ")}\n")
                        }
                    }
                    
                    if (hasCountry) {
                        append(address.country)
                    }
                }.trim()  // 移除最后的换行符
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

    /**
     * 创建带有更新后的WooFoodInfo的Order对象
     */
    private fun createOrderWithUpdatedWooFoodInfo(
        entity: OrderEntity,
        orderItems: List<OrderItem>,
        woofoodInfo: com.example.wooauto.domain.models.WooFoodInfo?,
        feeLines: List<com.example.wooauto.domain.models.FeeLine>
    ): Order {
        // 转换税费行
        val taxLines = entity.taxLines.map { taxLine ->
            com.example.wooauto.domain.models.TaxLine(
                id = taxLine.id,
                label = taxLine.label,
                ratePercent = taxLine.ratePercent,
                taxTotal = taxLine.taxTotal
            )
        }
        
        // 如果原始feeLines为空但有WooFoodInfo，从WooFoodInfo创建费用行
        val finalFeeLines = if (feeLines.isEmpty() && woofoodInfo != null) {
            val result = mutableListOf<com.example.wooauto.domain.models.FeeLine>()
            
            // 添加配送费
            if (woofoodInfo.isDelivery && woofoodInfo.deliveryFee != null && woofoodInfo.deliveryFee != "0.00") {
                result.add(
                    com.example.wooauto.domain.models.FeeLine(
                        id = -1,
                        name = "配送费",
                        total = woofoodInfo.deliveryFee,
                        totalTax = "0.00"
                    )
                )
            }
            
            // 添加小费
            if (woofoodInfo.tip != null && woofoodInfo.tip != "0.00") {
                result.add(
                    com.example.wooauto.domain.models.FeeLine(
                        id = -2,
                        name = "小费",
                        total = woofoodInfo.tip,
                        totalTax = "0.00"
                    )
                )
            }
            
            result
        } else {
            feeLines
        }
        
        // 调试日志：检查地址信息
        println("【Order创建】订单#${entity.number} - billingAddress: '${entity.billingAddress}', deliveryAddress: '${woofoodInfo?.deliveryAddress}'")
        Log.d("ORDER_DEBUG", "=== Order创建调试 ===")
        Log.d("ORDER_DEBUG", "订单#${entity.number}")
        Log.d("ORDER_DEBUG", "billingAddress: '${entity.billingAddress}'")  
        Log.d("ORDER_DEBUG", "deliveryAddress: '${woofoodInfo?.deliveryAddress}'")
        Log.d("ORDER_DEBUG", "woofoodInfo: ${woofoodInfo}")
        
        return Order(
            id = entity.id,
            number = entity.number.toString(),
            status = entity.status,
            dateCreated = Date(entity.dateCreated),
            customerName = entity.customerName,
            contactInfo = entity.contactInfo,
            billingInfo = entity.billingAddress,
            paymentMethod = entity.paymentMethod,
            total = entity.total,
            items = orderItems,
            isPrinted = entity.isPrinted,
            notificationShown = entity.notificationShown,
            notes = entity.customerNote,
            woofoodInfo = woofoodInfo,
            subtotal = entity.subtotal.takeIf { it.isNotEmpty() } ?: 
                       (entity.total.toDoubleOrNull()?.minus(entity.totalTax.toDoubleOrNull() ?: 0.0)?.toString() ?: entity.total),
            totalTax = entity.totalTax,
            discountTotal = entity.discountTotal,
            feeLines = finalFeeLines,
            taxLines = taxLines,
            isRead = entity.isRead
        )
    }
}