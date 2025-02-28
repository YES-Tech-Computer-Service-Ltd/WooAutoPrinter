package com.wooauto.data.remote.adapters

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

class FlexibleTypeAdapter : TypeAdapter<Any>() {
    override fun write(out: JsonWriter, value: Any?) {
        // 实现写入逻辑（如果需要）
    }

    override fun read(reader: JsonReader): Any? {
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> readObject(reader)
            JsonToken.BEGIN_ARRAY -> readArray(reader)
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> {
                val numberStr = reader.nextString()
                if (numberStr.contains(".")) numberStr.toDouble() else numberStr.toLong()
            }
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            else -> throw IllegalStateException("Unexpected JSON token: ${reader.peek()}")
        }
    }

    private fun readObject(reader: JsonReader): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        reader.beginObject()
        while (reader.hasNext()) {
            map[reader.nextName()] = read(reader)
        }
        reader.endObject()
        return map
    }

    private fun readArray(reader: JsonReader): List<Any?> {
        val list = mutableListOf<Any?>()
        reader.beginArray()
        while (reader.hasNext()) {
            list.add(read(reader))
        }
        reader.endArray()
        return list
    }
}