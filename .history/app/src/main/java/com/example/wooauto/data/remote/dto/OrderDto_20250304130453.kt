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
    val customerNote: String?
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
    // 这里需要从元数据中提取WooFood信息
    // 如果没有元数据或不是WooFood订单，返回null
    
    // TODO: 实现从元数据提取WooFood信息的逻辑
    // 这通常需要通过API响应中的meta_data字段
    
    // 示例实现（伪代码）
    /*
    val metaData = this.metaData ?: return null
    
    val orderMethod = metaData.find { it.key == "exwfood_order_method" }?.value?.toString()
    val deliveryTime = metaData.find { it.key == "exwfood_delivery_time" }?.value?.toString()
    val deliveryAddress = metaData.find { it.key == "exwfood_delivery_address" }?.value?.toString()
    val deliveryFee = metaData.find { it.key == "exwfood_delivery_fee" }?.value?.toString()
    val tip = metaData.find { it.key == "exwfood_tip" }?.value?.toString()
    
    val isDelivery = orderMethod?.lowercase() == "delivery"
    
    return if (orderMethod != null) {
        WooFoodInfo(
            orderMethod = orderMethod,
            deliveryTime = deliveryTime,
            deliveryAddress = deliveryAddress,
            deliveryFee = deliveryFee,
            tip = tip,
            isDelivery = isDelivery
        )
    } else {
        null
    }
    */
    
    // 临时返回null，后续需要实现实际逻辑
    return null
} 