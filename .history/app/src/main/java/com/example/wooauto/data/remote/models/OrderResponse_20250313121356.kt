package com.example.wooauto.data.remote.models

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

    @SerializedName("currency")
    val currency: String,

    @SerializedName("currency_symbol")
    val currencySymbol: String,

    @SerializedName("date_created")
    val dateCreated: String,

    @SerializedName("date_paid")
    val datePaid: String?,

    @SerializedName("total")
    val total: String,

    @SerializedName("customer_id")
    val customerId: Long,

    @SerializedName("transaction_id")
    val transactionId: String?,

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

    @SerializedName("customer_note")
    val customerNote: String?,

    @SerializedName("meta_data")
    val metaData: List<MetaDataResponse>,

    @SerializedName("fee_lines")
    val feeLines: List<FeeLine>?,

    @SerializedName("tax_lines")
    val taxLines: List<TaxLine>
) {
    // 扩展属性用于获取元数据中的信息
    val orderMethod: String?
        get() = metaData.find { it.key == "exwfood_order_method" }?.value?.toString()

    val deliveryDate: String?
        get() = metaData.find { it.key == "exwfood_date_deli" }?.value?.toString()

    val deliveryTime: String?
        get() = metaData.find { it.key == "exwfood_time_deli" }?.value?.toString()

    val tip: String?
        get() = feeLines?.find { it.name == "Show Your Appreciation" }?.total

    val deliveryFee: String?
        get() = feeLines?.find { it.name == "Shipping fee" }?.total

    // 辅助方法
    fun getMetaValue(key: String): String? {
        return metaData.find { it.key == key }?.value?.toString()
    }

    fun getFeeValue(name: String): String? {
        return feeLines?.find { it.name == name }?.total
    }

    fun getFeeTax(name: String): String? {
        return feeLines?.find { it.name == name }?.totalTax
    }
}

/**
 * 元数据响应模型
 */
data class MetaDataResponse(
    @SerializedName("id")
    val id: Long,
    
    @SerializedName("key")
    val key: String,
    
    @SerializedName("value")
    val value: Any
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
    val total: String,
    
    @SerializedName("total_tax")
    val totalTax: String,
    
    @SerializedName("meta_data")
    val metaData: List<MetaDataResponse>
)

/**
 * 账单信息响应模型
 */
data class BillingResponse(
    @SerializedName("first_name")
    val firstName: String,
    
    @SerializedName("last_name")
    val lastName: String,
    
    @SerializedName("company")
    val company: String?,
    
    @SerializedName("email")
    val email: String?,
    
    @SerializedName("phone")
    val phone: String?,
    
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
    
    @SerializedName("company")
    val company: String?,
    
    @SerializedName("phone")
    val phone: String?,
    
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
 * 费用行响应模型
 */
data class FeeLine(
    @SerializedName("id")
    val id: Long,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("total")
    val total: String,
    
    @SerializedName("total_tax")
    val totalTax: String
)

/**
 * 税费行响应模型
 */
data class TaxLine(
    @SerializedName("id")
    val id: Long,
    
    @SerializedName("rate_code")
    val rateCode: String,
    
    @SerializedName("label")
    val label: String,
    
    @SerializedName("rate_percent")
    val ratePercent: Double,
    
    @SerializedName("tax_total")
    val taxTotal: String
)