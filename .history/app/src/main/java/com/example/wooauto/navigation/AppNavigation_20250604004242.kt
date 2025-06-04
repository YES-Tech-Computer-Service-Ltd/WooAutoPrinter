package com.example.wooauto.navigation

/**
 * 应用导航路由定义
 * 统一管理所有页面路由，避免硬编码字符串
 */
object AppNavigation {
    
    // ==== 主要页面路由 ====
    object Main {
        const val ORDERS = "orders"
        const val PRODUCTS = "products" 
        const val SETTINGS = "settings"
    }
    
    // ==== 设置相关路由 ====
    object Settings {
        const val WEBSITE_SETTINGS = "website_settings"
        const val PRINTER_SETTINGS = "printer_settings"
        const val SOUND_SETTINGS = "sound_settings"
        const val AUTOMATION_SETTINGS = "automation_settings"
        const val LICENSE_SETTINGS = "license_settings"
        const val LANGUAGE_SETTINGS = "language_settings"
        
        // 带参数的路由
        const val PRINTER_DETAILS = "printer_details/{printerId}"
        
        // 构建带参数的路由
        fun printerDetailsRoute(printerId: String) = "printer_details/$printerId"
    }
    
    // ==== 打印模板相关路由 ====
    object Templates {
        const val PRINT_TEMPLATES = "print_templates"
        const val TEMPLATE_PREVIEW = "template_preview/{templateId}"
        
        // 构建带参数的路由
        fun templatePreviewRoute(templateId: String) = "template_preview/$templateId"
    }
    
    // ==== 其他功能路由 ====
    object Features {
        const val UPDATE_CHECK = "update_check"
        const val ABOUT = "about"
        const val HELP = "help"
    }
    
    /**
     * 获取所有主导航路由
     */
    fun getMainNavigationRoutes(): List<String> {
        return listOf(
            Main.ORDERS,
            Main.PRODUCTS,
            Main.SETTINGS
        )
    }
    
    /**
     * 获取所有设置页面路由
     */
    fun getSettingsRoutes(): List<String> {
        return listOf(
            Settings.WEBSITE_SETTINGS,
            Settings.PRINTER_SETTINGS,
            Settings.SOUND_SETTINGS,
            Settings.AUTOMATION_SETTINGS,
            Settings.LICENSE_SETTINGS,
            Settings.LANGUAGE_SETTINGS
        )
    }
    
    /**
     * 判断路由是否为设置页面
     */
    fun isSettingsRoute(route: String?): Boolean {
        return route != null && (
            getSettingsRoutes().contains(route) ||
            route.startsWith("printer_details/") ||
            route.startsWith("template_preview/")
        )
    }
    
    /**
     * 判断路由是否为主导航页面
     */
    fun isMainNavigationRoute(route: String?): Boolean {
        return route != null && getMainNavigationRoutes().contains(route)
    }
} 