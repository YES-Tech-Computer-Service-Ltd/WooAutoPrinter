package com.example.wooauto.presentation.screens.orders

import com.example.wooauto.domain.models.WooFoodInfo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class DeliveryDisplayInfo(
    val headline: String,
    val timeLabel: String,
    val hasDate: Boolean,
    val isFutureOrToday: Boolean
)

object DeliveryDisplayFormatter {

    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    private val zhWeekdays = arrayOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")

    fun format(
        wooFoodInfo: WooFoodInfo?,
        orderDate: Date,
        locale: Locale = Locale.getDefault()
    ): DeliveryDisplayInfo {
        val dateInfo = buildDateHeadline(wooFoodInfo?.deliveryDate, locale)
        val timeLabel = buildTimeLabel(wooFoodInfo?.deliveryTime, orderDate, locale)
        return DeliveryDisplayInfo(
            headline = dateInfo.headline,
            timeLabel = timeLabel,
            hasDate = dateInfo.hasDate,
            isFutureOrToday = dateInfo.isFutureOrToday
        )
    }

    private data class DateInfo(
        val headline: String,
        val hasDate: Boolean,
        val isFutureOrToday: Boolean
    )

    private fun buildDateHeadline(dateRaw: String?, locale: Locale): DateInfo {
        if (dateRaw.isNullOrBlank()) {
            val placeholder = if (isChinese(locale)) "— • 日期缺失" else "— • Date missing"
            return DateInfo(
                headline = placeholder,
                hasDate = false,
                isFutureOrToday = false
            )
        }

        val parsed = parseIsoDate(dateRaw) ?: run {
            val fallback = if (isChinese(locale)) "— • 日期格式错误" else "— • Invalid date"
            return DateInfo(fallback, hasDate = false, isFutureOrToday = false)
        }

        val targetCal = Calendar.getInstance().apply {
            time = parsed
            clearTime()
        }
        val todayCal = Calendar.getInstance().apply { clearTime() }

        val diffDays = ((targetCal.timeInMillis - todayCal.timeInMillis) / DAY_MILLIS).toInt()

        val dateLabel = if (isChinese(locale)) {
            "${targetCal.get(Calendar.MONTH) + 1}月${targetCal.get(Calendar.DAY_OF_MONTH)}日"
        } else {
            SimpleDateFormat("MMM d", locale).format(parsed)
        }

        val relativeLabel = when (diffDays) {
            0 -> if (isChinese(locale)) "今天" else "Today"
            1 -> if (isChinese(locale)) "明天" else "Tomorrow"
            -1 -> if (isChinese(locale)) "昨天" else "Yesterday"
            else -> buildWeekdayLabel(targetCal, locale)
        }

        val headline = "$dateLabel • $relativeLabel"
        return DateInfo(
            headline = headline,
            hasDate = true,
            isFutureOrToday = diffDays >= 0
        )
    }

    private fun buildTimeLabel(timeRaw: String?, orderDate: Date, locale: Locale): String {
        val cleaned = timeRaw
            ?.replace('：', ':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (cleaned != null) {
            return cleaned
        }

        val fallback = SimpleDateFormat("MM-dd HH:mm", locale)
        return fallback.format(orderDate)
    }

    private fun parseIsoDate(raw: String): Date? {
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
            }.parse(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildWeekdayLabel(cal: Calendar, locale: Locale): String {
        return if (isChinese(locale)) {
            val index = cal.get(Calendar.DAY_OF_WEEK) - 1
            zhWeekdays[index.coerceIn(0, zhWeekdays.lastIndex)]
        } else {
            SimpleDateFormat("EEEE", locale).format(cal.time)
        }
    }

    private fun Calendar.clearTime() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun isChinese(locale: Locale): Boolean {
        return locale.language.equals("zh", ignoreCase = true)
    }
}

