package com.example.wooauto

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.wooauto.ui.navigation.BottomNavBar
import com.example.wooauto.ui.navigation.Screen
import com.example.wooauto.ui.navigation.WooAutoNavHost
import com.example.wooauto.ui.theme.WooAutoTheme
import com.example.wooauto.utils.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化 PreferencesManager
        preferencesManager = PreferencesManager(this)

        // 检查API凭证
        lifecycleScope.launch {
            val websiteUrl = preferencesManager.websiteUrl.first()
            val apiKey = preferencesManager.apiKey.first()
            val apiSecret = preferencesManager.apiSecret.first()

            if (websiteUrl.isEmpty() || apiKey.isEmpty() || apiSecret.isEmpty()) {
                Toast.makeText(
                    this@MainActivity,
                    "请先在设置中配置WooCommerce API凭证",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        setContent {
            WooAutoTheme {
                Surface(
                    modifier = Modifier,
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    var shouldNavigateToSetup by remember { mutableStateOf(false) }
                    
                    // 检查是否首次启动
                    LaunchedEffect(Unit) {
                        if (preferencesManager.isFirstLaunch.first()) {
                            shouldNavigateToSetup = true
                        }
                    }

                    Scaffold(
                        bottomBar = {
                            BottomNavBar(navController = navController)
                        }
                    ) { paddingValues ->
                        Box(
                            modifier = Modifier.padding(paddingValues)
                        ) {
                            WooAutoNavHost(navController = navController)
                            
                            // 在导航图设置完成后执行导航
                            LaunchedEffect(shouldNavigateToSetup) {
                                if (shouldNavigateToSetup) {
                                    navController.navigate(Screen.WebsiteSetup.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            inclusive = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}