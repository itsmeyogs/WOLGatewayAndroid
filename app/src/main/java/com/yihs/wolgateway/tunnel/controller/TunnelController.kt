package com.yihs.wolgateway.tunnel.controller

import com.yihs.wolgateway.tunnel.adapter.TunnelRuntime
import com.yihs.wolgateway.tunnel.adapter.userSafeTunnelMessage
import com.yihs.wolgateway.tunnel.TunnelLog
import com.yihs.wolgateway.tunnel.model.TunnelRuntimeConfig
import com.yihs.wolgateway.tunnel.model.TunnelState
import com.yihs.wolgateway.tunnel.secure.TunnelTokenStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class TunnelController @Inject constructor(
    private val runtime: TunnelRuntime,
    private val tokenStore: TunnelTokenStore,
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<TunnelControllerState> = _state.asStateFlow()
    private val lifecycleMutex = Mutex()
    private var autoReconnect = false
    private var originAddress: String? = null
    private var monitorJob: Job? = null

    fun saveToken(token: String): Boolean = runCatching {
        tokenStore.replace(token)
        _state.value = _state.value.copy(
            status = TunnelState.DISCONNECTED,
            tokenConfigured = true,
            tokenPreview = maskToken(token),
            error = null,
            userMessage = null,
        )
    }.isSuccess

    fun connect(originAddress: String) {
        scope.launch {
            lifecycleMutex.withLock {
                this@TunnelController.originAddress = originAddress
                val token = tokenStore.read()
                if (token == null) {
                    _state.value = _state.value.copy(
                        status = TunnelState.NOT_CONFIGURED,
                        tokenConfigured = false,
                        error = null,
                        userMessage = null,
                    )
                    return@withLock
                }
                    _state.value = _state.value.copy(status = TunnelState.CONNECTING, error = null, userMessage = null)
                try {
                    if (!runtime.isRunning()) {
                        withTimeout(START_TIMEOUT_MS) {
                            runtime.start(TunnelRuntimeConfig(token, originAddress))
                        }
                    }
                    _state.value = _state.value.copy(status = TunnelState.CONNECTED, error = null, userMessage = null)
                    startMonitor()
                } catch (timeout: TimeoutCancellationException) {
                    runCatching { runtime.stop() }
                    _state.value = _state.value.copy(
                        status = TunnelState.ERROR,
                        error = TunnelControllerError.START_FAILED,
                        userMessage = "Tunnel connection timed out. Check your internet connection.",
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    TunnelLog.error("connect failed error=${error.safeMessage()}")
                    _state.value = _state.value.copy(
                        status = TunnelState.ERROR,
                        error = error.toControllerError(),
                        userMessage = error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun reconnect(originAddress: String) {
        scope.launch {
            lifecycleMutex.withLock {
                this@TunnelController.originAddress = originAddress
                _state.value = _state.value.copy(status = TunnelState.RECONNECTING, error = null, userMessage = null)
                runCatching { runtime.stop() }
                val token = tokenStore.read()
                if (token == null) {
                    _state.value = _state.value.copy(
                        status = TunnelState.NOT_CONFIGURED,
                        tokenConfigured = false,
                        error = null,
                        userMessage = null,
                    )
                    return@withLock
                }
                try {
                    withTimeout(START_TIMEOUT_MS) {
                        runtime.start(TunnelRuntimeConfig(token, originAddress))
                    }
                    _state.value = _state.value.copy(status = TunnelState.CONNECTED, error = null, userMessage = null)
                    startMonitor()
                } catch (timeout: TimeoutCancellationException) {
                    runCatching { runtime.stop() }
                    _state.value = _state.value.copy(
                        status = TunnelState.ERROR,
                        error = TunnelControllerError.START_FAILED,
                        userMessage = "Tunnel connection timed out. Check your internet connection.",
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    TunnelLog.error("reconnect failed error=${error.safeMessage()}")
                    _state.value = _state.value.copy(
                        status = TunnelState.ERROR,
                        error = error.toControllerError(),
                        userMessage = error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun disconnect() {
        monitorJob?.cancel()
        monitorJob = null
        scope.launch {
            lifecycleMutex.withLock {
                runCatching { runtime.stop() }
                _state.value = _state.value.copy(status = TunnelState.DISCONNECTED, error = null, userMessage = null)
            }
        }
    }

    fun onNetworkLost() {
        scope.launch {
            lifecycleMutex.withLock {
                runCatching { runtime.stop() }
                _state.value = _state.value.copy(
                    status = if (autoReconnect) TunnelState.RECONNECTING else TunnelState.DISCONNECTED,
                    error = null,
                    userMessage = null,
                )
                if (autoReconnect) startMonitor()
            }
        }
    }

    fun setAutoReconnect(enabled: Boolean) {
        autoReconnect = enabled
        if (!enabled) {
            monitorJob?.cancel()
            monitorJob = null
            if (_state.value.status == TunnelState.RECONNECTING) {
                scope.launch {
                    lifecycleMutex.withLock {
                        runCatching { runtime.stop() }
                        _state.value = _state.value.copy(status = TunnelState.DISCONNECTED, error = null, userMessage = null)
                    }
                }
            }
        } else if (_state.value.status == TunnelState.CONNECTED || _state.value.status == TunnelState.RECONNECTING) {
            startMonitor()
        }
    }

    private fun startMonitor() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                delay(MONITOR_INTERVAL_MS)
                if (runtime.isRunning()) continue
                lifecycleMutex.withLock {
                    if (runtime.isRunning()) return@withLock
                    val origin = originAddress ?: return@withLock
                    if (!autoReconnect) {
                        _state.value = _state.value.copy(status = TunnelState.DISCONNECTED, error = null, userMessage = null)
                        monitorJob?.cancel()
                        monitorJob = null
                        return@withLock
                    }
                    val token = tokenStore.read()
                    if (token == null) {
                        _state.value = _state.value.copy(
                            status = TunnelState.NOT_CONFIGURED,
                            tokenConfigured = false,
                            error = null,
                            userMessage = null,
                        )
                        return@withLock
                    }
                    _state.value = _state.value.copy(status = TunnelState.RECONNECTING, error = null)
                    try {
                        withTimeout(START_TIMEOUT_MS) {
                            runtime.start(TunnelRuntimeConfig(token, origin))
                        }
                        _state.value = _state.value.copy(
                            status = TunnelState.CONNECTED,
                            error = null,
                            userMessage = null,
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        TunnelLog.error("auto reconnect failed error=${error.safeMessage()}")
                        _state.value = _state.value.copy(
                            status = TunnelState.RECONNECTING,
                            error = error.toControllerError(),
                            userMessage = error.toUserMessage(),
                        )
                    }
                }
            }
        }
    }

    private fun initialState(): TunnelControllerState {
        val token = tokenStore.read()
        return TunnelControllerState(
            status = if (token == null) TunnelState.NOT_CONFIGURED else TunnelState.DISCONNECTED,
            tokenConfigured = token != null,
            tokenPreview = token?.let(::maskToken) ?: "••••••••",
        )
    }

    private fun maskToken(token: String): String = "${token.take(TOKEN_PREVIEW_LENGTH)}••••••••"

    private companion object {
        const val TOKEN_PREVIEW_LENGTH = 6
        const val MONITOR_INTERVAL_MS = 1_000L
        const val START_TIMEOUT_MS = 15_000L
    }
}

data class TunnelControllerState(
    val status: TunnelState,
    val tokenConfigured: Boolean,
    val tokenPreview: String,
    val error: TunnelControllerError? = null,
    val userMessage: String? = null,
)

enum class TunnelControllerError {
    RUNTIME_UNAVAILABLE,
    START_FAILED,
}

private fun Exception.toControllerError(): TunnelControllerError =
    if (message?.contains("not found or not executable") == true) {
        TunnelControllerError.RUNTIME_UNAVAILABLE
    } else {
        TunnelControllerError.START_FAILED
    }

private fun Throwable.safeMessage(): String = message?.take(300) ?: javaClass.simpleName

private fun Throwable.toUserMessage(): String? {
    return userSafeTunnelMessage(message.orEmpty())
}
