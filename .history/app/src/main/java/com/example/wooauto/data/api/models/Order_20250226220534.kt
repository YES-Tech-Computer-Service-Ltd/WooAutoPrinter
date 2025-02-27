package com.example.wooauto.data.api.models

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.util.Date
import android.util.Log

data class Order(
    @SerializedName("id")
    val id: Long,

    @SerializedName("number")
    val number: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("date_created")
    val dateCreated: Date,

    @SerializedName("total")
    val total: String,

    @SerializedName("customer_id")
    val customerId: Long,

    @SerializedName("billing")
    val billing: BillingAddress,

    @SerializedName("shipping")
    val shipping: ShippingAddress,

    @SerializedName("line_items")
    val lineItems: List<LineItem>,

    @SerializedName("payment_method")
    val paymentMethod: String,

    @SerializedName("payment_method_title")
    val paymentMethodTitle: String,

    @SerializedName("meta_data")
    val metaData: List<MetaData> = emptyList(),

    @SerializedName("fee_lines")
    val feeLines: List<FeeLine> = emptyList(),

    @SerializedName("tax_lines")
    val taxLines: List<TaxLine> = emptyList(),

    @SerializedName("shipping_lines")
    val shippingLines: List<ShippingLine> = emptyList(),

    @SerializedName("coupon_lines")
    val couponLines: List<Any> = emptyList(),

    // Local tracking data - not from API
    var isPrinted: Boolean = false,
    var notificationShown: Boolean = false,

    // WooCommerce Food plugin - parsed fields (not directly from API)
    var deliveryDate: String? = null,
    var deliveryTime: String? = null,
    var orderMethod: String? = null,
    var tip: String? = null,
    var deliveryFee: String? = null
) {
    companion object {
        private const val TAG = "Order_DEBUG"
    }

    init {
        Log.d(TAG, "===== 开始解析订单 ID: $id =====")
        Log.d(TAG, "开始解析元数据，共 ${metaData.size} 条")
        
        metaData.forEach { meta ->
            Log.d(TAG, "解析元数据: key=${meta.key}, value=${meta.value}")
            when (meta.key) {
                "exwfood_date_deli", "_delivery_date", "delivery_date" -> {
                    deliveryDate = meta.value.toString()
                    Log.d(TAG, "设置配送日期: $deliveryDate")
                }
                "exwfood_time_deli", "_delivery_time", "delivery_time" -> {
                    deliveryTime = meta.value.toString()
                    Log.d(TAG, "设置配送时间: $deliveryTime")
                }
                "exwfood_timeslot", "_delivery_timeslot", "delivery_timeslot" -> {
                    if (deliveryTime == null) {
                        val slot = meta.value.toString()
                        deliveryTime = slot.replace("|", " - ")
                        Log.d(TAG, "设置时间段: $deliveryTime")
                    }
                }
                "exwfood_order_method", "woofood_order_type", "_delivery_type", "delivery_type" -> {
                    orderMethod = meta.value.toString()
                    Log.d(TAG, "设置配送方式: $orderMethod")
                }
            }
        }

        Log.d(TAG, "开始解析费用信息，共 ${feeLines.size} 条")
        feeLines.forEach { fee ->
            Log.d(TAG, "解析费用: name=${fee.name}, total=${fee.total}")
            when {
                fee.name.equals("tip", ignoreCase = true) || 
                fee.name.contains("小费", ignoreCase = true) -> {
                    tip = fee.total
                    Log.d(TAG, "设置小费: $tip")
                }
                fee.name.equals("Shipping fee", ignoreCase = true) ||
                fee.name.equals("Delivery Fee", ignoreCase = true) ||
                fee.name.contains("配送费", ignoreCase = true) -> {
                    deliveryFee = fee.total
                    Log.d(TAG, "设置配送费: $deliveryFee")
                }
            }
        }

        Log.d(TAG, """
            ===== 订单解析结果 =====
            - 配送日期: ${deliveryDate ?: "未设置"}
            - 配送时间: ${deliveryTime ?: "未设置"}
            - 配送方式: ${orderMethod ?: "未设置"}
            - 小费: ${tip ?: "未设置"}
            - 配送费: ${deliveryFee ?: "未设置"}
            =======================
        """.trimIndent())
    }
}

data class BillingAddress(
    @SerializedName("first_name")
    val firstName: String,

    @SerializedName("last_name")
    val lastName: String,

    @SerializedName("company")
    val company: String?,

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
    val country: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("phone")
    val phone: String
) {
    fun getFullName(): String = "$firstName $lastName"
}

data class ShippingAddress(
    @SerializedName("first_name")
    val firstName: String,

    @SerializedName("last_name")
    val lastName: String,

    @SerializedName("company")
    val company: String?,

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
) {
    fun getFullName(): String = "$firstName $lastName"

    fun getFormattedAddress(): String {
        val parts = mutableListOf<String>()
        parts.add(address1)
        if (!address2.isNullOrBlank()) parts.add(address2)
        parts.add("$city, $state $postcode")
        parts.add(country)
        return parts.joinToString(", ")
    }
}

data class LineItem(
    @SerializedName("id")
    val id: Long,

    @SerializedName("name")
    val name: String,

    @SerializedName("product_id")
    val productId: Long,

    @SerializedName("variation_id")
    val variationId: Long,

    @SerializedName("quantity")
    val quantity: Int,

    @SerializedName("tax_class")
    val taxClass: String,

    @SerializedName("subtotal")
    val subtotal: String,

    @SerializedName("total")
    val total: String,

    @SerializedName("meta_data")
    val metaData: List<MetaData>?
)

data class MetaData(
    @SerializedName("id")
    val id: Long,

    @SerializedName("key")
    val key: String,

    @SerializedName("value")
    val value: Any
) {
    // Add a helper method to parse WooFood exceptions
    fun getFormattedValue(): String {
        return when {
            key == "_exceptions" -> {
                val rawValue = value.toString()
                try {
                    // Extract useful parts from the exceptions data
                    if (rawValue.contains("value=")) {
                        val valueStart = rawValue.indexOf("value=") + 6
                        val valueEnd = rawValue.indexOf(",", valueStart).takeIf { it > 0 } ?: rawValue.length
                        rawValue.substring(valueStart, valueEnd).trim()
                    } else {
                        "Option details"
                    }
                } catch (e: Exception) {
                    "Option"
                }
            }
            else -> value.toString()
        }
    }
}

data class FeeLine(
    @SerializedName("id")
    val id: Long,

    @SerializedName("name")
    val name: String,

    @SerializedName("tax_class")
    val taxClass: String,

    @SerializedName("tax_status")
    val taxStatus: String,

    @SerializedName("amount")
    val amount: String,

    @SerializedName("total")
    val total: String,

    @SerializedName("total_tax")
    val totalTax: String,

    @SerializedName("taxes")
    val taxes: List<Tax>? = null,

    @SerializedName("meta_data")
    val metaData: List<MetaData>? = null
)

data class Tax(
    @SerializedName("id")
    val id: Long,

    @SerializedName("total")
    val total: String,

    @SerializedName("subtotal")
    val subtotal: String?
)

data class TaxLine(
    @SerializedName("id")
    val id: Long,

    @SerializedName("rate_code")
    val rateCode: String,

    @SerializedName("rate_id")
    val rateId: Long,

    @SerializedName("label")
    val label: String,

    @SerializedName("compound")
    val compound: Boolean,

    @SerializedName("tax_total")
    val taxTotal: String,

    @SerializedName("shipping_tax_total")
    val shippingTaxTotal: String,

    @SerializedName("rate_percent")
    val ratePercent: Int,

    @SerializedName("meta_data")
    val metaData: List<MetaData>? = null
)

data class ShippingLine(
    @SerializedName("id")
    val id: Long,

    @SerializedName("method_title")
    val methodTitle: String,

    @SerializedName("method_id")
    val methodId: String,

    @SerializedName("total")
    val total: String,

    @SerializedName("total_tax")
    val totalTax: String,

    @SerializedName("taxes")
    val taxes: List<Tax>? = null,

    @SerializedName("meta_data")
    val metaData: List<MetaData>? = null
)