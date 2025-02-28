package com.example.wooauto.wooauto.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.wooauto.wooauto.presentation.components.WooAppBar
import com.example.wooauto.wooauto.presentation.components.WooBottomNavigation
import com.example.wooauto.presentation.navigation.NavigationItem
import com.example.wooauto.wooauto.presentation.navigation.Screen
import com.example.wooauto.wooauto.presentation.screens.home.HomeScreen
import com.example.wooauto.wooauto.presentation.screens.orders.OrdersScreen
import com.example.wooauto.wooauto.presentation.screens.products.ProductsScreen
import com.example.wooauto.wooauto.presentation.screens.settings.SettingsScreen
import com.example.wooauto.wooauto.presentation.theme.WooAutoTheme

class WooAutoApp {
    companion object {
        @Composable
        fun getTheme(content: @Composable () -> Unit) {
            WooAutoTheme {
                content()
            }
        }

        @Composable
        fun getContent() = AppContent()
    }
}

@Composable
fun AppContent() {
    val navController = rememberNavController()
    
    Scaffold(
        topBar = { WooAppBar(navController = navController) },
        bottomBar = { WooBottomNavigation(navController = navController) }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = NavigationItem.Orders.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(NavigationItem.Orders.route) {
                OrdersScreen(navController = navController)
            }
            
            composable(NavigationItem.Products.route) {
                ProductsScreen()
            }
            
            composable(NavigationItem.Settings.route) {
                SettingsScreen()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    WooAutoTheme {
        AppContent()
    }
} 