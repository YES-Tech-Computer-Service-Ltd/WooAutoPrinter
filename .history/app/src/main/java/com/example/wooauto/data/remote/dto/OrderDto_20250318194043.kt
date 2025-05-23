package com.example.wooauto.data.remote.dto

import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.OrderItem
import com.example.wooauto.domain.models.WooFoodInfo
import com.example.wooauto.domain.models.FeeLine
import com.example.wooauto.domain.models.TaxLine
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log
import com.example.wooauto.BuildConfig

data class OrderDto(
    val id: Long,
    @SerializedName("parent_id")
    val parentId: Long = 0,
    val number: String,
    val status: String,
    @SerializedName("date_created")
    val dateCreated: String,
    @SerializedName("date_modified")
    val dateModified: String,
    val total: String,
    val customer: CustomerDto?,
    @SerializedName("billing")
    val billingAddress: AddressDto,
    @SerializedName("shipping")
    val shippingAddress: AddressDto?,
    @SerializedName("line_items")
    val lineItems: List<LineItemDto>,
    @SerializedName("payment_method")
    val paymentMethod: String?,
    @SerializedName("payment_method_title")
    val paymentMethodTitle: String?,
    @SerializedName("customer_note")
    val customerNote: String?,
    @SerializedName("meta_data")
    val metaData: List<MetaDataDto>? = null,
    @SerializedName("fee_lines")
    val feeLines: List<FeeLineDto> = emptyList(),
    @SerializedName("tax_lines")
    val taxLines: List<TaxLineDto> = emptyList(),
    @SerializedName("total_tax")
    val totalTax: String = "0.00",
    @SerializedName("discount_total")
    val discountTotal: String = "0.00",
    @SerializedName("subtotal")
    val subtotal: String = "0.00"
)

data class CustomerDto(
    val id: Long?,
    @SerializedName("first_name")
    val firstName: String?,
    @SerializedName("last_name")
    val lastName: String?,
    val email: String?,
    val phone: String?
)

data class AddressDto(
    @SerializedName("first_name")
    val firstName: String?,
    @SerializedName("last_name")
    val lastName: String?,
    val company: String?,
    val address_1: String?,
    val address_2: String?,
    val city: String?,
    val state: String?,
    val postcode: String?,
    val country: String?,
    val phone: String?
)

// 扩展函数，将OrderDto转换为领域模型Order
fun OrderDto.toOrder(): Order {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    val createdDate = try {
        dateFormat.parse(dateCreated) ?: Date()
    } catch (e: Exception) {
        Date()
    }
    
    // 安全处理customer为null的情况
    val customerName = if (customer != null) {
        listOfNotNull(
            customer.firstName,
            customer.lastName
        ).joinToString(" ").takeIf { it.isNotBlank() } ?: "游客"
    } else {
        // 如果customer为null，尝试从billingAddress获取姓名信息
        listOfNotNull(
            billingAddress.firstName,
            billingAddress.lastName
        ).joinToString(" ").takeIf { it.isNotBlank() } ?: "游客"
    }
    
    val billingInfo = listOfNotNull(
        billingAddress.address_1,
        billingAddress.city,
        billingAddress.state,
        billingAddress.postcode
    ).joinToString(", ")
    
    // 安全处理customer为null的情况
    val contactInfo = if (customer != null) {
        listOfNotNull(
            customer.phone,
            customer.email
        ).firstOrNull() ?: billingAddress.phone ?: ""
    } else {
        billingAddress.phone ?: ""
    }
    
    // 处理WooFood相关信息
    val woofoodInfo = processWooFoodInfo()
    
    // 基于API返回的真实值或计算商品小计（如果API未提供）
    val calculatedSubtotal = if (subtotal == "0.00" || subtotal.isEmpty()) {
        lineItems.sumOf { 
            it.subtotal?.toDoubleOrNull() ?: 0.0 
        }.toString()
    } else {
        subtotal
    }
    
    // 使用API提供的真实税费总额，或者默认值
    val realTotalTax = if (totalTax.isNotEmpty() && totalTax != "0" && totalTax != "0.0" && totalTax != "0.00") {
        totalTax
    } else {
        // 如果没有API提供的税费总额，尝试从税费行计算
        taxLines.sumOf { 
            it.taxTotal.toDoubleOrNull() ?: 0.0 
        }.toString()
    }
    
    // 使用API提供的真实折扣总额
    val realDiscountTotal = discountTotal
    
    // 转换费用行和税费行到领域模型
    val domainFeeLines = feeLines.map { it.toFeeLine() }.toMutableList()
    
    // 处理税费行，确保正确识别GST和PST
    val domainTaxLines = taxLines.map { taxLineDto ->
        // 根据label提取合适的标签名称
        val label = when {
            taxLineDto.label.contains("GST", ignoreCase = true) -> "GST"
            taxLineDto.label.contains("PST", ignoreCase = true) -> "PST"
            else -> taxLineDto.label
        }
        
        // 提取正确的税率百分比
        val ratePercent = taxLineDto.ratePercent
        
        // 创建领域模型税费行
        TaxLine(
            id = taxLineDto.id,
            label = label,
            ratePercent = ratePercent,
            taxTotal = taxLineDto.taxTotal
        ).also {
            Log.d("OrderDto", "创建税费行: ${it.label} (${it.ratePercent}%) = ${it.taxTotal}")
        }
    }
    
    // 如果没有费用行，但有WooFood信息中的小费和配送费，添加这些费用
    if (domainFeeLines.isEmpty()) {
        woofoodInfo?.let {
            // 添加小费（如果有）
            it.tip?.let { tipAmount ->
                if (tipAmount.isNotEmpty() && tipAmount != "0" && tipAmount != "0.0" && tipAmount != "0.00") {
                    domainFeeLines.add(
                        FeeLine(
                            id = -1L, // 使用虚拟ID
                            name = "小费",
                            total = tipAmount,
                            totalTax = "0.00" // 默认不含税
                        )
                    )
                }
            }
            
            // 添加配送费（如果是外卖订单且有配送费）
            if (it.isDelivery) {
                it.deliveryFee?.let { feeAmount ->
                    if (feeAmount.isNotEmpty() && feeAmount != "0" && feeAmount != "0.0" && feeAmount != "0.00") {
                        domainFeeLines.add(
                            FeeLine(
                                id = -2L, // 使用虚拟ID
                                name = "配送费",
                                total = feeAmount,
                                totalTax = "0.00" // 默认不含税
                            )
                        )
                    }
                }
            }
        }
    }
    
    // 如果没有税费行，但有总税费，创建一个默认税费行
    val finalTaxLines = if (domainTaxLines.isEmpty() && realTotalTax != "0.00") {
        listOf(
            TaxLine(
                id = -1L, // 使用虚拟ID
                label = "税费", // 默认税种
                ratePercent = 5.0, // 默认税率
                taxTotal = realTotalTax
            )
        )
    } else {
        domainTaxLines
    }
    
    return Order(
        id = id,
        number = number,
        status = status,
        dateCreated = createdDate,
        total = total,
        customerName = customerName,
        contactInfo = contactInfo,
        billingInfo = billingInfo,
        paymentMethod = paymentMethodTitle ?: paymentMethod ?: "未指定",
        items = lineItems.map { it.toOrderItem() },
        isPrinted = false, // 默认为未打印
        notificationShown = false,  // 默认为未显示通知
        notes = customerNote.orEmpty(), // 使用客户备注作为订单备注
        woofoodInfo = woofoodInfo, // 添加WooFood信息
        subtotal = calculatedSubtotal,
        totalTax = realTotalTax,
        discountTotal = realDiscountTotal,
        feeLines = domainFeeLines,
        taxLines = finalTaxLines
    )
}

// 处理WooFood相关信息
fun OrderDto.processWooFoodInfo(): WooFoodInfo? {
    // 仅保留关键日志
    if (BuildConfig.DEBUG) {
        Log.d("OrderDto", "处理订单#$number 的WooFood信息和费用信息")
    }
    
    if (this.metaData == null) {
        Log.w("OrderDto", "订单#$number 的元数据为null，尝试从fee_lines提取信息")
        
        // 从fee_lines中尝试提取信息
        var tipFromFee: String? = null
        var deliveryFeeFromFee: String? = null
        
        feeLines.forEach { feeLine ->
            when {
                feeLine.name.contains("tip", ignoreCase = true) ||
                feeLine.name.contains("小费", ignoreCase = true) ||
                feeLine.name.contains("appreciation", ignoreCase = true) ||
                feeLine.name.contains("gratuity", ignoreCase = true) -> {
                    tipFromFee = feeLine.total
                }
                feeLine.name.contains("delivery", ignoreCase = true) ||
                feeLine.name.contains("shipping", ignoreCase = true) ||
                feeLine.name.contains("外卖", ignoreCase = true) ||
                feeLine.name.contains("配送", ignoreCase = true) -> {
                    deliveryFeeFromFee = feeLine.total
                }
            }
        }
        
        // 返回从fee_lines提取的信息
        val isDelivery = deliveryFeeFromFee != null
        return WooFoodInfo(
            orderMethod = if (isDelivery) "delivery" else "pickup",
            isDelivery = isDelivery,
            deliveryTime = null,
            deliveryAddress = null,
            deliveryFee = deliveryFeeFromFee,
            tip = tipFromFee
        )
    }
    
    // 元数据键名可能的变体
    val orderMethodKeys = listOf("exwfood_order_method", "_order_type", "order_type", "_woofood_order_type")
    val deliveryTimeKeys = listOf("exwfood_time_deli", "exwfood_delivery_time", "delivery_time", "_delivery_time", "_woofood_delivery_time")
    val deliveryAddressKeys = listOf("exwfood_delivery_address", "delivery_address", "_delivery_address", "_woofood_delivery_address")
    val deliveryFeeKeys = listOf("exwfood_delivery_fee", "delivery_fee", "_delivery_fee", "_woofood_delivery_fee")
    val tipKeys = listOf("exwfood_tip", "tip", "_tip", "_woofood_tip")
    
    // 从元数据中提取WooFood信息
    val orderMethod = findMetadataValue(orderMethodKeys)?.toString()
    Log.d("OrderDto", "订单#$number 订单方式: $orderMethod")
    
    val deliveryTime = findMetadataValue(deliveryTimeKeys)?.toString()
    Log.d("OrderDto", "订单#$number 配送时间: $deliveryTime")
    
    val deliveryAddress = findMetadataValue(deliveryAddressKeys)?.toString()
    
    // 直接从元数据提取配送费和小费，明确记录日志
    var deliveryFee = findMetadataValue(deliveryFeeKeys)?.toString()
    var tip = findMetadataValue(tipKeys)?.toString()
    Log.d("OrderDto", "从元数据中提取: 配送费=$deliveryFee, 小费=$tip")
    
    // 如果在元数据中未找到，尝试从fee_lines提取配送费
    if ((deliveryFee == null || deliveryFee == "0.00") && 
        feeLines.any { it.name.contains("delivery", ignoreCase = true) || it.name.contains("配送", ignoreCase = true) }) {
        // 从fee_lines中提取配送费
        val deliveryFeeLine = feeLines.find { 
            it.name.contains("delivery", ignoreCase = true) || 
            it.name.contains("配送", ignoreCase = true) || 
            it.name.contains("shipping", ignoreCase = true) || 
            it.name.contains("外卖", ignoreCase = true)
        }
        deliveryFee = deliveryFeeLine?.total
        Log.d("OrderDto", "从fee_lines中提取到配送费: $deliveryFee")
    }
    
    // 如果在元数据中未找到，尝试从fee_lines提取小费
    if ((tip == null || tip == "0.00") && 
        feeLines.any { 
            it.name.contains("tip", ignoreCase = true) || 
            it.name.contains("小费", ignoreCase = true) || 
            it.name.contains("gratuity", ignoreCase = true) ||
            it.name.contains("appreciation", ignoreCase = true)
        }) {
        // 从fee_lines中提取小费
        val tipLine = feeLines.find { 
            it.name.contains("tip", ignoreCase = true) || 
            it.name.contains("小费", ignoreCase = true) || 
            it.name.contains("gratuity", ignoreCase = true) ||
            it.name.contains("appreciation", ignoreCase = true)
        }
        tip = tipLine?.total
        Log.d("OrderDto", "从fee_lines中提取到小费: $tip")
    }
    
    // 计算是否为配送订单
    val isDelivery = when (orderMethod?.lowercase()) {
        "delivery" -> true
        else -> deliveryFee != null && deliveryFee != "0.00" // 如果有配送费，假定为外卖订单
    }
    Log.d("OrderDto", "订单#$number 是否为配送订单: $isDelivery, 配送费: $deliveryFee, 小费: $tip")

    // 创建并返回WooFoodInfo
    return WooFoodInfo(
        orderMethod = orderMethod ?: if (isDelivery) "delivery" else "pickup",
        deliveryTime = deliveryTime,
        deliveryAddress = deliveryAddress,
        deliveryFee = deliveryFee,
        tip = tip,
        isDelivery = isDelivery
    )
}

// 辅助函数：尝试多个可能的键名查找元数据值
private fun OrderDto.findMetadataValue(possibleKeys: List<String>): String? {
    // 遍历所有可能的键
    for (key in possibleKeys) {
        // 查找匹配的元数据
        val metadata = this.metaData?.find { it.key == key }
        if (metadata != null) {
            // 注释掉找到键的日志输出
            // Log.d("OrderDto", "找到键 '$key' 对应的值: ${metadata.value}")
            return metadata.value?.toString()
        }
    }
    
    // 尝试模糊匹配
    for (key in possibleKeys) {
        val partialMatch = this.metaData?.find { it.key?.contains(key, ignoreCase = true) == true }
        if (partialMatch != null) {
            // 注释掉模糊匹配的日志输出
            // Log.d("OrderDto", "模糊匹配到键 '${partialMatch.key}' 对应的值: ${partialMatch.value}")
            return partialMatch.value?.toString()
        }
    }
    
    // 没有找到匹配的键
    return null
} 