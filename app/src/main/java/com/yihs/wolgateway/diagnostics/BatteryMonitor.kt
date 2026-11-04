package com.yihs.wolgateway.diagnostics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class BatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun state(): Flow<DiagnosticsBatteryInfo> = callbackFlow {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(read(intent)).isSuccess
            }
        }
        context.registerReceiver(receiver, filter)
        trySend(read(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))))
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    private fun read(intent: Intent?): DiagnosticsBatteryInfo {
        val batteryIntent = if (intent?.action == Intent.ACTION_BATTERY_CHANGED) intent
        else context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = when {
            status == BatteryManager.BATTERY_STATUS_CHARGING -> true
            status == BatteryManager.BATTERY_STATUS_FULL && plugged != 0 -> true
            status == BatteryManager.BATTERY_STATUS_DISCHARGING ||
                status == BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        }
        return DiagnosticsBatteryInfo(
            levelPercent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null,
            charging = charging,
            batterySaver = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode,
        )
    }
}
