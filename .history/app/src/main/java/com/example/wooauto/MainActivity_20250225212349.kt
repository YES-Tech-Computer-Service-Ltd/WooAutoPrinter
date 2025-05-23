package com.example.wooauto

import android.content.Context
import android.content.res.Configuration
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
import com.example.wooauto.utils.LanguageHelper
import com.example.wooauto.utils.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager

    override fun attachBaseContext(newBase: Context) {
        // 在 Activity 创建前设置语言
        val tempPrefsManager = PreferencesManager(newBase)
        val languageCode = runBlocking { tempPrefsManager.language.value }
        val context = LanguageHelper.updateBaseContextLocale(newBase, languageCode)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化 PreferencesManager
        preferencesManager = PreferencesManager(this)

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
                            WooAutoNavHost(navController = navController)
                        }
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 配置变更时保持语言设置
        runBlocking {
            val languageCode = preferencesManager.language.value
            LanguageHelper.setLocale(this@MainActivity, languageCode)
        }
    }
}