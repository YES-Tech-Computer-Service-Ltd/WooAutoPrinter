package com.example.wooauto.presentation.navigation

sealed class Screen(val route: String) {
    object Settings : Screen("settings")
    // 新增：Orders 子路由模式
    object OrdersSection : Screen("orders/{section}") {
        fun routeFor(section: String) = "orders/$section"
    }
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

// 新增：Settings 二级子路由（用于在某个 section 下再进入更深一层，比如 general/language）
object SettingsSubPageRoutes {
    const val pattern = "settings/{section}/{sub}"
    fun routeFor(section: String, sub: String) = "settings/$section/$sub"
}

// 新增：Store Settings 路由
object StoreSettingsRoutes {
    const val pattern = "settings/general/store"
    const val route = "settings/general/store"
}