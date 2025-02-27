package com.example.wooauto.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.wooauto.data.database.dao.OrderDao
import com.example.wooauto.data.database.dao.ProductDao
import com.example.wooauto.data.database.entities.OrderEntity
import com.example.wooauto.data.database.entities.ProductEntity

@Database(
    entities = [
        OrderEntity::class,
        ProductEntity::class
    ],
    version = 2, // 更新版本号
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun orderDao(): OrderDao
    abstract fun productDao(): ProductDao

    companion object {
        private const val DATABASE_NAME = "wooauto_db"

        // 定义版本 1 到版本 2 的迁移，添加 WooCommerce Food 插件字段
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加新列
                database.execSQL("ALTER TABLE orders ADD COLUMN delivery_date TEXT")
                database.execSQL("ALTER TABLE orders ADD COLUMN delivery_time TEXT")
                database.execSQL("ALTER TABLE orders ADD COLUMN order_method TEXT")
                database.execSQL("ALTER TABLE orders ADD COLUMN tip TEXT")
                database.execSQL("ALTER TABLE orders ADD COLUMN delivery_fee TEXT")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2) // 添加迁移
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}