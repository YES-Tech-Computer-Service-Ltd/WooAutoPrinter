package com.wooauto.data.di

import android.content.Context
import androidx.room.Room
import com.wooauto.data.local.dao.OrderDao
import com.wooauto.data.local.dao.ProductDao
import com.wooauto.data.local.dao.SettingDao
import com.wooauto.data.local.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据库模块
 * 提供数据库和DAO的依赖注入
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * 提供Room数据库实例
     * @param context 应用上下文
     * @return 数据库实例
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "wooauto_database"
        ).build()
    }

    /**
     * 提供OrderDao实例
     * @param database 数据库实例
     * @return OrderDao实例
     */
    @Provides
    fun provideOrderDao(database: AppDatabase): OrderDao {
        return database.orderDao()
    }

    /**
     * 提供ProductDao实例
     * @param database 数据库实例
     * @return ProductDao实例
     */
    @Provides
    fun provideProductDao(database: AppDatabase): ProductDao {
        return database.productDao()
    }

    /**
     * 提供SettingDao实例
     * @param database 数据库实例
     * @return SettingDao实例
     */
    @Provides
    fun provideSettingDao(database: AppDatabase): SettingDao {
        return database.settingDao()
    }
} 