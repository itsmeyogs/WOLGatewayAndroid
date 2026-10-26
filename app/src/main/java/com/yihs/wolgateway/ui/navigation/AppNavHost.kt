package com.yihs.wolgateway.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yihs.wolgateway.ui.screen.dashboard.DashboardScreen

@Composable
fun AppNavHost() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        DashboardScreen(modifier = Modifier.padding(innerPadding))
    }
}
