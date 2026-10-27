package com.yihs.wolgateway.ui.screen.add_devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yihs.wolgateway.network.discovery.DiscoveryResult
import com.yihs.wolgateway.network.discovery.DiscoveryResults
import com.yihs.wolgateway.network.discovery.NetworkDiscovery
import com.yihs.wolgateway.domain.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface AddDevicesState {
    data object NotStarted : AddDevicesState
    data object Scanning : AddDevicesState
    data class Complete(val devices: List<DiscoveryResult>) : AddDevicesState
    data object Failed : AddDevicesState
}

@HiltViewModel
class AddDevicesViewModel @Inject constructor(
    private val discovery: NetworkDiscovery,
    private val repository: DeviceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<AddDevicesState>(AddDevicesState.NotStarted)
    val state: StateFlow<AddDevicesState> = _state.asStateFlow()
    private var scanJob: Job? = null
    private var discoveredResults: List<DiscoveryResult> = emptyList()

    init {
        viewModelScope.launch {
            repository.observeDevices().collect { savedDevices ->
                if (_state.value is AddDevicesState.Complete) updateVisibleResults(savedDevices)
            }
        }
    }

    fun scanIfNeeded() {
        if (_state.value == AddDevicesState.NotStarted) scan()
    }

    fun scan() {
        scanJob?.cancel()
        _state.value = AddDevicesState.Scanning
        scanJob = viewModelScope.launch {
            runCatching { discovery.scan() }
                .onSuccess {
                    discoveredResults = it
                    updateVisibleResults(repository.observeDevices().first())
                }
                .onFailure { error ->
                    if (error !is kotlinx.coroutines.CancellationException) {
                        _state.value = AddDevicesState.Failed
                    }
                }
        }
    }

    private fun updateVisibleResults(savedDevices: List<com.yihs.wolgateway.domain.model.Device>) {
        _state.value = AddDevicesState.Complete(
            DiscoveryResults.excludeSavedAddresses(discoveredResults, savedDevices),
        )
    }

}
