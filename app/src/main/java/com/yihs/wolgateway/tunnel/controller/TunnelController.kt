package com.yihs.wolgateway.tunnel.controller

import com.yihs.wolgateway.tunnel.adapter.TunnelRuntime
import com.yihs.wolgateway.tunnel.TunnelLog
import com.yihs.wolgateway.tunnel.model.TunnelRuntimeConfig
import com.yihs.wolgateway.tunnel.model.TunnelState
import com.yihs.wolgateway.tunnel.secure.TunnelTokenStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    fun saveToken(token: String): Boolean = runCatching {
        tokenStore.replace(token)
        _state.value = _state.value.copy(
            status = TunnelState.DISCONNECTED,
            tokenPreview = maskToken(token),
            error = null,
        )
    }.isSuccess

    fun connect(originAddress: String) {
        scope.launch {
            lifecycleMutex.withLock {
                val token = tokenStore.read()
                if (token == null) {
                    _state.value = _state.value.copy(status = TunnelState.NOT_CONFIGURED)
                    return@withLock
                }
                _state.value = _state.value.copy(status = TunnelState.CONNECTING, error = null)
                try {
                    if (!runtime.isRunning()) {
                        runtime.start(TunnelRuntimeConfig(token, originAddress))
                    }
                    _state.value = _state.value.copy(status = TunnelState.CONNECTED)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    TunnelLog.error("connect failed error=${error.safeMessage()}")
                    _state.value = _state.value.copy(
                        status = TunnelState.ERROR,
                        error = error.toControllerError(),
                    )
                }
            }
        }
    }

    fun reconnect(originAddress: String) {
        scope.launch {
            lifecycleMutex.withLock {
                _state.value = _state.value.copy(status = TunnelState.RECONNECTING, error = null)
                runCatching { runtime.stop() }
                val token = tokenStore.read()
                if (token == null) {
                    _state.value = _state.value.copy(status = TunnelState.NOT_CONFIGURED)
                    return@withLock
                }
                try {
                    runtime.start(TunnelRuntimeConfig(token, originAddress))
                    _state.value = _state.value.copy(status = TunnelState.CONNECTED)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    TunnelLog.error("reconnect failed error=${error.safeMessage()}")
                    _state.value = _state.value.copy(
                        status = TunnelState.ERROR,
                        error = error.toControllerError(),
                    )
                }
            }
        }
    }

    fun disconnect() {
        scope.launch {
            lifecycleMutex.withLock {
                runCatching { runtime.stop() }
                _state.value = _state.value.copy(status = TunnelState.DISCONNECTED, error = null)
            }
        }
    }

    private fun initialState(): TunnelControllerState {
        val token = tokenStore.read()
        return TunnelControllerState(
            status = if (token == null) TunnelState.NOT_CONFIGURED else TunnelState.DISCONNECTED,
            tokenPreview = token?.let(::maskToken) ?: "••••••••",
        )
    }

    private fun maskToken(token: String): String = "${token.take(TOKEN_PREVIEW_LENGTH)}••••••••"

    private companion object {
        const val TOKEN_PREVIEW_LENGTH = 6
    }
}

data class TunnelControllerState(
    val status: TunnelState,
    val tokenPreview: String,
    val error: TunnelControllerError? = null,
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
