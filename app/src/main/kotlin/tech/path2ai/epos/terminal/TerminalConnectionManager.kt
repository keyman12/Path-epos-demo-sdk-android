package tech.path2ai.epos.terminal

import kotlinx.coroutines.flow.StateFlow
import tech.path2ai.sdk.core.ReceiptData

/**
 * Protocol-style interface for terminal connection -- implemented by SDKTerminalManager (Path SDK).
 * Both the real SDK manager and any test mock implement this surface.
 */
interface TerminalConnectionManager {

    // ---- Observable state (Compose screens collect these) ----

    val connectionState: StateFlow<TerminalConnectionState>
    val discoveredDevices: StateFlow<List<TerminalDeviceItem>>
    val isScanning: StateFlow<Boolean>
    val lastResult: StateFlow<Map<String, Any>?>
    val lastError: StateFlow<String?>
    val transactionLog: StateFlow<List<TerminalTransactionLogEntry>>
    val logEntries: StateFlow<List<LogEntry>>
    val showTimeoutPrompt: StateFlow<Boolean>
    val isBluetoothPoweredOn: StateFlow<Boolean>

    val isReady: Boolean
    val sdkVersion: String?
    val protocolVersion: String?
    val integrationKind: String
    val lastWireRequestId: String?

    // ---- Connection ----

    fun startScan()
    fun stopScan()
    fun connect(device: TerminalDeviceItem)
    fun disconnect()
    fun stop()

    // ---- Payments ----

    fun startSale(amountMinor: Int, currency: String, tipMinor: Int? = null)
    fun startRefund(
        amountMinor: Int,
        currency: String,
        originalTransactionId: String? = null,
        originalReqId: String? = null,
        originalEntryId: String? = null
    )

    // ---- Cancel / Continue ----

    fun cancelCurrentOperation()
    fun continueWaiting()
    fun clearForNewTransaction()

    // ---- Cash ----

    fun addCashTransaction(amountMinor: Int, currency: String)
    fun recordCashRefund(originalEntry: TerminalTransactionLogEntry)

    // ---- Transaction log ----

    fun clearTransactionLog()

    // ---- Receipts ----

    suspend fun getReceiptData(transactionId: String): ReceiptData?

    // ---- Diagnostics ----

    suspend fun queryTransactionStatus(requestId: String? = null)
    fun getLogsForCopy(): String
    fun clearLogs()
    fun pruneLogs()
    fun buildSupportBundleSnapshot(): SupportBundleSnapshotV1

    fun getSupportBundle(): String {
        return SupportBundleSnapshotV1.encodePrettyString(buildSupportBundleSnapshot())
    }
}

/** A single device discovered during BLE scanning. */
data class TerminalDeviceItem(
    val id: String,
    val name: String,
    val rssi: Int = 0
)

/** Timestamped diagnostic log entry. */
data class LogEntry(
    val dateMillis: Long = System.currentTimeMillis(),
    val text: String
)

/** Extension on TerminalConnectionState for diagnostics label. */
val TerminalConnectionState.diagnosticsLabel: String
    get() = when (this) {
        is TerminalConnectionState.Disconnected -> "disconnected"
        is TerminalConnectionState.Connecting -> "connecting"
        is TerminalConnectionState.Connected -> "connected"
        is TerminalConnectionState.Unavailable -> "unavailable: $reason"
    }
