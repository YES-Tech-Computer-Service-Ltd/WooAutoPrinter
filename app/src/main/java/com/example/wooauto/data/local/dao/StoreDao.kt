package com.example.wooauto.data.local.dao

import androidx.room.*
import com.example.wooauto.data.local.entities.StoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores")
    fun getAllStores(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE isActive = 1")
    fun getActiveStores(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE id = :id")
    suspend fun getStoreById(id: Long): StoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStore(store: StoreEntity): Long

    @Update
    suspend fun updateStore(store: StoreEntity)

    @Delete
    suspend fun deleteStore(store: StoreEntity)

    @Query("SELECT COUNT(*) FROM stores")
    suspend fun getStoreCount(): Int
}

