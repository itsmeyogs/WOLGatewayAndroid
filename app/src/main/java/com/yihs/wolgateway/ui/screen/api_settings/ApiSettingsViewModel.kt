package com.yihs.wolgateway.ui.screen.api_settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yihs.wolgateway.R
import com.yihs.wolgateway.gateway.auth.ApiTokenStore
import com.yihs.wolgateway.gateway.server.GatewayServer
import com.yihs.wolgateway.gateway.server.GatewayServerConfig
import com.yihs.wolgateway.gateway.server.ServerState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.InetAddress
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class ApiSettingsViewModel @Inject constructor(
    private val preferences: DataStore<Preferences>,
    private val gatewayServer: GatewayServer,
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
                bindAddress = values[BIND_ADDRESS] ?: ApiSettingsUiState.DEFAULT_BIND_ADDRESS,
                port = (values[PORT] ?: ApiSettingsUiState.DEFAULT_PORT).toString(),
                serverState = gatewayServer.state.value,
                tokenMode = savedTokenMode,
                tokenPreview = maskToken(token),
                isLoading = false,
            )
        }
    }

    fun onBindAddressChanged(value: String) {
        _state.value = _state.value.copy(bindAddress = value, bindError = false)
    }

    fun onPortChanged(value: String) {
        _state.value = _state.value.copy(port = value.filter(Char::isDigit), portError = false)
    }

    fun setServerEnabled(enabled: Boolean) {
        if (enabled) startServer() else stopServer()
    }

    fun saveConfiguration(): Boolean {
        val current = _state.value
        val port = current.port.toIntOrNull()
        val bind = current.bindAddress.trim()
        val validPort = port in 1..65_535
        val validBind = isValidBindAddress(bind)
        if (!validPort || !validBind) {
            _state.value = current.copy(portError = !validPort, bindError = !validBind)
            viewModelScope.launch { _events.emit(ApiSettingsMessage(R.string.event_api_configuration_invalid)) }
            return false
        }
        viewModelScope.launch {
            applyConfiguration(
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
            gatewayServer.stop()
            savePreferences(serverEnabled = false)
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
        startAfterSave: Boolean,
        showSuccessFeedback: Boolean,
    ) {
        val current = _state.value
        val port = current.port.toIntOrNull()
        val bind = current.bindAddress.trim()
        val validPort = port in 1..65_535
        val validBind = isValidBindAddress(bind)
        if (!validPort || !validBind) {
            _state.value = current.copy(portError = !validPort, bindError = !validBind)
            _events.emit(ApiSettingsMessage(R.string.event_api_configuration_invalid))
            return
        }
        try {
            gatewayServer.stop()
            gatewayServer.configure(GatewayServerConfig(bind, port!!))
            savePreferences(serverEnabled = startAfterSave, bindAddress = bind, port = port)
            if (startAfterSave) gatewayServer.start()
            _state.value = _state.value.copy(
                bindAddress = bind,
                port = port.toString(),
                serverState = gatewayServer.state.value,
            )
            if (showSuccessFeedback) {
                _events.emit(ApiSettingsMessage(R.string.event_api_configuration_saved))
            }
        } catch (_: Exception) {
            _state.value = _state.value.copy(serverState = ServerState.STOPPED)
            savePreferences(serverEnabled = false, bindAddress = bind, port = port ?: ApiSettingsUiState.DEFAULT_PORT)
            _events.emit(ApiSettingsMessage(R.string.event_api_server_failed))
        }
    }

    private suspend fun savePreferences(
        serverEnabled: Boolean,
        bindAddress: String = _state.value.bindAddress,
        port: Int = _state.value.port.toIntOrNull() ?: ApiSettingsUiState.DEFAULT_PORT,
    ) {
        preferences.edit {
            it[SERVER_ENABLED] = serverEnabled
            it[BIND_ADDRESS] = bindAddress
            it[PORT] = port
        }
    }

    private fun isValidBindAddress(value: String): Boolean = runCatching {
        if (value.isBlank()) error("blank bind address")
        InetAddress.getByName(value)
        true
    }.getOrDefault(false)

    private fun maskToken(token: String): String = "${token.take(TOKEN_PREVIEW_LENGTH)}••••••••"

    private companion object {
        val SERVER_ENABLED = booleanPreferencesKey("api_server_enabled")
        val BIND_ADDRESS = stringPreferencesKey("api_bind_address")
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
