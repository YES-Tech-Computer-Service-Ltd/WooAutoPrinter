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
import androidx.compose.runtime.collectAsState
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
        // 在 Activity 创建前使用同步方法获取默认语言
        val tempPrefsManager = PreferencesManager(newBase)
        val languageCode = tempPrefsManager.getDefaultLanguage()
        val context = LanguageHelper.updateBaseContextLocale(newBase, languageCode)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化 PreferencesManager
        preferencesManager = PreferencesManager(this)

        // 确保当前语言设置正确
        lifecycleScope.launch {
            val currentLanguage = preferencesManager.language.first()
            LanguageHelper.setLocale(this@MainActivity, currentLanguage)
        }

        setContent {
            WooAutoTheme {
                Surface(
                    modifier = Modifier,
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    var shouldShowBottomBar by remember { mutableStateOf(true) }

                    // 监听语言变化
                    val language by preferencesManager.language.collectAsState(initial = "")
                    
                    // 只在语言真正发生变化时重启 Activity
                    var previousLanguage by remember { mutableStateOf(language) }
                    LaunchedEffect(language) {
                        if (language.isNotEmpty() && language != previousLanguage) {
                            previousLanguage = language
                            // 语言发生变化时重新创建 Activity
                            val intent = intent
                            finish()
                            startActivity(intent)
                            overridePendingTransition(0, 0)
                        }
                    }

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
        lifecycleScope.launch {
            val languageCode = preferencesManager.language.first()
            LanguageHelper.setLocale(this@MainActivity, languageCode)
        }
    }
}