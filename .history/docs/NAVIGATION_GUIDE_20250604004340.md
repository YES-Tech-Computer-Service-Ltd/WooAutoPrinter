# WooAuto 导航系统指南

## 📱 导航架构概览

WooAuto采用统一的导航管理架构，所有路由都通过`AppNavigation`集中管理。

### 🗂️ **文件结构**

```
app/src/main/java/com/example/wooauto/navigation/
├── AppNavigation.kt          # 🎯 统一路由管理（推荐）
├── NavigationItem.kt         # 🔗 底部导航栏项目
└── presentation/navigation/
    └── Screen.kt            # ⚠️ 已废弃，向后兼容保留
```

## 🎯 **推荐用法：AppNavigation**

### 基本导航

```kotlin
// ✅ 推荐：使用 AppNavigation
import com.example.wooauto.navigation.AppNavigation

// 主要页面导航
navController.navigate(AppNavigation.Main.ORDERS)
navController.navigate(AppNavigation.Main.PRODUCTS)
navController.navigate(AppNavigation.Main.SETTINGS)

// 设置页面导航
navController.navigate(AppNavigation.Settings.PRINTER_SETTINGS)
navController.navigate(AppNavigation.Settings.WEBSITE_SETTINGS)
```

### 带参数的导航

```kotlin
// ✅ 推荐：使用 AppNavigation 的帮助方法
val printerId = "12345"
navController.navigate(
    AppNavigation.Settings.printerDetailsRoute(printerId)
)

val templateId = "kitchen"
navController.navigate(
    AppNavigation.Templates.templatePreviewRoute(templateId)
)
```

### 条件导航判断

```kotlin
// ✅ 推荐：使用 AppNavigation 的工具方法
val currentRoute = navController.currentDestination?.route

if (AppNavigation.isSettingsRoute(currentRoute)) {
    // 处理设置页面特殊逻辑
    showBackButton = true
}

if (AppNavigation.isMainNavigationRoute(currentRoute)) {
    // 处理主导航页面逻辑
    showBottomNavigation = true
}
```

## ⚠️ **废弃用法：Screen**

```kotlin
// ❌ 废弃：不推荐使用 Screen
import com.example.wooauto.presentation.navigation.Screen

// 这些代码仍然可用，但建议迁移到 AppNavigation
navController.navigate(Screen.Settings.route)
navController.navigate(Screen.PrinterSettings.route)
navController.navigate(Screen.PrinterDetails.printerDetailsRoute(printerId))
```

## 🔄 **迁移指南**

### 从 Screen 迁移到 AppNavigation

```kotlin
// 旧代码 ❌
navController.navigate(Screen.Settings.route)
navController.navigate(Screen.PrinterSettings.route)
navController.navigate(Screen.PrinterDetails.printerDetailsRoute(printerId))

// 新代码 ✅
navController.navigate(AppNavigation.Main.SETTINGS)
navController.navigate(AppNavigation.Settings.PRINTER_SETTINGS)
navController.navigate(AppNavigation.Settings.printerDetailsRoute(printerId))
```

### 路由字符串对照表

| 功能 | 旧方式 (Screen) | 新方式 (AppNavigation) |
|------|----------------|----------------------|
| 主设置页 | `Screen.Settings.route` | `AppNavigation.Main.SETTINGS` |
| 打印机设置 | `Screen.PrinterSettings.route` | `AppNavigation.Settings.PRINTER_SETTINGS` |
| 网站设置 | `Screen.WebsiteSettings.route` | `AppNavigation.Settings.WEBSITE_SETTINGS` |
| 打印机详情 | `Screen.PrinterDetails.printerDetailsRoute(id)` | `AppNavigation.Settings.printerDetailsRoute(id)` |

## 🏗️ **在Compose中使用**

### NavHost 配置

```kotlin
NavHost(
    navController = navController,
    startDestination = AppNavigation.Main.ORDERS
) {
    // 主要页面
    composable(AppNavigation.Main.ORDERS) {
        OrdersScreen(navController = navController)
    }
    
    composable(AppNavigation.Main.PRODUCTS) {
        ProductsScreen(navController = navController)
    }
    
    composable(AppNavigation.Main.SETTINGS) {
        SettingsScreen(navController = navController)
    }
    
    // 设置页面
    composable(AppNavigation.Settings.PRINTER_SETTINGS) {
        PrinterSettingsScreen(navController = navController)
    }
    
    // 带参数的页面
    composable(
        route = AppNavigation.Settings.PRINTER_DETAILS,
        arguments = listOf(navArgument("printerId") { type = NavType.StringType })
    ) { backStackEntry ->
        val printerId = backStackEntry.arguments?.getString("printerId") ?: ""
        PrinterDetailsScreen(
            navController = navController,
            printerId = printerId
        )
    }
}
```

## 📋 **最佳实践**

### 1. **使用统一路由常量**
```kotlin
// ✅ 好
navController.navigate(AppNavigation.Settings.PRINTER_SETTINGS)

// ❌ 差
navController.navigate("printer_settings")  // 硬编码字符串
```

### 2. **类型安全的参数传递**
```kotlin
// ✅ 好：使用帮助方法
navController.navigate(
    AppNavigation.Settings.printerDetailsRoute(printerId)
)

// ❌ 差：手动拼接字符串
navController.navigate("printer_details/$printerId")
```

### 3. **条件判断使用工具方法**
```kotlin
// ✅ 好：使用工具方法
if (AppNavigation.isSettingsRoute(currentRoute)) {
    // 处理设置页面
}

// ❌ 差：硬编码判断
if (currentRoute?.startsWith("printer_") == true || currentRoute == "website_settings") {
    // 处理设置页面
}
```

### 4. **添加新路由的步骤**

1. 在 `AppNavigation` 中添加常量：
```kotlin
object Settings {
    const val NEW_FEATURE_SETTINGS = "new_feature_settings"
}
```

2. 更新工具方法：
```kotlin
fun getSettingsRoutes(): List<String> {
    return listOf(
        // ... existing routes
        Settings.NEW_FEATURE_SETTINGS
    )
}
```

3. 在 NavHost 中添加对应的 composable

## 🎨 **项目特定配置**

### 底部导航栏
底部导航栏项目在 `NavigationItem.kt` 中定义，已经自动使用新的路由常量。

### 特殊页面检测
项目中的特殊页面（如设置页面）会隐藏底部导航栏，使用 `AppNavigation.isSettingsRoute()` 进行判断。

## 🚀 **未来扩展**

导航系统已经为未来功能预留了扩展空间：

- `AppNavigation.Features.*` - 新功能页面
- `AppNavigation.Templates.*` - 模板相关页面
- 可以轻松添加新的路由分组

这种设计确保了代码的可维护性和类型安全性。 