package com.example.wooauto.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.wooauto.presentation.navigation.AppNavigation
import com.example.wooauto.presentation.screens.products.ProductsViewModel
import com.example.wooauto.presentation.theme.WooAutoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var navController: NavController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val tempNavController = rememberNavController()
            navController = tempNavController
            
            WooAutoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(navController = tempNavController)
                }
            }
        }
    }
    
    // 在MainActivity中添加一个lifecycleScope的方法，用于在resume时检查配置
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            // 检查产品视图模型是否在当前页面
            navController?.let { navController ->
                val currentRoute = navController.currentDestination?.route
                if (currentRoute?.contains("products", ignoreCase = true) == true) {
                    val viewModel = ViewModelProvider(this@MainActivity)[ProductsViewModel::class.java]
                    viewModel.checkAndRefreshConfig()
                }
            }
        }
    }
} 