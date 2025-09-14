package com.example.wooauto.di

import android.content.Context
import androidx.room.Room
import com.example.wooauto.data.local.dao.OrderDao
import com.example.wooauto.data.local.dao.ProductDao
import com.example.wooauto.data.local.dao.SettingDao
import com.example.wooauto.data.local.dao.TemplateConfigDao
import com.example.wooauto.data.local.db.AppDatabase
import com.example.wooauto.data.local.db.migrations.AppMigrations
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
            // 注册无损迁移
            .addMigrations(AppMigrations.MIGRATION_8_9, AppMigrations.MIGRATION_9_10)
            // 可选：对非常旧的版本使用破坏性回退，避免装了更旧版本的用户崩溃
            // .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7)
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

    /**
     * 提供TemplateConfigDao实例
     * @param database 数据库实例
     * @return TemplateConfigDao实例
     */
    @Provides
    fun provideTemplateConfigDao(database: AppDatabase): TemplateConfigDao {
        return database.templateConfigDao()
    }
} 