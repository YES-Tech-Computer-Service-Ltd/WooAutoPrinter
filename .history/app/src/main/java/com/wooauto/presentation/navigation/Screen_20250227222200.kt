package com.wooauto.presentation.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Orders : Screen("orders")
    object Products : Screen("products")
    object Settings : Screen("settings")
    object WebsiteSettings : Screen("website_settings")
} 