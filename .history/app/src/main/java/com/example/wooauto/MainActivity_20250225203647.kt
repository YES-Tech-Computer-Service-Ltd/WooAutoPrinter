package com.example.wooauto

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.wooauto.databinding.ActivityMainBinding
import com.example.wooauto.ui.navigation.BottomNavBar
import com.example.wooauto.ui.navigation.WooAutoNavHost
import com.example.wooauto.utils.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val preferencesManager = PreferencesManager(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查是否首次启动
        lifecycleScope.launch {
            if (preferencesManager.isFirstLaunch.first()) {
                // 显示设置向导
                startActivity(Intent(this@MainActivity, SetupWizardActivity::class.java))
                finish()
                return@launch
            }

            // 检查API凭证
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

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置 Toolbar
        setSupportActionBar(binding.toolbar)

        // 设置 Compose 内容
        binding.composeView.setContent {
            val navController = rememberNavController()

            Scaffold(
                bottomBar = { BottomNavBar(navController) }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    WooAutoNavHost(navController)
                }
            }
        }
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("配置提醒")
            .setMessage("请先完成WooCommerce API配置才能使用应用")
            .setPositiveButton("去设置") { _, _ ->
                // 跳转到设置页面
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton("稍后") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
}