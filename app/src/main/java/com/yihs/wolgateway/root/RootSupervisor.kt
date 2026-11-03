package com.yihs.wolgateway.root

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RootSupervisorState { STOPPED, STARTING, RUNNING, RESTARTING, FALLBACK }

/** Root lifecycle boundary. No arbitrary shell execution is exposed. */
@Singleton
class RootSupervisor internal constructor(
    private val detector: RootCapabilityDetector,
    private val executor: RootCommandExecutor,
    private val config: Config,
) {
    @Inject
    constructor(detector: RootCapabilityDetector, executor: RootCommandExecutor) : this(detector, executor, Config())

    private val lock = Any()
    var state: RootSupervisorState = RootSupervisorState.STOPPED
        private set
    private val _state = MutableStateFlow(state)
    val stateFlow: StateFlow<RootSupervisorState> = _state.asStateFlow()
    private var port = 8080
    private val scope = CoroutineScope(config.dispatcher)
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
                delay(config.watchdogIntervalMs)
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
        repeat(config.maxRestartAttempts) { attempt ->
            delay(config.restartBackoffMs * (attempt + 1))
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

    internal data class Config(
        val dispatcher: CoroutineContext = Dispatchers.IO,
        val watchdogIntervalMs: Long = 5_000L,
        val restartBackoffMs: Long = 1_000L,
        val maxRestartAttempts: Int = 3,
    )
}
