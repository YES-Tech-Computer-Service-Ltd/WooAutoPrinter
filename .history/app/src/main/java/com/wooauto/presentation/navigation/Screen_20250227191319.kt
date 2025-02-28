package com.wooauto.presentation.navigation

/**
 * 定义应用的导航路由
 */
sealed class Screen(val route: String) {
    object Orders : Screen("orders")
    object OrderDetails : Screen("order/{orderId}") {
        fun createRoute(orderId: Long) = "order/$orderId"
    }
    object Products : Screen("products")
    object ProductDetails : Screen("product/{productId}") {
        fun createRoute(productId: Long) = "product/$productId"
    }
    object Settings : Screen("settings")
} 