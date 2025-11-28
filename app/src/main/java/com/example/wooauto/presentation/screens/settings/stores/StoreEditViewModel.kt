package com.example.wooauto.presentation.screens.settings.stores

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.domain.models.Store
import com.example.wooauto.domain.repositories.StoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StoreEditViewModel @Inject constructor(
    private val storeRepository: StoreRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val storeId: Long? = savedStateHandle.get<String>("storeId")?.toLongOrNull()

    private val _storeName = MutableStateFlow("")
    val storeName: StateFlow<String> = _storeName.asStateFlow()

    private val _siteUrl = MutableStateFlow("")
    val siteUrl: StateFlow<String> = _siteUrl.asStateFlow()

    private val _consumerKey = MutableStateFlow("")
    val consumerKey: StateFlow<String> = _consumerKey.asStateFlow()

    private val _consumerSecret = MutableStateFlow("")
    val consumerSecret: StateFlow<String> = _consumerSecret.asStateFlow()

    private val _storeAddress = MutableStateFlow("")
    val storeAddress: StateFlow<String> = _storeAddress.asStateFlow()

    private val _storePhone = MutableStateFlow("")
    val storePhone: StateFlow<String> = _storePhone.asStateFlow()
    
    private val _isActive = MutableStateFlow(true)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    init {
        if (storeId != null && storeId != 0L) {
            loadStore(storeId)
        }
    }

    private fun loadStore(id: Long) {
        viewModelScope.launch {
            val store = storeRepository.getStoreById(id)
            if (store != null) {
                _storeName.value = store.name
                _siteUrl.value = store.siteUrl
                _consumerKey.value = store.consumerKey
                _consumerSecret.value = store.consumerSecret
                _storeAddress.value = store.address ?: ""
                _storePhone.value = store.phone ?: ""
                _isActive.value = store.isActive
            }
        }
    }

    fun onEvent(event: StoreEditEvent) {
        when (event) {
            is StoreEditEvent.NameChanged -> _storeName.value = event.name
            is StoreEditEvent.UrlChanged -> _siteUrl.value = event.url
            is StoreEditEvent.KeyChanged -> _consumerKey.value = event.key
            is StoreEditEvent.SecretChanged -> _consumerSecret.value = event.secret
            is StoreEditEvent.AddressChanged -> _storeAddress.value = event.address
            is StoreEditEvent.PhoneChanged -> _storePhone.value = event.phone
            is StoreEditEvent.ActiveChanged -> _isActive.value = event.isActive
            is StoreEditEvent.Save -> saveStore()
            is StoreEditEvent.Delete -> deleteStore()
        }
    }

    private fun saveStore() {
        viewModelScope.launch {
            if (_storeName.value.isBlank() || _siteUrl.value.isBlank() || _consumerKey.value.isBlank() || _consumerSecret.value.isBlank()) {
                _uiEvent.send(UiEvent.ShowSnackbar("Please fill all required fields"))
                return@launch
            }

            val store = Store(
                id = storeId ?: 0,
                name = _storeName.value,
                siteUrl = _siteUrl.value,
                consumerKey = _consumerKey.value,
                consumerSecret = _consumerSecret.value,
                address = _storeAddress.value,
                phone = _storePhone.value,
                isActive = _isActive.value
            )

            if (store.id == 0L) {
                storeRepository.addStore(store)
            } else {
                storeRepository.updateStore(store)
            }
            _uiEvent.send(UiEvent.SaveSuccess)
        }
    }

    private fun deleteStore() {
        viewModelScope.launch {
            if (storeId != null) {
                storeRepository.deleteStore(storeId)
                _uiEvent.send(UiEvent.SaveSuccess)
            }
        }
    }

    sealed class UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent()
        object SaveSuccess : UiEvent()
    }
}

sealed class StoreEditEvent {
    data class NameChanged(val name: String) : StoreEditEvent()
    data class UrlChanged(val url: String) : StoreEditEvent()
    data class KeyChanged(val key: String) : StoreEditEvent()
    data class SecretChanged(val secret: String) : StoreEditEvent()
    data class AddressChanged(val address: String) : StoreEditEvent()
    data class PhoneChanged(val phone: String) : StoreEditEvent()
    data class ActiveChanged(val isActive: Boolean) : StoreEditEvent()
    object Save : StoreEditEvent()
    object Delete : StoreEditEvent()
}

