package com.example.wooauto.presentation.navigation

sealed class Screen(val route: String) {
    object Settings : Screen("settings")
    object WebsiteSettings : Screen("website_settings")
    object PrinterSettings : Screen("printer_settings")
    object PrinterDetails : Screen("printer_details_select_device")
    object PrinterConfigurationSetup : Screen("printer_configuration_setup/{printerId}?name={name}&address={address}") {
        fun createRoute(printerId: String?, name: String? = null, address: String? = null): String {
            val idSegment = printerId ?: "null" // Use "null" string if printerId is actually null
            var route = "printer_configuration_setup/$idSegment"
            val queryParams = mutableListOf<String>()
            name?.let { queryParams.add("name=$it") }
            address?.let { queryParams.add("address=$it") }
            if (queryParams.isNotEmpty()) {
                route += "?" + queryParams.joinToString("&")
            }
            return route
        }
    }
    object PrintTemplates : Screen("print_templates")
    object TemplatePreview : Screen("template_preview/{templateId}") {
        fun templatePreviewRoute(templateId: String) = "template_preview/$templateId"
    }
    object SoundSettings : Screen("sound_settings")
    object AutomationSettings : Screen("automation_settings")
    object LicenseSettings : Screen("license_settings")
    object LanguageSettings : Screen("language_settings")

    
    companion object {
    }
} 