package com.example.wooauto.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.wooauto.ui.orders.OrderDetailsScreen
import com.example.wooauto.ui.orders.OrdersScreen
import com.example.wooauto.ui.products.ProductDetailsScreen
import com.example.wooauto.ui.products.ProductsScreen
import com.example.wooauto.ui.settings.SettingsScreen
import com.example.wooauto.ui.settings.printer.PrinterSetupScreen
import com.example.wooauto.ui.settings.website.WebsiteSetupScreen

@Composable
fun WooAutoNavHost(
    navController: NavHostController,
    onLanguageChanged: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Orders.route
    ) {
        // Orders screens
        composable(Screen.Orders.route) {
            OrdersScreen(
                onOrderClick = { orderId ->
                    navController.navigate(Screen.OrderDetails.createRoute(orderId))
                }
            )
        }

        composable(Screen.OrderDetails.route) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId")?.toLongOrNull() ?: 0L
            OrderDetailsScreen(
                orderId = orderId,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Products screens
        composable(Screen.Products.route) {
            ProductsScreen(
                onProductClick = { productId ->
                    navController.navigate(Screen.ProductDetails.createRoute(productId))
                }
            )
        }

        composable(Screen.ProductDetails.route) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId")?.toLongOrNull() ?: 0L
            ProductDetailsScreen(
                productId = productId,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Settings screens
        composable(Screen.Settings.route) {
            SettingsScreen(
                onPrinterSetupClick = { navController.navigate(Screen.PrinterSetup.route) },
                onWebsiteSetupClick = { navController.navigate(Screen.WebsiteSetup.route) },
                onLanguageChanged = onLanguageChanged
            )
        }

        composable(Screen.PrinterSetup.route) {
            PrinterSetupScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.WebsiteSetup.route) {
            WebsiteSetupScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
} 