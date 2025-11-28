package com.example.wooauto.domain.repositories

import com.example.wooauto.domain.models.Store
import kotlinx.coroutines.flow.Flow

interface StoreRepository {
    fun getAllStores(): Flow<List<Store>>
    
    suspend fun getStoreById(id: Long): Store?
    
    suspend fun addStore(store: Store): Long
    
    suspend fun updateStore(store: Store)
    
    suspend fun deleteStore(id: Long)
    
    suspend fun checkAndMigrateLegacyData()
}

