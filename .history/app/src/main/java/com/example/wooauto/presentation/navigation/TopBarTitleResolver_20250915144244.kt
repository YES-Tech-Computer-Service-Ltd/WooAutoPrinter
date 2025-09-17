package com.example.wooauto.presentation.navigation

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.wooauto.R
import com.example.wooauto.navigation.NavigationItem

/**
 * 统一解析 TopBar 标题的工具函数。
 * 返回 null 表示未匹配动态标题，外层应回退到静态 titleResId。
 */
@Composable
fun resolveTopBarTitle(route: String, args: Bundle?): String? {
    // orders/{section} → 显示 Orders - 子标题
    if (route.startsWith("orders/")) {
        val section = route.removePrefix("orders/")
        val subRes = when (section) {
            "active" -> R.string.orders_active
            "history" -> R.string.orders_history
            else -> null
        }
        if (subRes != null) {
            return stringResource(R.string.orders) + " - " + stringResource(subRes)
        }
    }

    // settings/{section}/{sub} → 生成 “一级 - 二级” 复合标题
    if (route.startsWith("settings/")) {
        val section = args?.getString("section")
        val sub = args?.getString("sub")
        if (!section.isNullOrEmpty() && !sub.isNullOrEmpty()) {
            val sectionRes = AppNavConfig
                .subEntriesForRoute(NavigationItem.Settings.route)
                .find { it.route == section }?.titleResId
            val subRes = when (sub) {
                "language" -> R.string.language
                "display" -> R.string.display_settings
                "store" -> R.string.store_settings
                "sound" -> R.string.sound_settings
                "templates" -> R.string.printer_templates
                else -> null
            }
            if (sectionRes != null && subRes != null) {
                return stringResource(sectionRes) + " - " + stringResource(subRes)
            }
        }
    }

    // template_preview/{templateId} → 生成 “Printing - 模板名”
    if (route.startsWith("template_preview/")) {
        val templateId = args?.getString("templateId")
        val nameRes = when (templateId) {
            "full_details" -> R.string.template_full_order_details
            "delivery" -> R.string.template_delivery_receipt
            "kitchen" -> R.string.template_kitchen_order
            "new" -> R.string.template_new_custom
            else -> R.string.template_custom
        }
        return stringResource(R.string.printing) + " - " + stringResource(nameRes)
    }

    return null
}


