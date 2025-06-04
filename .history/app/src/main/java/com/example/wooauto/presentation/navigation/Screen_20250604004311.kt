package com.example.wooauto.presentation.navigation

import com.example.wooauto.navigation.AppNavigation

/**
 * @deprecated 此类已过时，请使用 AppNavigation 进行路由管理
 * 这个类保留是为了向后兼容，建议使用 AppNavigation.Settings.* 替代
 */
@Deprecated(
    message = "使用 AppNavigation 替代",
    replaceWith = ReplaceWith("AppNavigation", "com.example.wooauto.navigation.AppNavigation")
)
sealed class Screen(val route: String) {
    object Settings : Screen(AppNavigation.Main.SETTINGS)
    object WebsiteSettings : Screen(AppNavigation.Settings.WEBSITE_SETTINGS)
    object PrinterSettings : Screen(AppNavigation.Settings.PRINTER_SETTINGS)
    object PrinterDetails : Screen(AppNavigation.Settings.PRINTER_DETAILS) {
        fun printerDetailsRoute(printerId: String) = AppNavigation.Settings.printerDetailsRoute(printerId)
    }
    object PrintTemplates : Screen(AppNavigation.Templates.PRINT_TEMPLATES)
    object TemplatePreview : Screen(AppNavigation.Templates.TEMPLATE_PREVIEW) {
        fun templatePreviewRoute(templateId: String) = AppNavigation.Templates.templatePreviewRoute(templateId)
    }
    object SoundSettings : Screen(AppNavigation.Settings.SOUND_SETTINGS)
    object AutomationSettings : Screen(AppNavigation.Settings.AUTOMATION_SETTINGS)
    object LicenseSettings : Screen(AppNavigation.Settings.LICENSE_SETTINGS)
    object LanguageSettings : Screen(AppNavigation.Settings.LANGUAGE_SETTINGS)

    
    companion object {
    }
} 