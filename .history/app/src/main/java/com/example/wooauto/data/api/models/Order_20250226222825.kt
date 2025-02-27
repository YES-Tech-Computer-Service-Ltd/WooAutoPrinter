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

        // WooCommerce Food plugin的元数据键名
        private const val META_DELIVERY_DATE = "exwfood_date_deli"
        private const val META_DELIVERY_TIME = "exwfood_time_deli"
        private const val META_DELIVERY_TIMESLOT = "exwfood_timeslot"
        private const val META_ORDER_METHOD = "exwfood_order_method"
        private const val META_ORDER_TYPE = "woofood_order_type"
    }

    init {
        Log.d(TAG, "===== 开始解析订单 ID: $id =====")
        parseMetaData()
        parseFeeLines()
        logOrderDetails()
    }

    private fun parseMetaData() {
        Log.d(TAG, "开始解析元数据，共 ${metaData.size} 条")
        metaData.forEach { meta ->
            Log.d(TAG, "解析元数据: key=${meta.key}, value=${meta.value}")
            when (meta.key) {
                META_DELIVERY_DATE -> {
                    deliveryDate = meta.value.toString()
                    Log.d(TAG, "设置配送日期: $deliveryDate")
                }
                META_DELIVERY_TIME -> {
                    deliveryTime = meta.value.toString()
                    Log.d(TAG, "设置配送时间: $deliveryTime")
                }
                META_DELIVERY_TIMESLOT -> {
                    if (deliveryTime == null) {
                        val slot = meta.value.toString()
                        deliveryTime = slot.replace("|", " - ")
                        Log.d(TAG, "设置时间段: $deliveryTime")
                    }
                }
                META_ORDER_METHOD, META_ORDER_TYPE -> {
                    orderMethod = meta.value.toString()
                    Log.d(TAG, "设置配送方式: $orderMethod")
                }
            }
        }
    }

    private fun parseFeeLines() {
        Log.d(TAG, """
            ===== 开始解析费用信息 =====
            订单ID: $id
            订单编号: $number
            费用行数量: ${feeLines.size}
            费用行详情:
            ${feeLines.joinToString("\n") { "- ${it.name}: ${it.total} (税: ${it.totalTax})" }}
            ========================
        """.trimIndent())

        feeLines.forEach { fee ->
            Log.d(TAG, """
                处理费用行:
                - 名称: ${fee.name}
                - 金额: ${fee.total}
                - 税额: ${fee.totalTax}
                - 税类: ${fee.taxClass}
                - 税状态: ${fee.taxStatus}
            """.trimIndent())

            when {
                fee.name.equals("tip", ignoreCase = true) ||
                fee.name.equals("Tip", ignoreCase = true) ||
                fee.name.equals("小费", ignoreCase = true) -> {
                    tip = fee.total
                    Log.d(TAG, "设置小费: $tip")
                }
                fee.name.equals("Shipping fee", ignoreCase = true) ||
                fee.name.equals("Delivery Fee", ignoreCase = true) ||
                fee.name.equals("配送费", ignoreCase = true) ||
                fee.name.equals("运费", ignoreCase = true) -> {
                    deliveryFee = fee.total
                    Log.d(TAG, "设置配送费: $deliveryFee")
                }
            }
        }

        Log.d(TAG, """
            ===== 费用解析结果 =====
            - 小费: ${tip ?: "未设置"}
            - 配送费: ${deliveryFee ?: "未设置"}
            =====================
        """.trimIndent())
    }

    private fun logOrderDetails() {
        Log.d(TAG, """
            ===== 订单解析结果 =====
            - 订单号: $number
            - 配送方式: ${orderMethod ?: "未设置"}
            - 配送日期: ${deliveryDate ?: "未设置"}
            - 配送时间: ${deliveryTime ?: "未设置"}
            - 小费: ${tip ?: "未设置"}
            - 配送费: ${deliveryFee ?: "未设置"}
            - 总金额: $total
            =======================
        """.trimIndent())
    }

    // 获取格式化的配送信息(用于列表页显示)
    fun getDeliveryInfo(): String {
        return when (orderMethod?.lowercase()) {
            "delivery" -> "配送"
            "pickup" -> "自取"
            else -> orderMethod ?: "未知"
        }
    }

    // 获取完整的配送详情(用于详情页显示)
    fun getFullDeliveryDetails(): Map<String, String?> {
        return mapOf(
            "配送方式" to getDeliveryInfo(),
            "配送日期" to deliveryDate,
            "配送时间" to deliveryTime,
            "配送费" to deliveryFee,
            "小费" to tip
        )
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