package com.example.wooauto.domain.managers

import com.example.wooauto.domain.models.Store
import com.example.wooauto.domain.repositories.StoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlobalStoreManager @Inject constructor(
    private val storeRepository: StoreRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // All active stores
    private val _activeStores = MutableStateFlow<List<Store>>(emptyList())
    val activeStores: StateFlow<List<Store>> = _activeStores.asStateFlow()

    // Currently selected store ID
    private val _selectedStoreId = MutableStateFlow<Long?>(null)
    val selectedStoreId: StateFlow<Long?> = _selectedStoreId.asStateFlow()

    // Derived flow for the selected store object
    val selectedStore: StateFlow<Store?> = combine(_activeStores, _selectedStoreId) { stores, id ->
        stores.find { it.id == id } ?: stores.firstOrNull()
    }.stateIn(scope, SharingStarted.Eagerly, null)

    // Stores that have unseen activity (for the red dot)
    private val _storesWithNotifications = MutableStateFlow<Set<Long>>(emptySet())
    val storesWithNotifications: StateFlow<Set<Long>> = _storesWithNotifications.asStateFlow()

    init {
        scope.launch {
            storeRepository.getAllStores().collect { stores ->
                val active = stores.filter { it.isActive }
                _activeStores.value = active
                
                // Initialize selection if needed
                if (_selectedStoreId.value == null && active.isNotEmpty()) {
                    // Prefer default or first
                    val default = active.find { it.isDefault } ?: active.first()
                    _selectedStoreId.value = default.id
                } else if (_selectedStoreId.value != null && active.none { it.id == _selectedStoreId.value }) {
                    // Selected store was disabled or deleted, switch to first available
                    _selectedStoreId.value = active.firstOrNull()?.id
                }
            }
        }
    }

    fun selectStore(storeId: Long) {
        _selectedStoreId.value = storeId
        // Clear notification for this store when selected
        clearNotificationForStore(storeId)
    }

    fun markStoreAsHasNotification(storeId: Long) {
        // Only mark if it's NOT the currently selected store
        if (_selectedStoreId.value != storeId) {
            _storesWithNotifications.value = _storesWithNotifications.value + storeId
        }
    }

    fun clearNotificationForStore(storeId: Long) {
        _storesWithNotifications.value = _storesWithNotifications.value - storeId
    }
}

