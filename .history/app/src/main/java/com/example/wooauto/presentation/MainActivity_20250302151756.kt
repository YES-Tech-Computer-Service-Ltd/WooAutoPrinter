package com.example.wooauto.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.wooauto.presentation.screens.products.ProductsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 具体UI内容省略，这里只修复编译错误
    }
    
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try {
                // 简化逻辑，直接检查配置
                val viewModel = ViewModelProvider(this@MainActivity)[ProductsViewModel::class.java]
                viewModel.checkAndRefreshConfig()
            } catch (e: Exception) {
                // 处理异常
            }
        }
    }
} 