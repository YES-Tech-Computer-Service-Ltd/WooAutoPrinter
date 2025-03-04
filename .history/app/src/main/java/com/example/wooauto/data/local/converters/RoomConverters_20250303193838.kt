package com.example.wooauto.data.local.converters

import androidx.room.TypeConverter
import com.example.wooauto.data.local.entities.CategoryEntity
import com.example.wooauto.data.local.entities.OrderLineItemEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

/**
 * 类别列表转换器
 */
class CategoryListConverter {
    private val gson = Gson()
    
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
}

/**
 * 图片列表转换器
 */
class ImageListConverter {
    private val gson = Gson()
    
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
}

/**
 * 订单行项目列表转换器
 */
class LineItemListConverter {
    private val gson = Gson()
    
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

/**
 * Date类型转换器
 */
class DateConverter {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
} 