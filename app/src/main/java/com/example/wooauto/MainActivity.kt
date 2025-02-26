package com.example.wooauto

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.wooauto.service.BackgroundPollingService
import com.example.wooauto.ui.navigation.BottomNavBar
import com.example.wooauto.ui.navigation.WooAutoNavHost
import com.example.wooauto.ui.theme.WooAutoTheme
import com.example.wooauto.utils.LanguageHelper
import com.example.wooauto.utils.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager

    override fun attachBaseContext(newBase: Context) {
        val languageCode = PreferencesManager.getStoredLanguage(newBase)
        val context = LanguageHelper.updateBaseContextLocale(newBase, languageCode)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferencesManager = PreferencesManager(this)

        // 检查并启动轮询服务
        lifecycleScope.launch {
            val apiKey = preferencesManager.apiKey.first()
            val apiSecret = preferencesManager.apiSecret.first()

            if (apiKey.isNotEmpty() && apiSecret.isNotEmpty()) {
                BackgroundPollingService.startService(this@MainActivity)
            }
        }

        setContent {
            WooAutoTheme {
                Surface(
                    modifier = Modifier,
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    var shouldShowBottomBar by remember { mutableStateOf(true) }

                    Scaffold(
                        bottomBar = {
                            if (shouldShowBottomBar) {
                                BottomNavBar(navController = navController)
                            }
                        }
                    ) { paddingValues ->
                        Box(
                            modifier = Modifier.padding(paddingValues)
                        ) {
                            WooAutoNavHost(
                                navController = navController,
                                onLanguageChanged = { 
                                    // 重新创建 Activity 以确保语言切换完全生效
                                    recreate()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        lifecycleScope.launch {
            val languageCode = preferencesManager.language.first()
            LanguageHelper.setLocale(this@MainActivity, languageCode)
            // 重新创建 Activity 以确保配置更改生效
            recreate()
        }
    }
}