package com.example.wooauto.presentation.navigation

// 导入自动镜像版本的图标
// 导入产品相关图标

sealed class NavigationItem(
    val route: String,
    val title: String
) {
    object Orders : NavigationItem(
        route = "orders",
        title = "订单"
    )
    
    object Products : NavigationItem(
        route = "products",
        title = "产品"
    )
    
    object Settings : NavigationItem(
        route = "settings",
        title = "设置"
    )
    
    companion object {
        val items = listOf(Orders, Products, Settings)
    }
} 