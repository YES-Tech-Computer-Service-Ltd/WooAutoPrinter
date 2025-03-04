package com.example.wooauto.data.remote.metadata

import com.example.wooauto.data.remote.dto.MetaDataDto
import com.google.gson.stream.JsonReader

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
     * 使用注册表的通用方法读取元数据
     */
    override fun readMetadata(reader: JsonReader): List<MetaDataDto> {
        return MetadataProcessorRegistry.getInstance().readMetadata(reader)
    }
    
    /**
     * 默认的元数据处理方式：直接返回原值
     */
    override fun processMetadata(metadata: MetaDataDto): Any? {
        return metadata.value
    }
} 