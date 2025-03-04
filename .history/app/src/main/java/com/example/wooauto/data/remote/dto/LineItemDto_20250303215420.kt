package com.example.wooauto.data.remote.dto

import com.example.wooauto.domain.models.ItemOption
import com.example.wooauto.domain.models.OrderItem
import com.example.wooauto.data.remote.metadata.MetadataProcessorRegistry
import com.example.wooauto.data.remote.metadata.WooFoodMetadataProcessor
import com.google.gson.annotations.SerializedName

data class LineItemDto(
    val id: Long,
    val name: String,
    @SerializedName("product_id")
    val productId: Long,
    val quantity: Int,
    val subtotal: String?,
    val total: String?,
    val price: Double?,
    @SerializedName("meta_data")
    val metaData: List<MetaDataDto>? = null,
    val image: ImageDto? = null
)

fun LineItemDto.toOrderItem(): OrderItem {
    // 获取元数据处理器注册表
    val metadataRegistry = MetadataProcessorRegistry.getInstance()
    
    // 确保WooFood处理器已经注册
    if (metadataRegistry.findProcessor("exwfood_order_method").processorId != "woofood") {
        metadataRegistry.registerProcessor(WooFoodMetadataProcessor())
    }
    
    // 处理元数据并提取选项
    val options = if (metaData != null && metaData.isNotEmpty()) {
        val processedData = metadataRegistry.processMetadata(metaData)
        
        // 收集所有处理后的ItemOption对象
        processedData.values.filterIsInstance<ItemOption>() +
        // 处理可能是ItemOption列表的值
        processedData.values.filterIsInstance<List<*>>().flatMap { list ->
            list.filterIsInstance<ItemOption>()
        }
    } else {
        emptyList()
    }
    
    return OrderItem(
        id = id,
        productId = productId,
        name = name,
        quantity = quantity,
        price = price?.toString() ?: "0.0",
        subtotal = subtotal ?: "0.0",
        total = total ?: "0.0",
        image = image?.src ?: "",
        options = options
    )
} 