package com.example.wooauto.data.remote.dto

import com.example.wooauto.domain.models.ItemOption
import com.example.wooauto.domain.models.OrderItem
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

data class MetaDataDto(
    val id: Long?,
    val key: String?,
    val value: Any?
)

data class ImageDto(
    val id: Long?,
    val src: String?
)

fun LineItemDto.toOrderItem(): OrderItem {
    // 从元数据中提取选项
    val options = metaData?.mapNotNull { meta ->
        // 排除特殊的元数据键
        if (meta.key != null && !meta.key.startsWith("_") && meta.value != null) {
            ItemOption(name = meta.key, value = meta.value.toString())
        } else {
            null
        }
    } ?: emptyList()
    
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