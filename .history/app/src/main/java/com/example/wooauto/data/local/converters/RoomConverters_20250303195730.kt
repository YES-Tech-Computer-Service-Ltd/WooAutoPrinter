package com.example.wooauto.data.local.converters

import androidx.room.TypeConverter
import com.example.wooauto.data.local.entities.CategoryEntity
import com.example.wooauto.data.local.entities.OrderItemEntity
import com.example.wooauto.data.local.entities.OrderLineItemEntity
import com.example.wooauto.data.local.entities.ProductAttributeEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

/**
 * Room数据库类型转换器
 * 用于转换复杂类型和Room支持的基本类型之间的转换
 */
class RoomConverters {
    private val gson = Gson()

    // Date转换
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    // String列表转换
    @TypeConverter
    fun fromString(value: String?): List<String> {
        if (value == null || value.isBlank()) return emptyList()
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromStringList(list: List<String>): String {
        return gson.toJson(list)
    }

    // Long列表转换
    @TypeConverter
    fun fromLongList(value: String?): List<Long> {
        if (value == null || value.isBlank()) return emptyList()
        val listType = object : TypeToken<List<Long>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun toLongList(list: List<Long>): String {
        return gson.toJson(list)
    }

    // OrderItem列表转换
    @TypeConverter
    fun fromOrderItems(value: String?): List<OrderItemEntity> {
        if (value == null || value.isBlank()) return emptyList()
        val listType = object : TypeToken<List<OrderItemEntity>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun toOrderItems(list: List<OrderItemEntity>): String {
        return gson.toJson(list)
    }

    // ProductAttribute列表转换
    @TypeConverter
    fun fromProductAttributes(value: String?): List<ProductAttributeEntity>? {
        if (value == null) return null
        val listType = object : TypeToken<List<ProductAttributeEntity>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun toProductAttributes(list: List<ProductAttributeEntity>?): String? {
        return if (list == null) null else gson.toJson(list)
    }

    // CategoryEntity列表转换
    @TypeConverter
    fun fromCategoryList(categories: List<CategoryEntity>): String {
        return gson.toJson(categories)
    }
    
    @TypeConverter
    fun toCategoryList(categoriesString: String): List<CategoryEntity> {
        if (categoriesString.isBlank()) return emptyList()
        val type = object : TypeToken<List<CategoryEntity>>() {}.type
        return gson.fromJson(categoriesString, type)
    }

    // 图片列表转换
    @TypeConverter
    fun fromImageList(images: List<String>): String {
        return gson.toJson(images)
    }
    
    @TypeConverter
    fun toImageList(imagesString: String): List<String> {
        if (imagesString.isBlank()) return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(imagesString, type)
    }

    // 订单行项目列表转换
    @TypeConverter
    fun fromLineItemList(lineItems: List<OrderLineItemEntity>): String {
        return gson.toJson(lineItems)
    }
    
    @TypeConverter
    fun toLineItemList(lineItemsString: String): List<OrderLineItemEntity> {
        if (lineItemsString.isBlank()) return emptyList()
        val type = object : TypeToken<List<OrderLineItemEntity>>() {}.type
        return gson.fromJson(lineItemsString, type)
    }
} 