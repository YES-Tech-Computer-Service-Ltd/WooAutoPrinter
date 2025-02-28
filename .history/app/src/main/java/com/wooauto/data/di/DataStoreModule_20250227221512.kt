package com.wooauto.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// 使用by lazy确保线程安全的延迟初始化
private val Context.dataStore by lazy { 
    preferencesDataStore(name = "woo_auto_preferences").getValue(this, Context::dataStore) 
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    
    @Singleton
    @Provides
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
} 