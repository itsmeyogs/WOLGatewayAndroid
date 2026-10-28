package com.yihs.wolgateway.ui.screen.tunnel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yihs.wolgateway.R
import com.yihs.wolgateway.tunnel.controller.TunnelController
import com.yihs.wolgateway.tunnel.controller.TunnelControllerError
import com.yihs.wolgateway.tunnel.model.TunnelState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class TunnelViewModel @Inject constructor(
    private val preferences: DataStore<Preferences>,
    private val controller: TunnelController,
) : ViewModel() {
    private val _state = MutableStateFlow(TunnelUiState())
    val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<TunnelMessage>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val values = preferences.data.first()
            val autoReconnect = values[AUTO_RECONNECT] ?: false
            controller.setAutoReconnect(autoReconnect)
            _state.value = _state.value.copy(autoReconnect = autoReconnect)
            controller.state.collect { runtime ->
                _state.value = _state.value.copy(
                    status = runtime.status,
                    tokenConfigured = runtime.tokenConfigured,
                    tokenPreview = runtime.tokenPreview,
                    errorMessage = runtime.userMessage,
                    error = if (runtime.error == TunnelControllerError.RUNTIME_UNAVAILABLE) {
                        R.string.event_tunnel_runtime_unavailable
                    } else null,
                )
            }
        }
    }

    fun onTokenInputChanged(value: String) {
        _state.value = _state.value.copy(tokenInput = value, tokenError = null)
    }

    fun editToken() {
        if (isConnectionActive()) return
        _state.value = _state.value.copy(isEditingToken = true, tokenInput = "", tokenError = null)
    }

    fun saveToken() {
        if (isConnectionActive()) return
        val token = _state.value.tokenInput
        if (token.length !in MIN_TOKEN_LENGTH..MAX_TOKEN_LENGTH || token.any { it.code !in 0x21..0x7e }) {
            _state.value = _state.value.copy(tokenError = R.string.validation_tunnel_token_invalid)
            return
        }
        if (controller.saveToken(token)) {
            _state.value = _state.value.copy(
                tokenConfigured = true,
                isEditingToken = false,
                tokenInput = "",
                tokenError = null,
                error = null,
                errorMessage = null,
            )
            viewModelScope.launch { _events.emit(TunnelMessage(R.string.event_tunnel_token_saved)) }
        }
    }

    fun setAutoReconnect(enabled: Boolean) {
        if (_state.value.status == TunnelState.NOT_CONFIGURED) return
        _state.value = _state.value.copy(autoReconnect = enabled)
        controller.setAutoReconnect(enabled)
        viewModelScope.launch {
            preferences.edit { it[AUTO_RECONNECT] = enabled }
        }
    }

    fun connect() {
        if (_state.value.status == TunnelState.NOT_CONFIGURED) return
        viewModelScope.launch { controller.connect(readOrigin()) }
    }

    fun disconnect() {
        controller.disconnect()
        if (_state.value.autoReconnect) {
            _state.value = _state.value.copy(autoReconnect = false)
            viewModelScope.launch {
                preferences.edit { it[AUTO_RECONNECT] = false }
            }
        }
    }

    private suspend fun readOrigin(): String {
        val port = preferences.data.first()[API_PORT] ?: DEFAULT_API_PORT
        return localApiOrigin(port)
    }

    private fun isConnectionActive(): Boolean = _state.value.status == TunnelState.CONNECTING ||
        _state.value.status == TunnelState.CONNECTED ||
        _state.value.status == TunnelState.RECONNECTING

    private companion object {
        val API_PORT = intPreferencesKey("api_port")
        val AUTO_RECONNECT = booleanPreferencesKey("tunnel_auto_reconnect")
        const val DEFAULT_API_PORT = 8080
        const val MIN_TOKEN_LENGTH = 32
        const val MAX_TOKEN_LENGTH = 8192
    }
}

internal fun localApiOrigin(port: Int): String = "127.0.0.1:$port"

data class TunnelMessage(val resourceId: Int)
