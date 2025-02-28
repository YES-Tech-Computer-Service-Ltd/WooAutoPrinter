package com.example.wooauto.presentation.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Orders : Screen("orders")
    object Products : Screen("products")
    object Settings : Screen("settings")
    object WebsiteSettings : Screen("website_settings")
    object PrinterSettings : Screen("printer_settings")
    object PrinterDetails : Screen("printer_details/{printerId}")
    
    fun createRoute(vararg args: String): String {
        return buildString {
            append(route)
            args.forEach { arg ->
                route.indexOf("{").takeIf { it > 0 }?.let {
                    val endIndex = route.indexOf("}")
                    if (endIndex > it) {
                        val oldArg = route.substring(it, endIndex + 1)
                        append(route.replace(oldArg, arg))
                    }
                }
            }
        }
    }
    
    companion object {
        fun printerDetailsRoute(printerId: String) = "printer_details/$printerId"
    }
} 