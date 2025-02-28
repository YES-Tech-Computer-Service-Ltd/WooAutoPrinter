package com.wooauto.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.wooauto.presentation.screens.orders.OrderDetailsScreen
import com.wooauto.presentation.screens.orders.OrdersScreen
import com.wooauto.presentation.screens.products.ProductDetailsScreen
import com.wooauto.presentation.screens.products.ProductsScreen
import com.wooauto.presentation.screens.settings.SettingsScreen

/**
 * 应用导航图组件
 *
 * @param navController 导航控制器
 * @param startDestination 起始目的地
 */
@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String = Screen.Orders.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // 订单列表页面
        composable(Screen.Orders.route) {
            OrdersScreen(
                onOrderClick = { orderId ->
                    navController.navigate(Screen.OrderDetails.createRoute(orderId))
                }
            )
        }

        // 订单详情页面
        composable(
            route = "orders/{orderId}",
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId") ?: return@composable
            OrderDetailsScreen(
                orderId = orderId,
                onNavigateBack = { navController.navigateUp() }
            )
        }

        // 产品列表页面
        composable(Screen.Products.route) {
            ProductsScreen(
                onProductClick = { productId ->
                    navController.navigate(Screen.ProductDetails.createRoute(productId))
                }
            )
        }

        // 产品详情页面
        composable(
            route = "products/{productId}",
            arguments = listOf(navArgument("productId") { type = NavType.StringType })
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId") ?: return@composable
            ProductDetailsScreen(
                productId = productId,
                onNavigateBack = { navController.navigateUp() }
            )
        }

        // 设置页面
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToWebsiteSetup = { navController.navigate("website_setup") },
                onNavigateToPrinterSetup = { navController.navigate("printer_setup") },
                onNavigateToSoundSetup = { navController.navigate("sound_setup") },
                onNavigateToLanguageSetup = { navController.navigate("language_setup") },
                onNavigateToAbout = { navController.navigate("about") }
            )
        }
    }
} 