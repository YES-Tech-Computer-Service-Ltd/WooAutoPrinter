package com.example.wooauto.data.remote.metadata

import com.example.wooauto.data.remote.dto.MetaDataDto
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 元数据处理器注册表
 * 用于管理和查找不同的元数据处理器
 */
class MetadataProcessorRegistry {
    private val processors = mutableListOf<MetadataProcessor>()
    private val isInitialized = AtomicBoolean(false)
    
    /**
     * 注册元数据处理器
     * @param processor 要注册的处理器
     */
    fun registerProcessor(processor: MetadataProcessor) {
        // 防止重复注册
        if (processors.none { it.processorId == processor.processorId }) {
            processors.add(processor)
            Log.d("MetadataProcessorRegistry", "注册处理器: ${processor.processorId}")
        }
    }
    
    /**
     * 根据元数据键查找合适的处理器
     * @param key 元数据的键
     * @return 处理器，如果没有找到则返回默认处理器
     */
    fun findProcessor(key: String): MetadataProcessor {
        ensureInitialized()
        
        val processor = processors.find { it.canProcess(key) }
        if (processor == null) {
            // 如果没有找到合适的处理器，创建一个新的默认处理器
            val defaultProcessor = DefaultMetadataProcessor()
            // 不需要注册，因为这只是一个临时的处理器
            return defaultProcessor
        }
        return processor
    }
    
    /**
     * 确保注册表已经初始化
     * 懒加载方式，只在首次使用时初始化
     */
    private fun ensureInitialized() {
        if (!isInitialized.get()) {
            synchronized(this) {
                if (!isInitialized.get()) {
                    initialize()
                    isInitialized.set(true)
                }
            }
        }
    }
    
    /**
     * 从JsonReader中读取元数据列表
     * 这是一个通用方法，用于从JSON中解析元数据列表
     * @param reader JsonReader对象
     * @return 解析后的元数据列表
     */
    fun readMetadata(reader: JsonReader): List<MetaDataDto> {
        val metaDataList = mutableListOf<MetaDataDto>()
        
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return emptyList()
        }
        
        try {
            reader.beginArray()
            while (reader.hasNext()) {
                var id: Long? = null
                var key: String? = null
                var value: Any? = null
                
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "id" -> id = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextLong()
                        "key" -> key = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                        "value" -> value = readValue(reader)
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                
                if (key != null) {
                    metaDataList.add(MetaDataDto(id, key, value))
                }
            }
            reader.endArray()
        } catch (e: Exception) {
            Log.e("MetadataProcessorRegistry", "解析元数据时出错: ${e.message}", e)
        }
        
        return metaDataList
    }
    
    /**
     * 处理元数据列表
     * @param metaDataList 元数据列表
     * @return 处理后的结果，每个元数据都会由对应的处理器处理
     */
    fun processMetadata(metaDataList: List<MetaDataDto>): Map<String, Any?> {
        ensureInitialized()
        
        val result = mutableMapOf<String, Any?>()
        
        metaDataList.forEach { metaData ->
            try {
                metaData.key?.let { key ->
                    val processor = findProcessor(key)
                    result[key] = processor.processMetadata(metaData)
                }
            } catch (e: Exception) {
                Log.e("MetadataProcessorRegistry", "处理元数据时出错: ${e.message}", e)
            }
        }
        
        return result
    }
    
    /**
     * 从JsonReader中读取值
     * 可以处理不同类型的值（字符串、数字、布尔、对象等）
     * @param reader JsonReader对象
     * @return 读取的值
     */
    private fun readValue(reader: JsonReader): Any? {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> {
                val stringValue = reader.nextString()
                // 尝试转换为适当的数字类型
                stringValue.toDoubleOrNull() ?: stringValue.toIntOrNull() ?: stringValue
            }
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.BEGIN_ARRAY -> {
                val list = mutableListOf<Any?>()
                reader.beginArray()
                while (reader.hasNext()) {
                    list.add(readValue(reader))
                }
                reader.endArray()
                list
            }
            JsonToken.BEGIN_OBJECT -> {
                val map = mutableMapOf<String, Any?>()
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    map[name] = readValue(reader)
                }
                reader.endObject()
                map
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }
    
    /**
     * 初始化注册表
     * 注册默认的处理器
     */
    fun initialize() {
        Log.d("MetadataProcessorRegistry", "初始化元数据处理器注册表")
        
        // 注册默认处理器
        registerProcessor(DefaultMetadataProcessor())
        
        // 注册WooFood处理器
        registerProcessor(WooFoodMetadataProcessor())
        
        isInitialized.set(true)
    }
    
    companion object {
        // 单例实例，使用双重检查锁定
        @Volatile
        private var instance: MetadataProcessorRegistry? = null
        
        fun getInstance(): MetadataProcessorRegistry {
            return instance ?: synchronized(this) {
                instance ?: MetadataProcessorRegistry().also { instance = it }
            }
        }
    }
} 