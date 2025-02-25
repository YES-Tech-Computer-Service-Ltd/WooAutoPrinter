package com.example.wooauto.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.wooauto.R
import com.example.wooauto.ui.orders.OrderDetailsScreen
import com.example.wooauto.ui.orders.OrdersScreen
import com.example.wooauto.ui.products.ProductDetailsScreen
import com.example.wooauto.ui.products.ProductsScreen
import com.example.wooauto.ui.settings.SettingsScreen
import com.example.wooauto.ui.settings.printer.PrinterSetupScreen
import com.example.wooauto.ui.screens.settings.sound.SoundSetupScreen
import com.example.wooauto.ui.settings.website.WebsiteSetupScreen

// Navigation route constants
sealed class Screen(val route: String) {
    object Orders : Screen("orders")
    object OrderDetails : Screen("order_details/{orderId}") {
        fun createRoute(orderId: Long) = "order_details/$orderId"
    }

    object Products : Screen("products")
    object ProductDetails : Screen("product_details/{productId}") {
        fun createRoute(productId: Long) = "product_details/$productId"
    }

    object Settings : Screen("settings")
    object PrinterSetup : Screen("printer_setup")
    object WebsiteSetup : Screen("website_setup")
    object SoundSetup : Screen("sound_setup")
}

// Bottom navigation items
private val bottomNavItems = listOf(
    BottomNavItem.Orders,
    BottomNavItem.Products,
    BottomNavItem.Settings
)

// Bottom navigation item model
sealed class BottomNavItem(
    val route: String,
    val titleResId: Int,
    val iconResId: Int
) {
    object Orders : BottomNavItem(
        route = Screen.Orders.route,
        titleResId = R.string.orders,
        iconResId = R.drawable.ic_order
    )

    object Products : BottomNavItem(
        route = Screen.Products.route,
        titleResId = R.string.products,
        iconResId = R.drawable.ic_product
    )

    object Settings : BottomNavItem(
        route = Screen.Settings.route,
        titleResId = R.string.settings,
        iconResId = R.drawable.ic_settings
    )
}

// Bottom navigation bar
@Composable
fun BottomNavBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Don't show bottom bar on detail screens
    if (currentRoute == null ||
        currentRoute.startsWith("order_details") ||
        currentRoute.startsWith("product_details") ||
        currentRoute == Screen.PrinterSetup.route ||
        currentRoute == Screen.WebsiteSetup.route ||
        currentRoute == Screen.SoundSetup.route) {
        return
    }

    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        painter = painterResource(id = item.iconResId),
                        contentDescription = stringResource(id = item.titleResId)
                    )
                },
                label = { Text(stringResource(id = item.titleResId)) },
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            // Pop up to the start destination of the graph to
                            // avoid building up a large stack of destinations
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            // Avoid multiple copies of the same destination when
                            // reselecting the same item
                            launchSingleTop = true
                            // Restore state when reselecting a previously selected item
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}

// Main navigation host
@Composable
fun WooAutoNavHost(navController: NavHostController) {
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
                onSoundSetupClick = { navController.navigate(Screen.SoundSetup.route) }
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

        composable(Screen.SoundSetup.route) {
            SoundSetupScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}