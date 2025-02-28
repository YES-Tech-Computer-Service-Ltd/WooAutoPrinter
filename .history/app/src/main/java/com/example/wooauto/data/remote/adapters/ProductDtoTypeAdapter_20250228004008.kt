package com.example.wooauto.data.remote.adapters

import com.example.wooauto.data.remote.dto.CategoryDto
import com.example.wooauto.data.remote.dto.ImageDto
import com.example.wooauto.data.remote.dto.ProductDto
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * ProductDto的类型适配器
 * 专门处理ProductDto的JSON解析，防止类型转换错误
 */
class ProductDtoTypeAdapter : TypeAdapter<ProductDto>() {
    override fun write(out: JsonWriter, value: ProductDto?) {
        // 实现序列化逻辑（如果需要）
        // 当前实现中主要关注反序列化
    }

    override fun read(reader: JsonReader): ProductDto? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }

        var id: Long = 0
        var name: String = ""
        var permalink: String = ""
        var description: String = ""
        var shortDescription: String = ""
        var sku: String = ""
        var price: String = ""
        var regularPrice: String = ""
        var salePrice: String = ""
        var onSale: Boolean = false
        var stockStatus: String = ""
        var stockQuantity: Int? = null
        var manageStock: Boolean = false
        var categories: List<CategoryDto>? = null
        var images: List<ImageDto> = emptyList()
        var status: String = ""

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextLong()
                "name" -> name = reader.nextString()
                "permalink" -> permalink = reader.nextString()
                "description" -> description = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); "" } else reader.nextString()
                "short_description" -> shortDescription = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); "" } else reader.nextString()
                "sku" -> sku = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); "" } else reader.nextString()
                "price" -> price = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); "0" } else reader.nextString()
                "regular_price" -> regularPrice = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); "0" } else reader.nextString()
                "sale_price" -> salePrice = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); "0" } else reader.nextString()
                "on_sale" -> onSale = reader.nextBoolean()
                "stock_status" -> stockStatus = reader.nextString()
                "stock_quantity" -> stockQuantity = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextInt()
                "manage_stock" -> manageStock = reader.nextBoolean()
                "categories" -> categories = readCategories(reader)
                "images" -> images = readImages(reader)
                "status" -> status = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return ProductDto(
            id = id,
            name = name,
            permalink = permalink,
            description = description,
            shortDescription = shortDescription,
            sku = sku,
            price = price,
            regularPrice = regularPrice,
            salePrice = salePrice,
            onSale = onSale,
            stockStatus = stockStatus,
            stockQuantity = stockQuantity,
            manageStock = manageStock,
            categories = categories,
            images = images,
            status = status
        )
    }

    private fun readCategories(reader: JsonReader): List<CategoryDto>? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }

        val categories = mutableListOf<CategoryDto>()
        reader.beginArray()
        while (reader.hasNext()) {
            var id: Long = 0
            var name: String = ""
            var slug: String = ""
            var parent: Long? = null
            var description: String = ""
            var count: Int = 0

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> id = reader.nextLong()
                    "name" -> name = reader.nextString()
                    "slug" -> slug = reader.nextString()
                    "parent" -> parent = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.nextLong()
                    "description" -> description = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); "" } else reader.nextString()
                    "count" -> count = reader.nextInt()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            categories.add(
                CategoryDto(
                    id = id,
                    name = name,
                    slug = slug,
                    parent = parent,
                    description = description,
                    count = count
                )
            )
        }
        reader.endArray()
        return categories
    }

    private fun readImages(reader: JsonReader): List<ImageDto> {
        val images = mutableListOf<ImageDto>()
        reader.beginArray()
        while (reader.hasNext()) {
            var id: Long = 0
            var src: String = ""

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> id = reader.nextLong()
                    "src" -> src = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            images.add(
                ImageDto(
                    id = id,
                    src = src
                )
            )
        }
        reader.endArray()
        return images
    }
} 