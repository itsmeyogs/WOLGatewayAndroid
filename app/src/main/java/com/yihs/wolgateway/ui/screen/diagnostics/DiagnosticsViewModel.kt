package com.yihs.wolgateway.ui.screen.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yihs.wolgateway.diagnostics.DiagnosticsProvider
import com.yihs.wolgateway.diagnostics.DiagnosticsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    provider: DiagnosticsProvider,
) : ViewModel() {
    val snapshot: StateFlow<DiagnosticsSnapshot?> = provider.snapshot()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
