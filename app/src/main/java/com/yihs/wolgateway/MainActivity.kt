package com.yihs.wolgateway

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.yihs.wolgateway.ui.theme.WOLGatewayTheme
import com.yihs.wolgateway.ui.navigation.AppNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WOLGatewayTheme {
                AppNavHost()
            }
        }
    }
}
