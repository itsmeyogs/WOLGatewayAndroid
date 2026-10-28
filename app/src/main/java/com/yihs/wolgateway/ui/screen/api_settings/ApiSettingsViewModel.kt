package com.yihs.wolgateway.ui.screen.api_settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.yihs.wolgateway.R
import com.yihs.wolgateway.androidservice.GatewayService
import com.yihs.wolgateway.data.preferences.GatewayStartupPreferences
import com.yihs.wolgateway.gateway.auth.ApiTokenStore
import com.yihs.wolgateway.gateway.server.GatewayServer
import com.yihs.wolgateway.gateway.server.GatewayServerConfig
import com.yihs.wolgateway.gateway.server.ServerState
import com.yihs.wolgateway.tunnel.controller.TunnelController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class ApiSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: DataStore<Preferences>,
    private val gatewayServer: GatewayServer,
    private val tunnelController: TunnelController,
    private val tokenStore: ApiTokenStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ApiSettingsUiState())
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<ApiSettingsMessage>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var savedTokenMode = ApiTokenMode.AUTOMATIC

    init {
        viewModelScope.launch {
            val values = preferences.data.first()
            savedTokenMode = if (values[TOKEN_MODE] == TOKEN_MODE_CUSTOM) {
                ApiTokenMode.CUSTOM
            } else {
                ApiTokenMode.AUTOMATIC
            }
            val token = tokenStore.read() ?: tokenStore.getOrCreate()
            _state.value = ApiSettingsUiState(
                port = (values[PORT] ?: ApiSettingsUiState.DEFAULT_PORT).toString(),
                serverState = gatewayServer.state.value,
                autoStartApi = values[GatewayStartupPreferences.AUTO_START_API] == true,
                tokenMode = savedTokenMode,
                tokenPreview = maskToken(token),
                isLoading = false,
            )
            combine(preferences.data, gatewayServer.state) { updatedValues, serverState ->
                _state.value.copy(
                    port = (updatedValues[PORT] ?: ApiSettingsUiState.DEFAULT_PORT).toString(),
                    serverState = serverState,
                    autoStartApi = updatedValues[GatewayStartupPreferences.AUTO_START_API] == true,
                    isLoading = false,
                )
            }.collect { _state.value = it }
        }
    }

    fun onPortChanged(value: String) {
        _state.value = _state.value.copy(port = value.filter(Char::isDigit), portError = false)
    }

    fun clearPortError() {
        _state.value = _state.value.copy(portError = false)
    }

    fun setServerEnabled(enabled: Boolean) {
        if (enabled) startServer() else stopServer()
    }

    fun saveConfiguration(portInput: String = _state.value.port): Boolean {
        val current = _state.value
        val port = portInput.toIntOrNull()
        val validPort = ApiSettingsUiState.isValidPort(port)
        if (!validPort) {
            _state.value = current.copy(portError = true)
            viewModelScope.launch { _events.emit(ApiSettingsMessage(R.string.event_api_configuration_invalid)) }
            return false
        }
        viewModelScope.launch {
            applyConfiguration(
                portInput = portInput,
                startAfterSave = current.serverState == ServerState.RUNNING,
                showSuccessFeedback = true,
            )
        }
        return true
    }

    fun startServer() {
        viewModelScope.launch {
            applyConfiguration(startAfterSave = true, showSuccessFeedback = false)
        }
    }

    fun stopServer() {
        viewModelScope.launch {
            tunnelController.disconnect()
            gatewayServer.stop()
            savePreferences(
                serverEnabled = false,
                port = _state.value.port.toIntOrNull() ?: ApiSettingsUiState.DEFAULT_PORT,
                clearAutostart = true,
            )
            GatewayService.stop(context)
            _state.value = _state.value.copy(serverState = gatewayServer.state.value)
        }
    }

    fun generateToken() {
        viewModelScope.launch {
            val token = tokenStore.createNew()
            savedTokenMode = ApiTokenMode.AUTOMATIC
            preferences.edit { it[TOKEN_MODE] = TOKEN_MODE_AUTOMATIC }
            _state.value = _state.value.copy(
                tokenMode = ApiTokenMode.AUTOMATIC,
                tokenPreview = maskToken(token),
                tokenInput = "",
                tokenInputError = null,
                isEditingCustomToken = false,
            )
            _events.emit(ApiSettingsMessage(R.string.event_api_token_generated))
        }
    }

    fun onTokenInputChanged(value: String) {
        _state.value = _state.value.copy(tokenInput = value, tokenInputError = null)
    }

    fun saveCustomToken() {
        viewModelScope.launch {
            val token = _state.value.tokenInput
            try {
                val savedToken = tokenStore.replace(token)
                savedTokenMode = ApiTokenMode.CUSTOM
                preferences.edit { it[TOKEN_MODE] = TOKEN_MODE_CUSTOM }
                _state.value = _state.value.copy(
                    tokenMode = ApiTokenMode.CUSTOM,
                    tokenPreview = maskToken(savedToken),
                    tokenInput = "",
                    tokenInputError = null,
                    isEditingCustomToken = false,
                )
                _events.emit(ApiSettingsMessage(R.string.event_api_token_saved))
            } catch (_: IllegalArgumentException) {
                val error = when {
                    token.length < MIN_TOKEN_LENGTH -> R.string.validation_token_too_short
                    token.length > MAX_TOKEN_LENGTH -> R.string.validation_token_too_long
                    else -> R.string.validation_token_invalid_characters
                }
                _state.value = _state.value.copy(tokenInputError = error)
            }
        }
    }

    fun selectTokenMode(mode: ApiTokenMode) {
        if (mode == _state.value.tokenMode && !(mode == ApiTokenMode.CUSTOM && !_state.value.isEditingCustomToken)) return
        if (mode == ApiTokenMode.AUTOMATIC) {
            if (savedTokenMode == ApiTokenMode.AUTOMATIC) {
                resetUncommittedTokenMode()
            } else {
                generateToken()
            }
        } else {
            _state.value = _state.value.copy(
                tokenMode = ApiTokenMode.CUSTOM,
                isEditingCustomToken = savedTokenMode != ApiTokenMode.CUSTOM,
                tokenInput = "",
                tokenInputError = null,
            )
        }
    }

    fun editCustomToken() {
        _state.value = _state.value.copy(isEditingCustomToken = true, tokenInput = "", tokenInputError = null)
    }

    fun readToken(): String? = tokenStore.read()

    fun resetUncommittedTokenMode() {
        _state.value = _state.value.copy(
            tokenMode = savedTokenMode,
            tokenInput = "",
            tokenInputError = null,
            isEditingCustomToken = false,
        )
    }

    private suspend fun applyConfiguration(
        portInput: String = _state.value.port,
        startAfterSave: Boolean,
        showSuccessFeedback: Boolean,
    ) {
        val current = _state.value
        val port = portInput.toIntOrNull()
        val validPort = ApiSettingsUiState.isValidPort(port)
        if (!validPort) {
            _state.value = current.copy(portError = true)
            _events.emit(ApiSettingsMessage(R.string.event_api_configuration_invalid))
            return
        }
        try {
            gatewayServer.stop()
            gatewayServer.configure(GatewayServerConfig(port!!))
            savePreferences(serverEnabled = startAfterSave, port = port)
            if (startAfterSave) gatewayServer.start()
            if (startAfterSave) GatewayService.start(context)
            _state.value = _state.value.copy(
                port = port.toString(),
                serverState = gatewayServer.state.value,
            )
            if (showSuccessFeedback) {
                _events.emit(ApiSettingsMessage(R.string.event_api_port_saved))
            }
        } catch (_: Exception) {
            _state.value = _state.value.copy(serverState = ServerState.STOPPED)
            savePreferences(serverEnabled = false, port = port ?: ApiSettingsUiState.DEFAULT_PORT)
            _events.emit(ApiSettingsMessage(R.string.event_api_server_failed))
        }
    }

    private suspend fun savePreferences(
        serverEnabled: Boolean,
        port: Int = _state.value.port.toIntOrNull() ?: ApiSettingsUiState.DEFAULT_PORT,
        clearAutostart: Boolean = false,
    ) {
        preferences.edit {
            it[SERVER_ENABLED] = serverEnabled
            it[PORT] = port
            if (clearAutostart) {
                it[GatewayStartupPreferences.AUTO_START_API] = false
                it[GatewayStartupPreferences.AUTO_START_TUNNEL] = false
            }
        }
    }

    private fun maskToken(token: String): String = "${token.take(TOKEN_PREVIEW_LENGTH)}••••••••"

    private companion object {
        val SERVER_ENABLED = booleanPreferencesKey("api_server_enabled")
        val PORT = intPreferencesKey("api_port")
        val TOKEN_MODE = stringPreferencesKey("api_token_mode")
        const val TOKEN_MODE_AUTOMATIC = "automatic"
        const val TOKEN_MODE_CUSTOM = "custom"
        const val MIN_TOKEN_LENGTH = 32
        const val MAX_TOKEN_LENGTH = 512
        const val TOKEN_PREVIEW_LENGTH = 6
    }
}

data class ApiSettingsMessage(val resourceId: Int)
