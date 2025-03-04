package com.example.wooauto.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.wooauto.data.local.dao.OrderDao
import com.example.wooauto.data.local.dao.ProductDao
import com.example.wooauto.data.local.dao.SettingDao
import com.example.wooauto.data.local.entities.OrderEntity
import com.example.wooauto.data.local.entities.ProductEntity
import com.example.wooauto.data.local.entities.SettingEntity
import com.example.wooauto.data.local.converters.RoomConverters

/**
 * 应用数据库类
 * 定义了数据库的配置和转换器
 * 
 * 版本历史：
 * - V1: 初始版本
 * - V2: 更新实体类结构，适应新的数据模型
 */
@Database(
    entities = [
        OrderEntity::class,
        ProductEntity::class,
        SettingEntity::class
    ],
    version = 2,  // 版本号从1升级到2
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun productDao(): ProductDao
    abstract fun settingDao(): SettingDao
} 