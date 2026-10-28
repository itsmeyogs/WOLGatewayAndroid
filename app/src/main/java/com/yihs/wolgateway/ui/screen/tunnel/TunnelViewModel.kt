package com.yihs.wolgateway.ui.screen.tunnel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yihs.wolgateway.R
import com.yihs.wolgateway.tunnel.controller.TunnelController
import com.yihs.wolgateway.tunnel.controller.TunnelControllerError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TunnelViewModel @Inject constructor(
    private val controller: TunnelController,
) : ViewModel() {
    private val _state = MutableStateFlow(TunnelUiState())
    val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<TunnelMessage>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            controller.state.collect { runtime ->
                _state.value = _state.value.copy(
                    status = runtime.status,
                    tokenPreview = runtime.tokenPreview,
                    error = if (runtime.error == TunnelControllerError.RUNTIME_UNAVAILABLE) {
                        R.string.event_tunnel_runtime_unavailable
                    } else if (runtime.error == TunnelControllerError.START_FAILED) {
                        R.string.event_tunnel_start_failed
                    } else null,
                )
            }
        }
    }

    fun onTokenInputChanged(value: String) {
        _state.value = _state.value.copy(tokenInput = value, tokenError = null)
    }

    fun onOriginChanged(value: String) {
        _state.value = _state.value.copy(originAddress = value, originError = false)
    }

    fun editToken() {
        _state.value = _state.value.copy(isEditingToken = true, tokenInput = "", tokenError = null)
    }

    fun saveToken() {
        val token = _state.value.tokenInput
        if (token.length !in MIN_TOKEN_LENGTH..MAX_TOKEN_LENGTH || token.any { it.code !in 0x21..0x7e }) {
            _state.value = _state.value.copy(tokenError = R.string.validation_tunnel_token_invalid)
            return
        }
        if (controller.saveToken(token)) {
            _state.value = _state.value.copy(isEditingToken = false, tokenInput = "", tokenError = null)
            viewModelScope.launch { _events.emit(TunnelMessage(R.string.event_tunnel_token_saved)) }
        }
    }

    fun connect() {
        val origin = _state.value.originAddress.trim()
        if (origin.isBlank()) {
            _state.value = _state.value.copy(originError = true)
            return
        }
        controller.connect(origin)
    }

    fun reconnect() = controller.reconnect(_state.value.originAddress.trim())

    fun disconnect() = controller.disconnect()

    private companion object {
        const val MIN_TOKEN_LENGTH = 32
        const val MAX_TOKEN_LENGTH = 8192
    }
}

data class TunnelMessage(val resourceId: Int)
