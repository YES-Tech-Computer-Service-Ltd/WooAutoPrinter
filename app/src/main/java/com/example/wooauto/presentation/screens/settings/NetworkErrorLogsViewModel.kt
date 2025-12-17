package com.example.wooauto.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.diagnostics.network.NetworkErrorLogEntry
import com.example.wooauto.diagnostics.network.NetworkErrorLogStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NetworkErrorLogsViewModel @Inject constructor(
    private val store: NetworkErrorLogStore
) : ViewModel() {

    val logs: StateFlow<List<NetworkErrorLogEntry>> = store
        .logsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearAll() {
        viewModelScope.launch {
            store.clear()
        }
    }
}


