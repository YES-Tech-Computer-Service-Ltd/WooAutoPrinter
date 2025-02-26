package com.example.wooauto

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.wooauto.databinding.ActivityMainBinding
import com.example.wooauto.utils.SharedPreferencesManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
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

        // 首先设置 Toolbar
        setSupportActionBar(binding.toolbar)

        // 然后设置 Navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // 设置 ActionBar
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.ordersFragment,
                R.id.productsFragment,
                R.id.settingsFragment
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        // 最后设置底部导航
        binding.bottomNavigation.setupWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}