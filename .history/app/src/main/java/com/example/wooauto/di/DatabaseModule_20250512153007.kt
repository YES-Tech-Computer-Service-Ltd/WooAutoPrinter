package com.example.wooauto.di

import android.content.Context
import androidx.room.Room
import com.example.wooauto.data.local.dao.OrderDao
import com.example.wooauto.data.local.dao.ProductDao
import com.example.wooauto.data.local.dao.SettingDao
import com.example.wooauto.data.local.db.AppDatabase
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
        )
            .fallbackToDestructiveMigration() // 当数据库版本更新时，将删除旧数据库并创建新的（适用于开发阶段）
                                         // 注意：这会导致所有数据丢失，在生产环境中应该使用适当的迁移策略
        .build()
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