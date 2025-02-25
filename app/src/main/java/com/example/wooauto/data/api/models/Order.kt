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

    // Local tracking data - not from API
    var isPrinted: Boolean = false,
    var notificationShown: Boolean = false
)

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