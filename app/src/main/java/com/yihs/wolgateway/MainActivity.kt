package com.yihs.wolgateway

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import com.yihs.wolgateway.androidservice.GatewayCoordinator
import com.yihs.wolgateway.androidservice.GatewayService
import kotlinx.coroutines.launch
import com.yihs.wolgateway.ui.theme.WOLGatewayTheme
import com.yihs.wolgateway.ui.navigation.AppNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var coordinator: GatewayCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            if (coordinator.hasDesiredRuntime()) {
                GatewayService.ensure(this@MainActivity)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
        enableEdgeToEdge()
        setContent {
            WOLGatewayTheme {
                AppNavHost()
            }
        }
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST = 100
    }
}
