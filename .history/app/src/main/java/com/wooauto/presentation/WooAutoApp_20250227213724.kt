package com.wooauto.presentation

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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wooauto.presentation.components.WooAppBar
import com.wooauto.presentation.screens.home.HomeScreen
import com.wooauto.presentation.theme.WooAutoTheme

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
        topBar = { WooAppBar() }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("home") {
                HomeScreen(navController = navController)
            }
            // 可以在这里添加更多的导航目标
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