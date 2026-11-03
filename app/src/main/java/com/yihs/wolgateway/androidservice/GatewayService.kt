package com.yihs.wolgateway.androidservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yihs.wolgateway.R
import com.yihs.wolgateway.gateway.server.GatewayServer
import com.yihs.wolgateway.gateway.server.ServerState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.yihs.wolgateway.tunnel.controller.TunnelController
import com.yihs.wolgateway.tunnel.model.TunnelState

@AndroidEntryPoint
class GatewayService : Service() {
    @Inject lateinit var coordinator: GatewayCoordinator
    @Inject lateinit var gatewayServer: GatewayServer
    @Inject lateinit var tunnelController: TunnelController
    @Inject lateinit var serviceState: GatewayServiceState

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var connectivityManager: ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            serviceScope.launch { coordinator.onNetworkLost() }
        }

        override fun onAvailable(network: Network) {
            serviceScope.launch { coordinator.reconnectTunnelIfEnabled() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceState.setRunning(true)
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        serviceScope.launch {
            combine(gatewayServer.state, tunnelController.state) { apiState, tunnelState ->
                apiState == ServerState.RUNNING && tunnelState.status in setOf(
                    TunnelState.CONNECTED,
                    TunnelState.CONNECTING,
                    TunnelState.RECONNECTING,
                )
            }.collect(::updateNotification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        serviceScope.launch {
            try {
                if (!coordinator.startFromPreferences()) stopSelf()
            } catch (_: Exception) {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceState.setRunning(false)
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        coordinator.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.gateway_service_notification_api))
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private fun updateNotification(tunnelActive: Boolean) {
        val text = getString(
            if (tunnelActive) R.string.gateway_service_notification_api_tunnel
            else R.string.gateway_service_notification_api,
        )
        // Android 13 may suppress this notification when permission/channel access is
        // disabled. That visibility state must not affect the service coordinator.
        runCatching {
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(text)
                    .setOngoing(true)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .build(),
            )
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.gateway_service_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        const val ACTION_STOP = "com.yihs.wolgateway.action.STOP_SERVICE"
        private const val CHANNEL_ID = "gateway_service"
        private const val NOTIFICATION_ID = 1001

        fun start(context: android.content.Context) {
            ContextCompat.startForegroundService(context, Intent(context, GatewayService::class.java))
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, GatewayService::class.java))
        }
    }
}
