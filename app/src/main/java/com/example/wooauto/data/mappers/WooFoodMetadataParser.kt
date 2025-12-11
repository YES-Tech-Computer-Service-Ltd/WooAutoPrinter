package com.example.wooauto.data.mappers

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统一解析 WooFood 元数据，避免在不同链路中重复实现解析逻辑。
 * 仅对元数据中明确存在的字段进行解析，不尝试从备注等不可靠来源“猜测”值。
 */
object WooFoodMetadataParser {

    private val textualDatePatterns = listOf(
        "MMMM d, yyyy",
        "MMM d, yyyy",
        "yyyy-MM-dd",
        "MM/dd/yyyy"
    )

    data class Result(
        val orderMethod: String?,
        val deliveryDate: String?,
        val deliveryTime: String?,
        val dineInPersonCount: String?,
        val isDelivery: Boolean
    )

    /**
     * @param metaEntries 任意 Key-Value 形式的元数据集合
     */
    fun parse(metaEntries: List<Pair<String, Any?>>): Result {
        var orderMethod: String? = null
        var deliveryTime: String? = null
        var deliveryDate: String? = null
        var dineInPersonCount: String? = null

        metaEntries.forEach { (rawKey, rawValue) ->
            val key = rawKey.lowercase(Locale.ROOT)
            val value = rawValue?.toString()?.trim() ?: return@forEach

            when (key) {
                "exwfood_order_method" -> {
                    orderMethod = value
                }
                "exwfood_time_deli", "exwfood_delivery_time", "_woofood_delivery_time" -> {
                    deliveryTime = normalizeTime(value)
                }
                "exwfood_person_dinein" -> {
                    // WooFood dine-in people count (party size). Keep raw string to be robust to upstream formats.
                    dineInPersonCount = value
                }
                "exwfood_datetime_deli_unix",
                "exwfood_date_deli_unix" -> {
                    if (deliveryDate == null) {
                        deliveryDate = parseUnixDate(value)
                    }
                }
                "exwfood_date_deli",
                "exwfood_delivery_date",
                "_woofood_delivery_date" -> {
                    if (deliveryDate == null) {
                        deliveryDate = parseTextualDate(value)
                    }
                }
            }
        }

        val isDelivery = orderMethod?.equals("delivery", ignoreCase = true) == true

        return Result(
            orderMethod = orderMethod,
            deliveryDate = deliveryDate,
            deliveryTime = deliveryTime,
            dineInPersonCount = dineInPersonCount,
            isDelivery = isDelivery
        )
    }

    private fun parseUnixDate(value: String): String? {
        val timestamp = value.toLongOrNull() ?: return null
        val date = Date(timestamp * 1000)
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return formatter.format(date)
    }

    private fun parseTextualDate(raw: String): String? {
        val normalized = raw.replace("，", ",").trim()
        textualDatePatterns.forEach { pattern ->
            try {
                val formatter = SimpleDateFormat(pattern, Locale.ENGLISH)
                val parsed = formatter.parse(normalized)
                if (parsed != null) {
                    val targetFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    return targetFormatter.format(parsed)
                }
            } catch (_: Exception) {
                // 尝试下一种格式
            }
        }
        return null
    }

    private fun normalizeTime(raw: String): String {
        return raw.replace('：', ':').trim()
    }
}

