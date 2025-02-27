package com.wooauto.data.remote.models

import com.google.gson.annotations.SerializedName

/**
 * 订单响应模型
 * 用于解析WooCommerce API返回的订单数据
 */
data class OrderResponse(
    @SerializedName("id")
    val id: Long,
    
    @SerializedName("number")
    val number: String,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("date_created")
    val dateCreated: String,
    
    @SerializedName("total")
    val total: String,
    
    @SerializedName("customer_id")
    val customerId: Long,
    
    @SerializedName("billing")
    val billing: BillingResponse,
    
    @SerializedName("shipping")
    val shipping: ShippingResponse,
    
    @SerializedName("payment_method")
    val paymentMethod: String,
    
    @SerializedName("payment_method_title")
    val paymentMethodTitle: String,
    
    @SerializedName("line_items")
    val lineItems: List<OrderItemResponse>,
    
    @SerializedName("meta_data")
    val metaData: List<MetaDataResponse>,
    
    @SerializedName("delivery_date")
    val deliveryDate: String? = null,
    
    @SerializedName("delivery_time")
    val deliveryTime: String? = null,
    
    @SerializedName("order_method")
    val orderMethod: String? = null,
    
    @SerializedName("tip")
    val tip: String? = null,
    
    @SerializedName("delivery_fee")
    val deliveryFee: String? = null
)

/**
 * 元数据响应模型
 */
data class MetaDataResponse(
    @SerializedName("key")
    val key: String,
    
    @SerializedName("value")
    val value: String
)

/**
 * 订单项响应模型
 */
data class OrderItemResponse(
    @SerializedName("id")
    val id: Long,
    
    @SerializedName("product_id")
    val productId: Long,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("quantity")
    val quantity: Int,
    
    @SerializedName("price")
    val price: String,
    
    @SerializedName("total")
    val total: String
)

/**
 * 账单信息响应模型
 */
data class BillingResponse(
    @SerializedName("first_name")
    val firstName: String,
    
    @SerializedName("last_name")
    val lastName: String,
    
    @SerializedName("email")
    val email: String?,
    
    @SerializedName("address_1")
    val address1: String,
    
    @SerializedName("address_2")
    val address2: String?,
    
    @SerializedName("city")
    val city: String,
    
    @SerializedName("state")
    val state: String,
    
    @SerializedName("postcode")
    val postcode: String,
    
    @SerializedName("country")
    val country: String
)

/**
 * 配送信息响应模型
 */
data class ShippingResponse(
    @SerializedName("first_name")
    val firstName: String,
    
    @SerializedName("last_name")
    val lastName: String,
    
    @SerializedName("address_1")
    val address1: String,
    
    @SerializedName("address_2")
    val address2: String?,
    
    @SerializedName("city")
    val city: String,
    
    @SerializedName("state")
    val state: String,
    
    @SerializedName("postcode")
    val postcode: String,
    
    @SerializedName("country")
    val country: String
) 