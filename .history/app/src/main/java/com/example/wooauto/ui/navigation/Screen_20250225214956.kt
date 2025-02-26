package com.example.wooauto.ui.navigation

sealed class Screen(val route: String) {
    object Orders : Screen("orders")
    object OrderDetails : Screen("orders/{orderId}") {
        fun createRoute(orderId: Long) = "orders/$orderId"
    }
    
    object Products : Screen("products")
    object ProductDetails : Screen("products/{productId}") {
        fun createRoute(productId: Long) = "products/$productId"
    }
    
    object Settings : Screen("settings")
    object PrinterSetup : Screen("settings/printer")
    object WebsiteSetup : Screen("settings/website")
} 