package com.example.wooauto.data.repository

import android.util.Log
import com.example.wooauto.data.local.WooCommerceConfig
import com.example.wooauto.data.local.dao.SettingDao
import com.example.wooauto.data.local.dao.StoreDao
import com.example.wooauto.data.local.entities.StoreEntity
import com.example.wooauto.data.mappers.StoreMapper
import com.example.wooauto.domain.models.Store
import com.example.wooauto.domain.repositories.StoreRepository
import com.example.wooauto.utils.UiLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreRepositoryImpl @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val storeDao: StoreDao,
    private val settingDao: SettingDao,
    private val wooCommerceConfig: WooCommerceConfig
) : StoreRepository {

    override fun getAllStores(): Flow<List<Store>> {
        return storeDao.getAllStores().map { entities ->
            entities.map { StoreMapper.toDomain(it) }
        }
    }

    override suspend fun getStoreById(id: Long): Store? {
        return storeDao.getStoreById(id)?.let { StoreMapper.toDomain(it) }
    }

    override suspend fun addStore(store: Store): Long {
        return storeDao.insertStore(StoreMapper.toEntity(store))
    }

    override suspend fun updateStore(store: Store) {
        storeDao.updateStore(StoreMapper.toEntity(store))
    }

    override suspend fun deleteStore(id: Long) {
        val store = storeDao.getStoreById(id)
        if (store != null) {
            storeDao.deleteStore(store)
        }
    }

    override suspend fun checkAndMigrateLegacyData() {
        try {
            val count = storeDao.getStoreCount()
            if (count > 0) {
                UiLog.d("StoreRepository", "Store table is not empty, skipping migration.")
                return
            }

            // Start Migration
            UiLog.d("StoreRepository", "Checking legacy data for migration...")

            val siteUrl = wooCommerceConfig.siteUrl.first()
            val consumerKey = wooCommerceConfig.consumerKey.first()
            val consumerSecret = wooCommerceConfig.consumerSecret.first()

            if (siteUrl.isBlank() && consumerKey.isBlank()) {
                UiLog.d("StoreRepository", "No legacy API config found, skipping migration.")
                return
            }

            // Fetch store info from SettingDao first
            var storeName = settingDao.getSettingByKey(SettingsRepositoryImpl.KEY_STORE_NAME)?.value
            var storeAddress = settingDao.getSettingByKey(SettingsRepositoryImpl.KEY_STORE_ADDRESS)?.value
            var storePhone = settingDao.getSettingByKey(SettingsRepositoryImpl.KEY_STORE_PHONE)?.value

            // If not found in SettingDao, try to migrate from SharedPreferences (legacy storage)
            if (storeName.isNullOrBlank()) {
                try {
                    // Try default SharedPreferences
                    val packageName = context.packageName
                    val defaultPrefs = context.getSharedPreferences("${packageName}_preferences", android.content.Context.MODE_PRIVATE)
                    storeName = defaultPrefs.getString(SettingsRepositoryImpl.KEY_STORE_NAME, null)
                    
                    if (!storeName.isNullOrBlank()) {
                        storeAddress = defaultPrefs.getString(SettingsRepositoryImpl.KEY_STORE_ADDRESS, storeAddress)
                        storePhone = defaultPrefs.getString(SettingsRepositoryImpl.KEY_STORE_PHONE, storePhone)
                        UiLog.d("StoreRepository", "Recovered legacy store info from default SharedPreferences: $storeName")
                    } else {
                        // Try "settings" SharedPreferences (DataStore backing file sometimes or older custom prefs)
                        val settingsPrefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                        storeName = settingsPrefs.getString(SettingsRepositoryImpl.KEY_STORE_NAME, null)
                        
                        if (!storeName.isNullOrBlank()) {
                            storeAddress = settingsPrefs.getString(SettingsRepositoryImpl.KEY_STORE_ADDRESS, storeAddress)
                            storePhone = settingsPrefs.getString(SettingsRepositoryImpl.KEY_STORE_PHONE, storePhone)
                            UiLog.d("StoreRepository", "Recovered legacy store info from 'settings' SharedPreferences: $storeName")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("StoreRepository", "Failed to read legacy SharedPreferences", e)
                }
            }
            
            // Use default if still null/blank
            val finalStoreName = if (storeName.isNullOrBlank()) SettingsRepositoryImpl.DEFAULT_STORE_NAME else storeName
            val finalStoreAddress = storeAddress
            val finalStorePhone = storePhone

            // Create new Store Entity
            val newStore = StoreEntity(
                name = if (finalStoreName.isBlank()) "My Store" else finalStoreName,
                siteUrl = siteUrl,
                consumerKey = consumerKey,
                consumerSecret = consumerSecret,
                address = finalStoreAddress,
                phone = finalStorePhone,
                isActive = true,
                isDefault = true
            )

            storeDao.insertStore(newStore)
            UiLog.d("StoreRepository", "Successfully migrated legacy store: ${newStore.name}")

        } catch (e: Exception) {
            Log.e("StoreRepository", "Error during legacy data migration", e)
        }
    }
}

