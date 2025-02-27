package com.example.wooauto.data.api.models

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.util.Date

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
    init {
        // 解析元数据中的送餐信息
        metaData.forEach { meta ->
            when (meta.key) {
                "exwfood_date_deli" -> deliveryDate = meta.value.toString()
                "exwfood_time_deli" -> deliveryTime = meta.value.toString()
                "exwfood_timeslot" -> {
                    if (deliveryTime == null) {
                        val slot = meta.value.toString()
                        deliveryTime = slot.replace("|", " - ")
                    }
                }
                "exwfood_order_method", "woofood_order_type" -> orderMethod = meta.value.toString()
            }
        }

        // 解析费用信息
        feeLines.forEach { fee ->
            when {
                fee.name.equals("tip", ignoreCase = true) -> tip = fee.total
                fee.name.equals("Shipping fee", ignoreCase = true) ||
                        fee.name.equals("Delivery Fee", ignoreCase = true) -> deliveryFee = fee.total
            }
        }
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
)

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