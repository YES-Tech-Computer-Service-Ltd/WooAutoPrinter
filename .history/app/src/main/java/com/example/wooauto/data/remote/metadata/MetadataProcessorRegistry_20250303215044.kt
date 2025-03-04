package com.example.wooauto.data.remote.metadata

import com.example.wooauto.data.remote.dto.MetaDataDto
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken

/**
 * 元数据处理器注册表
 * 用于管理和查找不同的元数据处理器
 */
class MetadataProcessorRegistry {
    private val processors = mutableListOf<MetadataProcessor>()
    
    /**
     * 注册元数据处理器
     * @param processor 要注册的处理器
     */
    fun registerProcessor(processor: MetadataProcessor) {
        processors.add(processor)
    }
    
    /**
     * 根据元数据键查找合适的处理器
     * @param key 元数据的键
     * @return 处理器，如果没有找到则返回默认处理器
     */
    fun findProcessor(key: String): MetadataProcessor {
        return processors.find { it.canProcess(key) } ?: DefaultMetadataProcessor()
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
        
        return metaDataList
    }
    
    /**
     * 处理元数据列表
     * @param metaDataList 元数据列表
     * @return 处理后的结果，每个元数据都会由对应的处理器处理
     */
    fun processMetadata(metaDataList: List<MetaDataDto>): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        
        metaDataList.forEach { metaData ->
            metaData.key?.let { key ->
                val processor = findProcessor(key)
                result[key] = processor.processMetadata(metaData)
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
    
    companion object {
        // 单例实例
        private val instance = MetadataProcessorRegistry()
        
        fun getInstance(): MetadataProcessorRegistry = instance
    }
} 