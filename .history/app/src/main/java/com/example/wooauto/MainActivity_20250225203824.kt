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
    private val preferencesManager = PreferencesManager(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                    
                    // 检查是否首次启动
                    lifecycleScope.launch {
                        if (preferencesManager.isFirstLaunch.first()) {
                            // 导航到网站设置页面
                            navController.navigate(Screen.WebsiteSetup.route)
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
                        }
                    }
                }
            }
        }
    }
}