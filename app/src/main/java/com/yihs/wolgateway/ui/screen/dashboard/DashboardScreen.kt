package com.yihs.wolgateway.ui.screen.dashboard

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: FoundationViewModel = hiltViewModel(),
) {
    Text(
        text = viewModel.title,
        modifier = modifier.padding(horizontal = 16.dp),
    )
}
