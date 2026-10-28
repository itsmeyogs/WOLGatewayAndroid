package com.yihs.wolgateway.ui.screen.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yihs.wolgateway.R
import com.yihs.wolgateway.domain.model.Device
import com.yihs.wolgateway.domain.repository.DeviceRepository
import com.yihs.wolgateway.domain.repository.DuplicateDeviceException
import com.yihs.wolgateway.domain.validation.DeviceField
import com.yihs.wolgateway.domain.validation.InvalidDeviceException
import com.yihs.wolgateway.domain.validation.ValidationMessage
import com.yihs.wolgateway.domain.model.WolResult
import com.yihs.wolgateway.domain.usecase.CheckDeviceStatusUseCase
import com.yihs.wolgateway.domain.usecase.WakeDeviceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.yihs.wolgateway.gateway.server.GatewayServer
import com.yihs.wolgateway.tunnel.controller.TunnelController

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val wakeDeviceUseCase: WakeDeviceUseCase,
    private val checkDeviceStatusUseCase: CheckDeviceStatusUseCase,
    gatewayServer: GatewayServer,
    tunnelController: TunnelController,
) : ViewModel() {
    val gatewayState = gatewayServer.state
    val tunnelState = tunnelController.state

    val devices: StateFlow<List<Device>> = repository.observeDevices().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _fieldErrors = MutableStateFlow<Map<DeviceField, ValidationMessage>>(emptyMap())
    val fieldErrors: StateFlow<Map<DeviceField, ValidationMessage>> = _fieldErrors.asStateFlow()

    private val _events = MutableSharedFlow<UiMessage>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private val _busyDeviceIds = MutableStateFlow<Set<Long>>(emptySet())
    val busyDeviceIds: StateFlow<Set<Long>> = _busyDeviceIds.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val statusCheckMutex = Mutex()

    fun setSearchQuery(value: String) {
        _searchQuery.value = value
    }

    fun observeDevice(id: Long) = repository.observeDevice(id)

    suspend fun saveDevice(device: Device): Boolean {
        return try {
            _fieldErrors.value = emptyMap()
            val newDeviceId = if (device.id == 0L) {
                repository.addDevice(device)
            } else {
                repository.updateDevice(device)
                null
            }
            _events.emit(UiMessage(R.string.event_device_saved))
            newDeviceId?.let(::checkDeviceStatusAfterSave)
            true
        } catch (error: InvalidDeviceException) {
            _fieldErrors.value = error.issues.associate { it.field to it.message }
            _events.emit(UiMessage(R.string.event_device_validation_failed))
            false
        } catch (_: DuplicateDeviceException) {
            _events.emit(UiMessage(R.string.event_device_duplicate))
            false
        } catch (_: IllegalArgumentException) {
            _events.emit(UiMessage(R.string.event_unable_to_save_device))
            false
        } catch (_: Exception) {
            _events.emit(UiMessage(R.string.event_device_could_not_be_saved))
            false
        }
    }

    fun deleteDevice(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteDevice(id) }
                .onSuccess { _events.emit(UiMessage(R.string.event_device_deleted)) }
                .onFailure { _events.emit(UiMessage(R.string.event_device_could_not_be_deleted)) }
        }
    }

    fun wakeDevice(id: Long) {
        runDeviceAction(id) {
            val result = wakeDeviceUseCase(id)
            _events.emit(
                UiMessage(
                    if (result.result == WolResult.PACKET_SENT) {
                        R.string.event_wol_packet_sent
                    } else {
                        R.string.event_wol_packet_failed
                    },
                ),
            )
            if (result.result == WolResult.PACKET_SENT) {
                listOf(5_000L, 15_000L, 30_000L).forEach { delayMs ->
                    delay(delayMs)
                    runCatching { checkDeviceStatusUseCase(id) }
                }
            }
        }
    }

    fun checkDeviceStatus(id: Long) {
        runDeviceAction(id) {
            statusCheckMutex.withLock { checkDeviceStatusUseCase(id) }
            _events.emit(UiMessage(R.string.event_status_checked))
        }
    }

    private fun checkDeviceStatusAfterSave(id: Long) {
        viewModelScope.launch {
            try {
                statusCheckMutex.withLock { checkDeviceStatusUseCase(id) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Status probing is best-effort; the saved device remains valid.
            }
        }
    }

    fun refreshAllStatuses() {
        if (_isRefreshing.value || devices.value.isEmpty()) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                devices.value.map { it.id }.forEach { id ->
                    statusCheckMutex.withLock { checkDeviceStatusUseCase(id) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _events.emit(UiMessage(R.string.event_network_action_failed))
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun runDeviceAction(id: Long, action: suspend () -> Unit) {
        if (id in _busyDeviceIds.value) return
        viewModelScope.launch {
            _busyDeviceIds.value += id
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _events.emit(UiMessage(R.string.event_network_action_failed))
            } finally {
                _busyDeviceIds.value -= id
            }
        }
    }

}

data class UiMessage(
    val resourceId: Int,
    val arguments: List<Any> = emptyList(),
)
