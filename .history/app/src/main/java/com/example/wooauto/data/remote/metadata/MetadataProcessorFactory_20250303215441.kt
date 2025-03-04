package com.example.wooauto.data.remote.metadata

/**
 * 元数据处理器工厂
 * 用于创建和注册不同类型的元数据处理器
 */
object MetadataProcessorFactory {
    
    /**
     * 创建并注册默认的处理器
     * @return 元数据处理器注册表实例
     */
    fun createDefaultRegistry(): MetadataProcessorRegistry {
        val registry = MetadataProcessorRegistry.getInstance()
        
        // 注册WooFood处理器
        registry.registerProcessor(WooFoodMetadataProcessor())
        
        // 在这里可以注册其他处理器
        
        return registry
    }
    
    /**
     * 为特定的插件类型创建处理器
     * @param pluginType 插件类型
     * @return 对应的元数据处理器
     */
    fun createProcessorForPlugin(pluginType: String): MetadataProcessor {
        return when (pluginType.lowercase()) {
            "woofood" -> WooFoodMetadataProcessor()
            // 未来可以添加其他插件的处理器
            else -> DefaultMetadataProcessor()
        }
    }
    
    /**
     * 根据元数据键猜测可能的处理器类型
     * @param key 元数据键
     * @return 可能的处理器
     */
    fun guessProcessorByKey(key: String): MetadataProcessor {
        // WooFood相关的键
        val woofoodKeys = listOf("exwfood_", "_woofood_", "CKB OR S/S PK", "_exoptions")
        if (woofoodKeys.any { key.contains(it) }) {
            return WooFoodMetadataProcessor()
        }
        
        // 未来可以添加其他插件的判断逻辑
        
        // 默认处理器
        return DefaultMetadataProcessor()
    }
} 