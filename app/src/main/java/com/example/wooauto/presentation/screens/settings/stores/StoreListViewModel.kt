package com.example.wooauto.presentation.screens.settings.stores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.domain.models.Store
import com.example.wooauto.domain.repositories.StoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StoreListViewModel @Inject constructor(
    private val storeRepository: StoreRepository
) : ViewModel() {

    val stores: StateFlow<List<Store>> = storeRepository.getAllStores()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Function to set active store (if we implement switching logic later)
    fun setActiveStore(store: Store) {
        viewModelScope.launch {
            // Logic to set active store
            // For now, just a placeholder or simple update
             storeRepository.updateStore(store.copy(isActive = true))
             // We might need to deactivate others if single-active policy
        }
    }
}

