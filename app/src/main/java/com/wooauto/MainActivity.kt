package com.wooauto

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.wooauto.presentation.navigation.AppNavigation
import com.wooauto.domain.repositories.DomainSettingRepository
import com.wooauto.ui.theme.WooAutoTheme
import com.wooauto.utils.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingRepository: DomainSettingRepository

    override fun attachBaseContext(newBase: Context) {
        // 在Activity创建时获取保存的语言设置
        val language = runBlocking {
            settingRepository.getLanguageFlow().first()
        }
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 监听语言设置的变化
        lifecycleScope.launch {
            settingRepository.getLanguageFlow().collect { language ->
                val context = LocaleHelper.setLocale(this@MainActivity, language)
                resources.updateConfiguration(
                    context.resources.configuration,
                    context.resources.displayMetrics
                )
                // 重新创建Activity以应用新的语言设置
                recreate()
            }
        }

        setContent {
            WooAutoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavigation(navController = navController)
                }
            }
        }
    }
} 