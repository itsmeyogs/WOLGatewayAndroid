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

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val repository: DeviceRepository,
) : ViewModel() {
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

    fun setSearchQuery(value: String) {
        _searchQuery.value = value
    }

    fun observeDevice(id: Long) = repository.observeDevice(id)

    suspend fun saveDevice(device: Device): Boolean {
        return try {
            _fieldErrors.value = emptyMap()
            if (device.id == 0L) repository.addDevice(device) else repository.updateDevice(device)
            _events.emit(UiMessage(R.string.event_device_saved))
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

    fun acknowledgeUnavailableAction(action: String) {
        _events.tryEmit(UiMessage(R.string.event_action_requires_engine, listOf(action)))
    }

}

data class UiMessage(
    val resourceId: Int,
    val arguments: List<Any> = emptyList(),
)
