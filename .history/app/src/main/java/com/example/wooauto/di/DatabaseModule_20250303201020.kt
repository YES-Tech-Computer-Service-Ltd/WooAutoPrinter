package com.example.wooauto.di

import android.content.Context
import androidx.room.Room
import com.example.wooauto.data.local.db.AppDatabase
import com.example.wooauto.data.local.dao.OrderDao
import com.example.wooauto.data.local.dao.ProductDao
import com.example.wooauto.data.local.dao.SettingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "wooauto_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    
    @Provides
    @Singleton
    fun provideProductDao(appDatabase: AppDatabase): ProductDao {
        return appDatabase.productDao()
    }
    
    @Provides
    @Singleton
    fun provideOrderDao(appDatabase: AppDatabase): OrderDao {
        return appDatabase.orderDao()
    }

    @Provides
    @Singleton
    fun provideSettingDao(appDatabase: AppDatabase): SettingDao {
        return appDatabase.settingDao()
    }
} 