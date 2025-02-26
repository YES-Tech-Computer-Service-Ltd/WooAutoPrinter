package com.example.wooauto

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.wooauto.databinding.ActivityMainBinding
import com.example.wooauto.ui.navigation.BottomNavBar
import com.example.wooauto.ui.navigation.WooAutoNavHost
import com.example.wooauto.utils.SharedPreferencesManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefsManager: SharedPreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefsManager = SharedPreferencesManager(this)

        // Check if first launch to show setup wizard
        if (prefsManager.isFirstLaunch()) {
            // TODO: Launch setup wizard Activity
            // For now, just mark as not first launch
            prefsManager.setFirstLaunch(false)
        }

        // 检查API凭证
        if (prefsManager.getApiKey().isEmpty() || prefsManager.getApiSecret().isEmpty()) {
            Toast.makeText(this, "请先设置WooCommerce API凭证", Toast.LENGTH_LONG).show()
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
}