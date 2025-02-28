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

// 使用标准的DataStore定义方式，避免递归
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "woo_auto_preferences")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    
    @Singleton
    @Provides
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
} 