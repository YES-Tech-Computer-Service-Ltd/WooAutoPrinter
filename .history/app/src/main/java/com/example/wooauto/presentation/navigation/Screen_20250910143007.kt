package com.example.wooauto.presentation.navigation

sealed class Screen(val route: String) {
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
    object AutomationSettings : Screen("automation_settings")
    object LicenseSettings : Screen("license_settings")
    object LanguageSettings : Screen("language_settings")

    
    companion object {
    }
} 
 
// 新增：Settings 子路由（用于二级菜单）
object SettingsSectionRoutes {
    const val pattern = "settings/{section}"
    fun routeFor(section: String) = "settings/$section"
}