package com.example.wooauto.data.remote.metadata

import com.example.wooauto.data.remote.dto.MetaDataDto
import com.google.gson.stream.JsonReader

/**
 * 元数据处理器接口
 * 用于处理不同插件的元数据
 */
interface MetadataProcessor {
    /**
     * 处理器ID，用于标识处理器类型
     */
    val processorId: String
    
    /**
     * 判断是否可以处理指定的元数据
     * @param key 元数据的键
     * @return 是否可以处理
     */
    fun canProcess(key: String): Boolean
    
    /**
     * 从JsonReader中读取元数据列表
     * @param reader JsonReader对象
     * @return 解析后的元数据列表
     */
    fun readMetadata(reader: JsonReader): List<MetaDataDto>
    
    /**
     * 处理单个元数据项
     * @param metadata 元数据项
     * @return 处理后的结果，可以是任何类型
     */
    fun processMetadata(metadata: MetaDataDto): Any?
} 