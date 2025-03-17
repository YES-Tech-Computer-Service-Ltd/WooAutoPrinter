package com.example.wooauto.presentation.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Orders : Screen("orders")
    object Products : Screen("products")
    object Settings : Screen("settings")
    object WebsiteSettings : Screen("website_settings")
    object PrinterSettings : Screen("printer_settings")
    object PrinterDetails : Screen("printer_details/{printerId}") {
        fun printerDetailsRoute(printerId: String) = "printer_details/$printerId"
    }
    object PrintTemplates : Screen("print_templates")
    object TemplatePreview : Screen("template_preview/{templateId}") {
        fun templatePreviewRoute(templateId: String) = "template_preview/$templateId"
    }
    object SoundSettings : Screen("sound_settings")
    
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
        // 其他方法可以放在这里
    }
} 