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