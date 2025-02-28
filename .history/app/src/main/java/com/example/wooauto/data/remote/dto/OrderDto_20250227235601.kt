package com.example.wooauto.data.remote.dto

import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.OrderItem
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OrderDto(
    val id: Long,
    val number: String,
    val status: String,
    @SerializedName("date_created")
    val dateCreated: String,
    @SerializedName("date_modified")
    val dateModified: String,
    val total: String,
    val customer: CustomerDto,
    @SerializedName("billing")
    val billingAddress: AddressDto,
    @SerializedName("shipping")
    val shippingAddress: AddressDto?,
    @SerializedName("line_items")
    val lineItems: List<LineItemDto>,
    @SerializedName("payment_method")
    val paymentMethod: String?,
    @SerializedName("payment_method_title")
    val paymentMethodTitle: String?
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

data class LineItemDto(
    val id: Long,
    val name: String,
    @SerializedName("product_id")
    val productId: Long,
    val quantity: Int,
    val subtotal: String,
    val total: String,
    val price: Double
)

// 扩展函数，将OrderDto转换为领域模型Order
fun OrderDto.toOrder(): Order {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    val createdDate = try {
        dateFormat.parse(dateCreated) ?: Date()
    } catch (e: Exception) {
        Date()
    }
    
    val customerName = listOfNotNull(
        customer.firstName,
        customer.lastName
    ).joinToString(" ").takeIf { it.isNotBlank() } ?: "游客"
    
    val billingInfo = listOfNotNull(
        billingAddress.address_1,
        billingAddress.city,
        billingAddress.state,
        billingAddress.postcode
    ).joinToString(", ")
    
    val contactInfo = listOfNotNull(
        customer.phone,
        customer.email
    ).firstOrNull() ?: billingAddress.phone ?: ""
    
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
        notificationShown = false  // 默认为未显示通知
    )
}

// 扩展函数，将LineItemDto转换为领域模型OrderItem
private fun LineItemDto.toOrderItem(): OrderItem {
    return OrderItem(
        id = id,
        productId = productId,
        name = name,
        quantity = quantity,
        price = price.toString(),
        subtotal = subtotal,
        total = total
    )
} 