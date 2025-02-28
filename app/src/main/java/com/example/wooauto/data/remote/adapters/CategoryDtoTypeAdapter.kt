package com.example.wooauto.data.remote.adapters

import com.example.wooauto.data.remote.dto.CategoryDto
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * CategoryDto的类型适配器
 * 专门处理CategoryDto的JSON解析，防止类型转换错误
 */
class CategoryDtoTypeAdapter : TypeAdapter<CategoryDto>() {
    override fun write(out: JsonWriter, value: CategoryDto?) {
        // 实现序列化逻辑（如果需要）
        // 当前实现中主要关注反序列化
    }

    override fun read(reader: JsonReader): CategoryDto? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }

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

        return CategoryDto(
            id = id,
            name = name,
            slug = slug,
            parent = parent,
            description = description,
            count = count
        )
    }
} 