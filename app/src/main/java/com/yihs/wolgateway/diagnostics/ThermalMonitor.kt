package com.yihs.wolgateway.diagnostics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.yihs.wolgateway.androidservice.GatewayServiceMode
import com.yihs.wolgateway.androidservice.GatewayServiceModeState
import com.yihs.wolgateway.root.RootCommandExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ThermalMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modeState: GatewayServiceModeState,
    private val rootExecutor: RootCommandExecutor,
) {
    fun state(): Flow<DiagnosticsThermalInfo> = modeState.effective.flatMapLatest { mode ->
        callbackFlow { register(mode == GatewayServiceMode.ROOT) }
    }.distinctUntilChanged()

    private suspend fun kotlinx.coroutines.channels.ProducerScope<DiagnosticsThermalInfo>.register(rootMode: Boolean) {
        val power = context.getSystemService(PowerManager::class.java)
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        var batteryIntent: Intent? = context.registerReceiver(null, batteryFilter)
        val rootSensors = if (rootMode) withContext(Dispatchers.IO) {
            runCatching { rootExecutor.thermalSensors().take(MAX_SENSORS) }.getOrDefault(emptyList())
        } else emptyList()
        val emit = {
            trySend(read(batteryIntent, power, rootMode, rootSensors)).isSuccess
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) batteryIntent = intent
                emit()
            }
        }
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        })
        val listener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager.OnThermalStatusChangedListener { emit() }
        } else null
        val executor: Executor = context.mainExecutor
        if (listener != null) power?.addThermalStatusListener(executor, listener)
        emit()
        awaitClose {
            context.unregisterReceiver(receiver)
            if (listener != null) power?.removeThermalStatusListener(listener)
        }
    }

    private fun read(
        intent: Intent?,
        power: PowerManager?,
        rootMode: Boolean,
        rootSensors: List<com.yihs.wolgateway.root.RootThermalReading>,
    ): DiagnosticsThermalInfo {
        val batteryTemperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }
            ?.div(10.0)
            ?.takeIf { it in MIN_C..MAX_C }
        val androidStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            power?.currentThermalStatus?.let(::thermalStatus)
        } else null
        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { power?.getThermalHeadroom(0)?.toDouble()?.takeIf { it >= 0.0 } }.getOrNull()
        } else null
        val sensors = buildList {
            batteryTemperature?.let { add(DiagnosticsThermalSensor("battery", "BATTERY", it, temperatureStatus(it), ThermalSource.ANDROID_PUBLIC)) }
            rootSensors.forEach { reading ->
                if (reading.name != "battery") add(DiagnosticsThermalSensor(reading.name, sensorType(reading.name), reading.temperatureCelsius, temperatureStatus(reading.temperatureCelsius), ThermalSource.ROOT_ALLOWLIST))
            }
        }.distinctBy { it.name }.sortedBy { it.name }
        val source = when {
            rootSensors.isNotEmpty() -> ThermalSource.ROOT_ALLOWLIST
            batteryTemperature != null || androidStatus != null || headroom != null -> ThermalSource.ANDROID_PUBLIC
            else -> ThermalSource.UNAVAILABLE
        }
        return DiagnosticsThermalInfo(
            source = source,
            mode = if (rootMode) DiagnosticsMode.ROOT else DiagnosticsMode.STANDARD,
            status = androidStatus ?: sensors.maxOfOrNull { it.status } ?: ThermalStatus.UNKNOWN,
            batteryTemperatureCelsius = batteryTemperature,
            thermalStatus = androidStatus?.name,
            thermalHeadroom = headroom,
            sensors = sensors,
            sampledAt = System.currentTimeMillis(),
            available = source != ThermalSource.UNAVAILABLE,
            safeError = if (rootMode && rootSensors.isEmpty() && source == ThermalSource.ANDROID_PUBLIC) "Root thermal sensors unavailable; using Android public data" else null,
        )
    }

    private companion object {
        const val MIN_C = -40.0
        const val MAX_C = 150.0
        const val MAX_SENSORS = 16

        fun thermalStatus(status: Int) = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NOMINAL
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.CRITICAL
            else -> ThermalStatus.UNKNOWN
        }

        fun temperatureStatus(celsius: Double) = when {
            celsius >= 70 -> ThermalStatus.CRITICAL
            celsius >= 60 -> ThermalStatus.SEVERE
            celsius >= 50 -> ThermalStatus.MODERATE
            celsius >= 45 -> ThermalStatus.LIGHT
            else -> ThermalStatus.NOMINAL
        }

        fun sensorType(name: String) = when {
            name.contains("batt") -> "BATTERY"
            name.contains("cpu") -> "CPU"
            name.contains("gpu") -> "GPU"
            name.contains("soc") -> "SOC"
            name.contains("skin") -> "SKIN"
            name.contains("modem") -> "MODEM"
            else -> "UNKNOWN"
        }
    }
}
