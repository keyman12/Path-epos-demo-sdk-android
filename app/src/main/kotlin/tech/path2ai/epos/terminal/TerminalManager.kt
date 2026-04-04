package tech.path2ai.epos.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TerminalManager(private val adapter: PaymentTerminalAdapter) : ViewModel() {

    val connectionState: StateFlow<TerminalConnectionState> = adapter.connectionState

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<TerminalDeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<TerminalDeviceInfo>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun connect() {
        viewModelScope.launch {
            try { adapter.connect() } catch (_: Exception) { }
        }
    }

    fun disconnect() {
        viewModelScope.launch { adapter.disconnect() }
    }

    fun scanForDevices() {
        viewModelScope.launch {
            _isScanning.value = true
            _discoveredDevices.value = emptyList()
            try {
                _discoveredDevices.value = adapter.scanForDevices()
            } catch (_: Exception) { }
            _isScanning.value = false
        }
    }

    fun connectToDevice(device: TerminalDeviceInfo) {
        viewModelScope.launch {
            try { adapter.connectToDevice(device.id) } catch (_: Exception) { }
        }
    }

    suspend fun submitSale(request: TerminalSaleRequest): TerminalSaleResponse {
        _isBusy.value = true
        return try {
            adapter.submitSale(request)
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun submitRefund(request: TerminalRefundRequest): TerminalRefundResponse {
        _isBusy.value = true
        return try {
            adapter.submitRefund(request)
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun getTransactionStatus(reference: String): TerminalTransactionStatus {
        return adapter.getTransactionStatus(reference)
    }

    val isConnected: Boolean
        get() = connectionState.value is TerminalConnectionState.Connected

    val adapterName: String get() = adapter.adapterName

    val connectionLabel: String
        get() = when (connectionState.value) {
            is TerminalConnectionState.Disconnected -> "Disconnected"
            is TerminalConnectionState.Connecting -> "Connecting…"
            is TerminalConnectionState.Connected -> "Connected"
            is TerminalConnectionState.Unavailable -> "Unavailable: ${(connectionState.value as TerminalConnectionState.Unavailable).reason}"
        }
}
