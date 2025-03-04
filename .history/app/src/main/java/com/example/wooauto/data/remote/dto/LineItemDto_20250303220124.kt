package com.example.wooauto.data.remote.dto

import android.util.Log
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
    val options = try {
        // 获取元数据处理器注册表
        val metadataRegistry = MetadataProcessorRegistry.getInstance()
        
        // 处理元数据并提取选项
        if (metaData != null && metaData.isNotEmpty()) {
            val processedData = metadataRegistry.processMetadata(metaData)
            
            // 收集直接处理后的ItemOption对象
            val directOptions = processedData.values.filterIsInstance<ItemOption>()
            
            // 处理可能是ItemOption列表的值
            val listOptions = processedData.values.filterIsInstance<List<*>>().flatMap { list ->
                list.filterIsInstance<ItemOption>()
            }
            
            // 合并两种类型的选项
            directOptions + listOptions
        } else {
            // 如果没有元数据，返回空列表
            emptyList()
        }
    } catch (e: Exception) {
        // 发生错误时记录日志并返回空列表
        Log.e("LineItemDto", "处理元数据时出错: ${e.message}", e)
        
        // 降级处理：使用简单方式提取选项
        metaData?.mapNotNull { meta ->
            if (meta.key != null && !meta.key.startsWith("_") && meta.value != null) {
                ItemOption(name = meta.key, value = meta.value.toString())
            } else {
                null
            }
        } ?: emptyList()
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