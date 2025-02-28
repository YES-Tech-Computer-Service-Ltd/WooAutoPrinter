package com.example.wooauto.wooauto.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.wooauto.data.local.dao.OrderDao
import com.wooauto.data.local.dao.ProductDao
import com.wooauto.data.local.dao.SettingDao
import com.wooauto.data.local.entities.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

/**
 * 应用数据库类
 * 定义了数据库的配置和转换器
 */
@Database(
    entities = [
        OrderEntity::class,
        ProductEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun productDao(): ProductDao
    abstract fun settingDao(): SettingDao
}

/**
 * 类型转换器
 * 用于转换复杂类型和Room支持的基本类型之间的转换
 */
class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromString(value: String?): List<String> {
        if (value == null) return emptyList()
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromStringList(list: List<String>): String {
        return gson.toJson(list)
    }

    @TypeConverter
    fun fromLongList(value: String?): List<Long> {
        if (value == null) return emptyList()
        val listType = object : TypeToken<List<Long>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun toLongList(list: List<Long>): String {
        return gson.toJson(list)
    }

    @TypeConverter
    fun fromOrderItems(value: String?): List<OrderItemEntity> {
        if (value == null) return emptyList()
        val listType = object : TypeToken<List<OrderItemEntity>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun toOrderItems(list: List<OrderItemEntity>): String {
        return gson.toJson(list)
    }

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
} 