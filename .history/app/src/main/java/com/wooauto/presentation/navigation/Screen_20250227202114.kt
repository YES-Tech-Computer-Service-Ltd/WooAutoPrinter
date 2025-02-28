package com.wooauto.presentation.navigation

/**
 * 定义应用的导航路由
 */
sealed class Screen(val route: String) {
    object Orders : Screen("orders")
    object OrderDetails : Screen("orders") {
        fun createRoute(orderId: String) = "orders/$orderId"
    }
    object Products : Screen("products")
    object ProductDetails : Screen("products") {
        fun createRoute(productId: Long) = "products/$productId"
    }
    object Settings : Screen("settings")
} 