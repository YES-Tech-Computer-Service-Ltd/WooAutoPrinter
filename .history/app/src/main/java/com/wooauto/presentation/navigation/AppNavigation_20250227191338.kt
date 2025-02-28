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
            route = Screen.OrderDetails.route,
            arguments = listOf(
                navArgument("orderId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getLong("orderId") ?: return@composable
            OrderDetailsScreen(
                orderId = orderId,
                onNavigateBack = { navController.popBackStack() }
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
            route = Screen.ProductDetails.route,
            arguments = listOf(
                navArgument("productId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getLong("productId") ?: return@composable
            ProductDetailsScreen(
                productId = productId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 设置页面
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
} 