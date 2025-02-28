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
        name = name,
        quantity = quantity,
        price = price ?: "0.0",
        subtotal = subtotal ?: "0.0",
        total = total ?: "0.0",
        image = image?.src ?: "",
        options = options
    )
} 