package com.example.wooauto.presentation.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Orders : Screen("orders")
    object Products : Screen("products")
    object Settings : Screen("settings")
    object PrinterSettings : Screen("printer_settings")
    object PrinterDetails : Screen("printer_details/{printerId}") {
        fun printerDetailsRoute(printerId: String) = "printer_details/$printerId"
    }
    object PrintTemplates : Screen("print_templates")
    object TemplatePreview : Screen("template_preview/{templateId}") {
        fun templatePreviewRoute(templateId: String) = "template_preview/$templateId"
    }
    object SoundSettings : Screen("sound_settings")
    object AutomationSettings : Screen("automation_settings")
    object LicenseSettings : Screen("license_settings")

    
    companion object {
    }
} 