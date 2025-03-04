package com.example.wooauto.data.remote.metadata

import com.example.wooauto.data.remote.dto.MetaDataDto
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken

/**
 * 默认元数据处理器
 * 用于处理通用的元数据
 */
class DefaultMetadataProcessor : MetadataProcessor {
    override val processorId: String = "default"
    
    /**
     * 默认处理器可以处理任何元数据
     */
    override fun canProcess(key: String): Boolean = true
    
    /**
     * 读取元数据列表
     * 提供自己的实现，而不是依赖MetadataProcessorRegistry，避免循环依赖
     */
    override fun readMetadata(reader: JsonReader): List<MetaDataDto> {
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
     * 默认的元数据处理方式：直接返回原值
     */
    override fun processMetadata(metadata: MetaDataDto): Any? {
        return metadata.value
    }
    
    /**
     * 从JsonReader中读取值
     * 可以处理不同类型的值（字符串、数字、布尔、对象等）
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
} 