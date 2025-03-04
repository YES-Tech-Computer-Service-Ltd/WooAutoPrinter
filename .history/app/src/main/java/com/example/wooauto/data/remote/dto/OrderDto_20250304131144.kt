package com.example.wooauto.data.remote.dto

import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.OrderItem
import com.example.wooauto.domain.models.WooFoodInfo
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val metaData: List<MetaDataDto>? = null
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
        woofoodInfo = woofoodInfo // 添加WooFood信息
    )
}

// 处理WooFood相关信息
private fun OrderDto.processWooFoodInfo(): WooFoodInfo? {
    // 如果没有元数据，直接返回null
    val metaData = this.metaData
    if (metaData == null) {
        android.util.Log.d("WooFood", "订单 #$number 没有元数据")
        return null
    }
    
    android.util.Log.d("WooFood", "订单 #$number 元数据数量: ${metaData.size}")
    
    // 元数据键名可能的变体
    val orderMethodKeys = listOf("exwfood_order_method", "_order_type", "order_type", "_woofood_order_type")
    val deliveryTimeKeys = listOf("exwfood_delivery_time", "delivery_time", "_delivery_time", "_woofood_delivery_time")
    val deliveryAddressKeys = listOf("exwfood_delivery_address", "delivery_address", "_delivery_address", "_woofood_delivery_address")
    val deliveryFeeKeys = listOf("exwfood_delivery_fee", "delivery_fee", "_delivery_fee", "_woofood_delivery_fee")
    val tipKeys = listOf("exwfood_tip", "tip", "_tip", "_woofood_tip")
    
    // 从元数据中提取WooFood信息
    val orderMethod = findMetadataValue(metaData, orderMethodKeys)
    val deliveryTime = findMetadataValue(metaData, deliveryTimeKeys)
    val deliveryAddress = findMetadataValue(metaData, deliveryAddressKeys)
    val deliveryFee = findMetadataValue(metaData, deliveryFeeKeys)
    val tip = findMetadataValue(metaData, tipKeys)
    
    // 记录找到的值
    android.util.Log.d("WooFood", "订单 #$number WooFood信息: 订单方式=$orderMethod, 配送时间=$deliveryTime")
    
    // 判断是否是外卖订单 - 更多可能的值
    val isDelivery = when (orderMethod?.lowercase()) {
        "delivery" -> true
        "配送" -> true
        "外卖" -> true
        "delivery order" -> true
        else -> false
    }
    
    // 如果没有订单方式，仍然返回基本信息 - 保证所有订单都有WooFood信息
    return WooFoodInfo(
        orderMethod = orderMethod ?: if (isDelivery) "Delivery" else "Pickup",
        deliveryTime = deliveryTime,
        deliveryAddress = deliveryAddress,
        deliveryFee = deliveryFee,
        tip = tip,
        isDelivery = isDelivery
    )
}

// 辅助函数：尝试多个可能的键名查找元数据值
private fun findMetadataValue(metaData: List<MetaDataDto>, possibleKeys: List<String>): String? {
    // 遍历所有可能的键
    for (key in possibleKeys) {
        // 查找匹配的元数据
        val metadata = metaData.find { it.key == key }
        if (metadata != null) {
            android.util.Log.d("WooFood", "找到键 '$key' 对应的值: ${metadata.value}")
            return metadata.value?.toString()
        }
    }
    
    // 尝试模糊匹配
    for (key in possibleKeys) {
        val partialMatch = metaData.find { it.key?.contains(key, ignoreCase = true) == true }
        if (partialMatch != null) {
            android.util.Log.d("WooFood", "模糊匹配到键 '${partialMatch.key}' 对应的值: ${partialMatch.value}")
            return partialMatch.value?.toString()
        }
    }
    
    return null
} 