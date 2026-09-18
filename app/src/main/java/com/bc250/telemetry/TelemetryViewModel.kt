package com.bc250.telemetry

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val POLL_INTERVAL_MS = 700L

sealed interface ConnectionState {
    data object Searching : ConnectionState
    data class Connected(val host: String) : ConnectionState
    data object NotFound : ConnectionState
}

class TelemetryViewModel(application: Application) : AndroidViewModel(application) {
    private val discovery = DeviceDiscovery(application)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Searching)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _telemetry = MutableStateFlow<Telemetry?>(null)
    val telemetry: StateFlow<Telemetry?> = _telemetry.asStateFlow()

    init {
        startConnectAndPoll()
    }

    fun retry() {
        _connectionState.value = ConnectionState.Searching
        startConnectAndPoll()
    }

    private fun startConnectAndPoll() {
        viewModelScope.launch {
            val host = discovery.discover()
            if (host == null) {
                _connectionState.value = ConnectionState.NotFound
                return@launch
            }
            _connectionState.value = ConnectionState.Connected(host)
            val client = TelemetryClient(host)
            var misses = 0
            while (true) {
                val data = client.fetchTelemetry()
                if (data != null) {
                    misses = 0
                    _telemetry.value = data
                } else {
                    misses++
                    // A handful of consecutive failures means the board moved,
                    // rebooted, or dropped off Wi-Fi -> re-run discovery.
                    if (misses >= 5) {
                        _connectionState.value = ConnectionState.Searching
                        startConnectAndPoll()
                        return@launch
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }
}
