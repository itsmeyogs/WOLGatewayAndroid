package com.yihs.wolgateway.androidservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import com.yihs.wolgateway.data.preferences.GatewayStartupPreferences
import com.yihs.wolgateway.data.preferences.gatewayPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val preferences: DataStore<Preferences> = appContext.gatewayPreferences()
                val values = preferences.data.first()
                if (values[GatewayStartupPreferences.AUTO_START_API] == true ||
                    values[GatewayStartupPreferences.AUTO_START_TUNNEL] == true
                ) {
                    GatewayService.start(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
