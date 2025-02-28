package com.example.wooauto.presentation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.example.wooauto.R
import com.example.wooauto.presentation.screens.products.ProductsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
    
    // 在MainActivity中添加一个lifecycleScope的方法，用于在resume时检查配置
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            // 检查产品视图模型是否需要刷新数据
            val navController = findNavController(R.id.nav_host_fragment)
            if (navController.currentDestination?.id == R.id.productsFragment) {
                val viewModel = ViewModelProvider(this@MainActivity)[ProductsViewModel::class.java]
                viewModel.checkAndRefreshConfig()
            }
        }
    }
} 