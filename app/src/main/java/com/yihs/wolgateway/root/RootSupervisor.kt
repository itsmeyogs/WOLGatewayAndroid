package com.yihs.wolgateway.root

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RootSupervisorState { STOPPED, STARTING, RUNNING, RESTARTING, FALLBACK }

/** Root lifecycle boundary. No arbitrary shell execution is exposed. */
@Singleton
class RootSupervisor @Inject constructor(
    private val detector: RootCapabilityDetector,
    private val executor: RootCommandExecutor,
) {
    private val lock = Any()
    var state: RootSupervisorState = RootSupervisorState.STOPPED
        private set
    private val _state = MutableStateFlow(state)
    val stateFlow: StateFlow<RootSupervisorState> = _state.asStateFlow()
    private var port = 8080
    private val scope = CoroutineScope(Dispatchers.IO)
    private var watchdog: Job? = null

    fun start(consentGiven: Boolean, apiPort: Int = 8080): Boolean = synchronized(lock) {
        if (state == RootSupervisorState.RUNNING || state == RootSupervisorState.STARTING) return true
        if (!consentGiven || detector.detect() != RootCapability.AVAILABLE || !executor.verify()) {
            state = RootSupervisorState.FALLBACK
            _state.value = state
            return false
        }
        state = RootSupervisorState.STARTING
        _state.value = state
        port = apiPort
        if (!executor.startGatewayService() || !executor.isGatewayServiceRunning()) {
            state = RootSupervisorState.FALLBACK
            _state.value = state
            return false
        }
        state = RootSupervisorState.RUNNING
        _state.value = state
        watchdog?.cancel()
        watchdog = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                if (state != RootSupervisorState.RUNNING) break
                if (!executor.isGatewayServiceRunning() || !executor.isApiReady(port)) {
                    state = RootSupervisorState.RESTARTING
                    _state.value = state
                    if (!restartWithBackoff()) {
                    state = RootSupervisorState.FALLBACK
                    _state.value = state
                    break
                    }
                    state = RootSupervisorState.RUNNING
                    _state.value = state
                }
            }
        }
        return true
    }

    private suspend fun restartWithBackoff(): Boolean {
        repeat(MAX_RESTART_ATTEMPTS) { attempt ->
            delay(RESTART_BACKOFF_MS * (attempt + 1))
            if (executor.startGatewayService() && executor.isGatewayServiceRunning()) return true
        }
        return false
    }

    fun stop() = synchronized(lock) {
        watchdog?.cancel()
        watchdog = null
        if (state != RootSupervisorState.STOPPED && state != RootSupervisorState.FALLBACK) {
            executor.stopGatewayService()
        }
        state = RootSupervisorState.STOPPED
        _state.value = state
    }

    private companion object {
        const val WATCHDOG_INTERVAL_MS = 5_000L
        const val RESTART_BACKOFF_MS = 1_000L
        const val MAX_RESTART_ATTEMPTS = 3
    }
}
