package com.example.wooauto.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.wooauto.data.local.converters.CategoryListConverter
import com.example.wooauto.data.local.converters.DateConverter
import com.example.wooauto.data.local.converters.ImageListConverter
import com.example.wooauto.data.local.converters.LineItemListConverter
import com.example.wooauto.data.local.dao.OrderDao
import com.example.wooauto.data.local.dao.ProductDao
import com.example.wooauto.data.local.entities.OrderEntity
import com.example.wooauto.data.local.entities.ProductEntity

@Database(
    entities = [ProductEntity::class, OrderEntity::class], 
    version = 1,
    exportSchema = false
)
@TypeConverters(
    CategoryListConverter::class,
    ImageListConverter::class,
    LineItemListConverter::class,
    DateConverter::class
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun orderDao(): OrderDao
    
    companion object {
        private const val DATABASE_NAME = "wooauto_db"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 