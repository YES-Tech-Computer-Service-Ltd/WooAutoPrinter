package com.example.wooauto.data.remote.metadata

import com.example.wooauto.data.remote.dto.MetaDataDto
import com.example.wooauto.domain.models.ItemOption
import com.google.gson.stream.JsonReader

/**
 * WooFood元数据处理器
 * 专门用于处理WooFood插件的元数据
 */
class WooFoodMetadataProcessor : MetadataProcessor {
    override val processorId: String = "woofood"
    
    // WooFood相关的元数据前缀或关键字
    private val WOOFOOD_KEYS = listOf("exwfood_", "_woofood_")
    
    /**
     * 判断是否是WooFood相关的元数据
     */
    override fun canProcess(key: String): Boolean {
        return WOOFOOD_KEYS.any { prefix -> key.startsWith(prefix) } || 
               key == "CKB OR S/S PK" || // 特定的WooFood选项键
               key == "_exoptions" // WooFood选项列表
    }
    
    /**
     * 使用注册表的通用方法读取元数据
     */
    override fun readMetadata(reader: JsonReader): List<MetaDataDto> {
        return MetadataProcessorRegistry.getInstance().readMetadata(reader)
    }
    
    /**
     * 处理WooFood元数据
     * 根据不同的键进行不同的处理
     */
    override fun processMetadata(metadata: MetaDataDto): Any? {
        return when {
            metadata.key == "exwfood_order_method" -> {
                // 处理订单方法
                metadata.value?.toString()
            }
            metadata.key == "_exoptions" -> {
                // 处理选项列表
                processExOptions(metadata.value)
            }
            !metadata.key.isNullOrEmpty() && !metadata.key.startsWith("_") -> {
                // 处理普通选项
                ItemOption(name = metadata.key, value = metadata.value?.toString() ?: "")
            }
            else -> metadata.value
        }
    }
    
    /**
     * 处理WooFood的选项列表
     * @param value 选项列表的值，通常是一个列表或映射
     * @return 处理后的ItemOption列表
     */
    private fun processExOptions(value: Any?): List<ItemOption> {
        if (value == null) return emptyList()
        
        return when (value) {
            is List<*> -> {
                value.mapNotNull { option ->
                    if (option is Map<*, *>) {
                        val name = option["name"]?.toString()
                        val optionValue = option["value"]?.toString() ?: option["v"]?.toString()
                        
                        if (name != null && optionValue != null) {
                            ItemOption(name = name, value = optionValue)
                        } else null
                    } else null
                }
            }
            is Map<*, *> -> {
                value.entries.mapNotNull { entry ->
                    val key = entry.key?.toString()
                    val entryValue = entry.value?.toString()
                    
                    if (key != null && entryValue != null) {
                        ItemOption(name = key, value = entryValue)
                    } else null
                }
            }
            else -> emptyList()
        }
    }
} 