package com.example.wooauto.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.wooauto.data.database.dao.OrderDao
import com.example.wooauto.data.database.dao.ProductDao
import com.example.wooauto.data.database.entities.OrderEntity
import com.example.wooauto.data.database.entities.ProductEntity

@Database(
    entities = [
        OrderEntity::class,
        ProductEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun orderDao(): OrderDao
    abstract fun productDao(): ProductDao

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