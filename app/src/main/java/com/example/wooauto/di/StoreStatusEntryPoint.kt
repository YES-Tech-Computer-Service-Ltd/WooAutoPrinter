package com.example.wooauto.di

import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.google.gson.Gson
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient

/**
 * Hilt EntryPoint for accessing network dependencies from top-level Compose UI (TopBar),
 * where we don't have a dedicated ViewModel/AndroidEntryPoint owner.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface StoreStatusEntryPoint {
    fun okHttpClient(): OkHttpClient
    fun gson(): Gson
    fun settingsRepository(): DomainSettingRepository
}


