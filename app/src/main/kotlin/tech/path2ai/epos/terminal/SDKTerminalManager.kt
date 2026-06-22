package tech.path2ai.epos.terminal

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tech.path2ai.sdk.PathTerminal
import tech.path2ai.sdk.PathTerminalEvent
import tech.path2ai.sdk.ConnectionState
import tech.path2ai.sdk.core.*
import tech.path2ai.sdk.emulator.BLEPathTerminalAdapter
import tech.path2ai.sdk.emulator.TcpPathTerminalAdapter
import tech.path2ai.sdk.psdk.VerifonePSDKAdapter
import tech.path2ai.sdk.psdk.VerifoneTerminalConfig
import tech.path2ai.sdk.diagnostics.PathDiagnostics
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "PathTerminal"
private const val LOG_MAX_ENTRIES = 500
private const val LOG_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
private const val TIMEOUT_THRESHOLD_MS = 30_000L
private const val PREFS_NAME = "path_terminal_prefs"
private const val KEY_TRANSACTION_LOG = "TerminalTransactionLog"
private const val KEY_LAST_DEVICE_ID = "PathLastConnectedTerminalDeviceId"

/**
 * Real implementation wrapping PathTerminal + BLEPathTerminalAdapter.
 * Collects SDK events and maps them to app-level StateFlows consumed by Compose UI.
 */
class SDKTerminalManager(
    private val context: Context
) : TerminalConnectionManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // The backend switch: the adapter is chosen from TerminalBackendSettings
    // and can be swapped at runtime via applyBackend() — everything above
    // PathTerminal is backend-agnostic.
    private var backend: TerminalBackend = TerminalBackendSettings.backend(context)
    private var adapter: PathTerminalAdapter = buildAdapter(backend)
    internal var terminal = PathTerminal(adapter = adapter)
        private set
    private var eventCollectionJob: Job? = null

    private fun buildAdapter(backend: TerminalBackend): PathTerminalAdapter = when (backend) {
        TerminalBackend.EMULATOR_BLE -> BLEPathTerminalAdapter(
            context = context,
            sdkVersion = "0.1.1",
            adapterVersion = "0.1.1",
            deviceNameFilter = null,
            // Same terminal-login credentials as the real backend — the emulator
            // performs the same connect-time login handshake (protocol v1.3).
            username = TerminalBackendSettings.loginUsername(context),
            password = TerminalBackendSettings.loginPassword(context),
            shift = TerminalBackendSettings.loginShift(context),
            onLog = { msg -> scope.launch { log(msg) } }
        )
        TerminalBackend.EMULATOR_WIFI -> TcpPathTerminalAdapter(
            host = TerminalBackendSettings.emulatorHost(context),
            username = TerminalBackendSettings.loginUsername(context),
            password = TerminalBackendSettings.loginPassword(context),
            shift = TerminalBackendSettings.loginShift(context),
            onLog = { msg -> scope.launch { log(msg) } }
        )
        TerminalBackend.VERIFONE -> VerifonePSDKAdapter(
            context = context,
            config = VerifoneTerminalConfig(
                host = TerminalBackendSettings.verifoneHost(context),
                username = TerminalBackendSettings.loginUsername(context),
                password = TerminalBackendSettings.loginPassword(context),
                shift = TerminalBackendSettings.loginShift(context),
                refundPassword = TerminalBackendSettings.refundPassword(context)
            ),
            onLog = { msg -> scope.launch { log(msg) } }
        )
    }

    override fun applyBackend() {
        val newBackend = TerminalBackendSettings.backend(context)
        log("Switching terminal backend: ${backend.name} -> ${newBackend.name}")
        // Cleanly drop the old backend first (one client at a time on real terminals).
        currentSaleJob?.cancel(); currentRefundJob?.cancel(); currentVoidJob?.cancel()
        timeoutCheckJob?.cancel()
        eventCollectionJob?.cancel()
        val old = terminal
        scope.launch {
            try { old.disconnect() } catch (_: Exception) { }
        }
        backend = newBackend
        adapter = buildAdapter(newBackend)
        terminal = PathTerminal(adapter = adapter)
        _discoveredDevices.value = emptyList()
        _connectionState.value = TerminalConnectionState.Disconnected
        isReady = false
        startEventCollection()
        updateBluetoothState()
        log("Backend ready: ${integrationKind}")
    }

    override fun applyCustomerDisplayBranding() {
        val t = terminal
        scope.launch {
            try {
                val content = if (CustomerDisplaySettings.isEnabled(context)) {
                    CustomerDisplayContent(
                        imageBytes = CustomerDisplaySettings.logoBytes(context),
                        caption = CustomerDisplaySettings.caption(context).ifBlank { null }
                    )
                } else null
                t.setIdleBranding(content)
                log(
                    if (content != null) "Customer display: logo applied (attract mode on)"
                    else "Customer display: branding cleared"
                )
            } catch (e: Exception) {
                log("Customer display: failed to apply branding — ${e.message}")
            }
        }
    }

    /**
     * Apply the typed host AND connect, in one step. For the IP-based backends
     * (Emulator Wi-Fi / Verifone) there's nothing to "scan" — you typed the
     * address — so this rebuilds the adapter with the saved host and connects
     * straight to the single TCP endpoint. A wrong/unreachable host surfaces as
     * Unavailable + a log line, instead of the button looking dead.
     */
    fun applyHostAndConnect() {
        applyBackend()                 // rebuild the adapter with the saved host; resets state
        val t = terminal
        _isScanning.value = false      // no BLE-style "scanning" for a typed-in host
        _connectionState.value = TerminalConnectionState.Connecting
        log("Connecting to ${integrationKind}...")
        scope.launch {
            try {
                // Connect straight to the single TCP endpoint — don't surface it as a
                // "discovered device" (that produced a redundant card on the settings screen).
                val device = t.discoverDevices().firstOrNull()
                if (device == null) {
                    _connectionState.value = TerminalConnectionState.Unavailable("No terminal at that address")
                    log("No terminal found at the configured host")
                    return@launch
                }
                t.connect(device)
                _connectionState.value = TerminalConnectionState.Connected
                isReady = true
                lastAckTime = System.currentTimeMillis()
                log("Connected to ${device.name}.")
                // Paint the merchant logo on the customer screen (attract mode).
                applyCustomerDisplayBranding()
            } catch (e: Exception) {
                _lastError.value = e.message
                _connectionState.value = TerminalConnectionState.Unavailable(e.message ?: "Connect failed")
                log("Connect failed: ${e.message}")
            }
        }
    }

    private var currentSaleJob: Job? = null
    private var currentRefundJob: Job? = null
    private var currentVoidJob: Job? = null
    private var pendingRefundOriginalEntryId: String? = null
    private var pendingVoidOriginalEntryId: String? = null
    private var lastAckTime: Long? = null
    private var timeoutCheckJob: Job? = null

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ---- StateFlows ----

    private val _connectionState = MutableStateFlow<TerminalConnectionState>(TerminalConnectionState.Disconnected)
    override val connectionState: StateFlow<TerminalConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<TerminalDeviceItem>>(emptyList())
    override val discoveredDevices: StateFlow<List<TerminalDeviceItem>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastResult = MutableStateFlow<Map<String, Any>?>(null)
    override val lastResult: StateFlow<Map<String, Any>?> = _lastResult.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _transactionLog = MutableStateFlow<List<TerminalTransactionLogEntry>>(emptyList())
    override val transactionLog: StateFlow<List<TerminalTransactionLogEntry>> = _transactionLog.asStateFlow()

    private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    override val logEntries: StateFlow<List<LogEntry>> = _logEntries.asStateFlow()

    private val _showTimeoutPrompt = MutableStateFlow(false)
    override val showTimeoutPrompt: StateFlow<Boolean> = _showTimeoutPrompt.asStateFlow()

    private val _isBluetoothPoweredOn = MutableStateFlow(true)
    override val isBluetoothPoweredOn: StateFlow<Boolean> = _isBluetoothPoweredOn.asStateFlow()

    override var isReady: Boolean = false
        private set

    override val sdkVersion: String? = "0.1.1"
    override val protocolVersion: String? = "0.1"
    override val integrationKind: String
        get() = when (backend) {
            TerminalBackend.EMULATOR_BLE -> "path_sdk_emulator_ble"
            TerminalBackend.EMULATOR_WIFI -> "path_sdk_emulator_wifi"
            TerminalBackend.VERIFONE -> "path_sdk_verifone"
        }

    private var _lastWireRequestId: String? = null
    override val lastWireRequestId: String? get() = _lastWireRequestId

    /**
     * Record the wire request id used by a UI-driven transaction (sale/refund/
     * void via AppTerminalManager.submit*), so the Developer Diagnostics
     * "Last req_id" + GetTransactionStatus reflect the normal till flow — not
     * just the manual startSale/startRefund paths.
     */
    fun noteWireRequestId(id: String) { _lastWireRequestId = id }

    // ---- Init ----

    init {
        updateBluetoothState()
        loadTransactionLog()
        startEventCollection()

        val savedDeviceId = prefs.getString(KEY_LAST_DEVICE_ID, null)
        if (savedDeviceId != null) {
            log("Hint: last terminal id $savedDeviceId. BLE does not auto-reconnect after app restart.")
        }
        Log.d(TAG, "SDKTerminalManager ready")
    }

    // ---- Event collection ----

    private fun startEventCollection() {
        eventCollectionJob?.cancel()
        eventCollectionJob = scope.launch {
            terminal.events.collect { event ->
                when (event) {
                    is PathTerminalEvent.ConnectionStateChanged -> {
                        logConnectionEvent(event.state)
                        mapConnectionState(event.state)
                    }
                    is PathTerminalEvent.DeviceDiscovered -> {
                        val item = TerminalDeviceItem(
                            id = event.device.id,
                            name = event.device.name,
                            rssi = event.device.rssi
                        )
                        val current = _discoveredDevices.value.toMutableList()
                        val idx = current.indexOfFirst { it.id == item.id }
                        if (idx >= 0) current[idx] = item else current.add(item)
                        _discoveredDevices.value = current
                    }
                    is PathTerminalEvent.TransactionStateChanged -> {
                        val state = event.state
                        if (state == TransactionState.PENDING_DEVICE || state == TransactionState.CARD_READ) {
                            lastAckTime = System.currentTimeMillis()
                        }
                    }
                    is PathTerminalEvent.Error -> {
                        _lastError.value = event.error.message
                        log("Error: ${event.error.message}")
                    }
                    else -> { /* ignore */ }
                }
            }
        }
    }

    private fun mapConnectionState(conn: ConnectionState) {
        // Scanning is the ONLY state that drives the spinner; every other event clears it.
        // (The TCP connect path emits Scanning then Connected — without this the spinner stuck
        // on after "Terminal Connected", because nothing reset isScanning.)
        _isScanning.value = (conn == ConnectionState.Scanning)
        when (conn) {
            ConnectionState.Idle -> {
                if (terminal.isConnected) {
                    _connectionState.value = TerminalConnectionState.Connected
                    isReady = true
                } else {
                    _connectionState.value = TerminalConnectionState.Disconnected
                    isReady = false
                }
            }
            ConnectionState.Scanning -> {
                if (terminal.isConnected) isReady = true
            }
            ConnectionState.Connecting -> {
                _connectionState.value = TerminalConnectionState.Connecting
            }
            ConnectionState.Connected -> {
                _connectionState.value = TerminalConnectionState.Connected
                isReady = true
                lastAckTime = System.currentTimeMillis()
            }
            ConnectionState.Disconnected -> {
                _connectionState.value = TerminalConnectionState.Disconnected
                isReady = false
            }
            is ConnectionState.Error -> {
                _connectionState.value = TerminalConnectionState.Unavailable(conn.message)
                _lastError.value = conn.message
            }
        }
    }

    private fun logConnectionEvent(conn: ConnectionState) {
        val line = when (conn) {
            ConnectionState.Idle -> "event connection: idle (isConnected=${terminal.isConnected})"
            ConnectionState.Scanning -> "event connection: scanning"
            ConnectionState.Connecting -> "event connection: connecting"
            ConnectionState.Connected -> "event connection: connected"
            ConnectionState.Disconnected -> "event connection: disconnected"
            is ConnectionState.Error -> "event connection: error ${conn.message}"
        }
        log(line)
    }

    private fun updateBluetoothState() {
        val btAdapter = BluetoothAdapter.getDefaultAdapter()
        val enabled = btAdapter?.isEnabled ?: false
        _isBluetoothPoweredOn.value = enabled
        // Bluetooth only matters on the BLE backend — TCP backends (emulator
        // Wi-Fi mode, Verifone) work with Bluetooth off entirely.
        if (backend != TerminalBackend.EMULATOR_BLE) {
            if (_connectionState.value is TerminalConnectionState.Unavailable) {
                _connectionState.value = if (terminal.isConnected) {
                    TerminalConnectionState.Connected
                } else {
                    TerminalConnectionState.Disconnected
                }
            }
            return
        }
        if (!enabled) {
            _connectionState.value = TerminalConnectionState.Unavailable("Bluetooth unavailable")
            isReady = false
        } else if (_connectionState.value is TerminalConnectionState.Unavailable) {
            _connectionState.value = if (terminal.isConnected) {
                TerminalConnectionState.Connected
            } else {
                TerminalConnectionState.Disconnected
            }
        }
    }

    // ---- Logging ----

    private fun log(message: String) {
        pruneLogsIfNeeded()
        val entries = _logEntries.value.toMutableList()
        entries.add(LogEntry(text = message))
        if (entries.size > LOG_MAX_ENTRIES) {
            entries.subList(0, entries.size - LOG_MAX_ENTRIES).clear()
        }
        _logEntries.value = entries
        Log.d(TAG, message)
    }

    private fun pruneLogsIfNeeded() {
        val cutoff = System.currentTimeMillis() - LOG_MAX_AGE_MS
        val pruned = _logEntries.value.filter { it.dateMillis >= cutoff }
        if (pruned.size < _logEntries.value.size) {
            _logEntries.value = pruned
        }
    }

    override fun getLogsForCopy(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return _logEntries.value.joinToString("\n") { entry ->
            "${fmt.format(Date(entry.dateMillis))}  ${entry.text}"
        }
    }

    override fun clearLogs() {
        _logEntries.value = emptyList()
    }

    override fun pruneLogs() {
        pruneLogsIfNeeded()
    }

    // ---- Transaction log persistence ----

    private val json = Json { ignoreUnknownKeys = true }

    private fun loadTransactionLog() {
        val raw = prefs.getString(KEY_TRANSACTION_LOG, null) ?: return
        try {
            _transactionLog.value = json.decodeFromString<List<TerminalTransactionLogEntry>>(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load transaction log: ${e.message}")
        }
    }

    private fun saveTransactionLog() {
        try {
            val encoded = json.encodeToString(_transactionLog.value)
            prefs.edit().putString(KEY_TRANSACTION_LOG, encoded).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save transaction log: ${e.message}")
        }
    }

    // ---- Connection ----

    override fun startScan() {
        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        log("Scanning for BLE devices...")
        scope.launch {
            try {
                val found = terminal.discoverDevices()
                _discoveredDevices.value = found.map {
                    TerminalDeviceItem(id = it.id, name = it.name, rssi = it.rssi)
                }
                _isScanning.value = false
                if (terminal.isConnected) {
                    _connectionState.value = TerminalConnectionState.Connected
                    isReady = true
                } else {
                    _connectionState.value = TerminalConnectionState.Disconnected
                }
            } catch (e: Exception) {
                _isScanning.value = false
                _lastError.value = e.message
                _connectionState.value = TerminalConnectionState.Unavailable(e.message ?: "Scan failed")
                log("Scan failed: ${e.message}")
            }
        }
    }

    override fun stopScan() {
        _isScanning.value = false
        if (terminal.isConnected) {
            _connectionState.value = TerminalConnectionState.Connected
            isReady = true
        } else {
            _connectionState.value = TerminalConnectionState.Disconnected
        }
    }

    override fun connect(device: TerminalDeviceItem) {
        _connectionState.value = TerminalConnectionState.Connecting
        log("Connecting to ${device.name}...")
        scope.launch {
            // Android BLE is unreliable on the first connectGatt() call — it often produces
            // no callbacks at all (times out silently). Auto-retry up to 3 times so the user
            // never has to press Connect twice.
            val maxAttempts = 3
            var lastError: Exception? = null
            for (attempt in 1..maxAttempts) {
                try {
                    val discovered = DiscoveredDevice(id = device.id, name = device.name, rssi = device.rssi)
                    terminal.connect(discovered)
                    _connectionState.value = TerminalConnectionState.Connected
                    isReady = true
                    lastAckTime = System.currentTimeMillis()
                    log("Connected (attempt $attempt).")
                    prefs.edit().putString(KEY_LAST_DEVICE_ID, device.id).apply()
                    applyCustomerDisplayBranding()
                    return@launch
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < maxAttempts) {
                        log("Connect attempt $attempt failed (${e.message}) — retrying in 300ms...")
                        delay(300)
                    } else {
                        log("Connect failed after $maxAttempts attempts: ${e.message}")
                    }
                }
            }
            _lastError.value = lastError?.message
            _connectionState.value = TerminalConnectionState.Unavailable(lastError?.message ?: "Connect failed")
        }
    }

    override fun disconnect() {
        stop()
    }

    override fun stop() {
        currentSaleJob?.cancel()
        currentRefundJob?.cancel()
        currentVoidJob?.cancel()
        currentSaleJob = null
        currentRefundJob = null
        currentVoidJob = null
        _showTimeoutPrompt.value = false
        _lastWireRequestId = null
        timeoutCheckJob?.cancel()
        scope.launch {
            try {
                terminal.disconnect()
            } catch (_: Exception) { }
            isReady = false
            _connectionState.value = TerminalConnectionState.Disconnected
        }
    }

    // ---- Payments ----

    override fun clearForNewTransaction() {
        _lastResult.value = null
        lastAckTime = null
        log("---")
    }

    override fun startSale(amountMinor: Int, currency: String, tipMinor: Int?) {
        clearForNewTransaction()
        if (!isReady) {
            log("Not connected. Connect to terminal first.")
            return
        }
        val envelope = RequestEnvelope.create(sdkVersion = "0.1.1", adapterVersion = "0.1.1")
        _lastWireRequestId = envelope.requestId
        val request = TransactionRequest.sale(
            amountMinor = amountMinor,
            currency = currency,
            tipMinor = tipMinor,
            envelope = envelope
        )
        log("Sending Sale request...")
        lastAckTime = System.currentTimeMillis()
        startTimeoutTracking()

        currentSaleJob = scope.launch {
            try {
                val result = terminal.sale(request)
                applyResult(result, "Sale")
            } catch (e: PathError) {
                _lastError.value = e.message
                _lastResult.value = errorResultMap(e.message ?: "Sale error", "Sale", amountMinor, currency)
                val recov = if (e.recoverable) " [recoverable]" else ""
                log("Sale error$recov [${e.code}]: ${e.message}")
            } catch (e: Exception) {
                _lastError.value = e.message
                _lastResult.value = errorResultMap(e.message ?: "Sale failed", "Sale", amountMinor, currency)
                log("Sale failed: ${e.message}")
            } finally {
                timeoutCheckJob?.cancel()
            }
        }
    }

    override fun startRefund(
        amountMinor: Int,
        currency: String,
        originalTransactionId: String?,
        originalReqId: String?,
        originalEntryId: String?
    ) {
        clearForNewTransaction()
        if (!isReady) {
            log("Not connected. Connect to terminal first.")
            return
        }
        pendingRefundOriginalEntryId = originalEntryId
        val envelope = RequestEnvelope.create(sdkVersion = "0.1.1", adapterVersion = "0.1.1")
        _lastWireRequestId = envelope.requestId
        val request = TransactionRequest.refund(
            amountMinor = amountMinor,
            currency = currency,
            originalTransactionId = originalTransactionId,
            originalRequestId = originalReqId,
            envelope = envelope
        )
        log("Sending Refund request...")
        lastAckTime = System.currentTimeMillis()
        startTimeoutTracking()

        currentRefundJob = scope.launch {
            try {
                val result = terminal.refund(request)
                applyResult(result, "Refund")
            } catch (e: PathError) {
                _lastError.value = e.message
                _lastResult.value = errorResultMap(e.message ?: "Refund error", "Refund", amountMinor, currency)
                val recov = if (e.recoverable) " [recoverable]" else ""
                log("Refund error$recov [${e.code}]: ${e.message}")
            } catch (e: Exception) {
                _lastError.value = e.message
                _lastResult.value = errorResultMap(e.message ?: "Refund failed", "Refund", amountMinor, currency)
                log("Refund failed: ${e.message}")
            } finally {
                timeoutCheckJob?.cancel()
            }
        }
    }

    override fun startVoid(
        originalTransactionId: String,
        originalEntryId: String?
    ) {
        clearForNewTransaction()
        if (!isReady) {
            log("Not connected. Connect to terminal first.")
            return
        }
        pendingVoidOriginalEntryId = originalEntryId
        val envelope = RequestEnvelope.create(sdkVersion = "0.1.1", adapterVersion = "0.1.1")
        _lastWireRequestId = envelope.requestId
        val request = TransactionRequest.voidTransaction(
            originalTransactionId = originalTransactionId,
            envelope = envelope
        )
        log("Sending Void request...")
        lastAckTime = System.currentTimeMillis()

        currentVoidJob = scope.launch {
            try {
                val result = terminal.voidTransaction(request)
                applyResult(result, "Void")
            } catch (e: PathError) {
                _lastError.value = e.message
                _lastResult.value = errorResultMap(e.message ?: "Void error", "Void", 0, "GBP")
                val recov = if (e.recoverable) " [recoverable]" else ""
                log("Void error$recov [${e.code}]: ${e.message}")
            } catch (e: Exception) {
                _lastError.value = e.message
                _lastResult.value = errorResultMap(e.message ?: "Void failed", "Void", 0, "GBP")
                log("Void failed: ${e.message}")
            }
        }
    }

    private fun applyResult(result: TransactionResult, cmd: String) {
        val statusStr = when (result.state) {
            TransactionState.APPROVED, TransactionState.REFUNDED -> "approved"
            TransactionState.REVERSED -> "reversed"
            TransactionState.DECLINED -> "declined"
            TransactionState.TIMED_OUT -> "timed_out"
            TransactionState.FAILED -> "failed"
            else -> "declined"
        }
        _lastResult.value = mapOf(
            "txn_id" to (result.transactionId ?: result.requestId),
            "req_id" to result.requestId,
            "status" to statusStr,
            "amount" to result.amountMinor,
            "currency" to result.currency,
            "card_last_four" to (result.cardLastFour ?: "0000")
        )
        val errorMsg = result.error?.message
        if (errorMsg != null) {
            log("Result: $statusStr -- $errorMsg")
        } else {
            log("Result: $statusStr")
        }

        val txnStatus = when (result.state) {
            TransactionState.APPROVED, TransactionState.REFUNDED, TransactionState.REVERSED ->
                TerminalTransactionLogStatus.SUCCESS
            TransactionState.TIMED_OUT -> TerminalTransactionLogStatus.TIMED_OUT
            else -> TerminalTransactionLogStatus.DECLINE
        }
        val entry = TerminalTransactionLogEntry(
            urn = "URN-${UUID.randomUUID().toString().take(8).uppercase()}",
            cardLastFour = result.cardLastFour ?: "0000",
            amountMinor = result.amountMinor,
            currency = result.currency,
            type = when (cmd) {
                "Refund" -> TerminalTransactionType.REFUND
                "Void" -> TerminalTransactionType.VOID
                else -> TerminalTransactionType.SALE
            },
            status = txnStatus,
            reqId = result.requestId,
            transactionId = result.transactionId,
            isCash = false
        )
        val log = _transactionLog.value.toMutableList()
        log.add(0, entry)

        // Mark original sale as refunded if this was a linked refund
        if (cmd == "Refund" && pendingRefundOriginalEntryId != null) {
            val origIdx = log.indexOfFirst { it.id == pendingRefundOriginalEntryId }
            if (origIdx >= 0) {
                log[origIdx] = log[origIdx].withRefundedAt(System.currentTimeMillis())
            }
            pendingRefundOriginalEntryId = null
        }
        // Mark original sale as voided if this was a successful void
        if (cmd == "Void" && pendingVoidOriginalEntryId != null) {
            if (result.state == TransactionState.REVERSED) {
                val origIdx = log.indexOfFirst { it.id == pendingVoidOriginalEntryId }
                if (origIdx >= 0) {
                    log[origIdx] = log[origIdx].withVoidedAt(System.currentTimeMillis())
                }
            }
            pendingVoidOriginalEntryId = null
        }
        _transactionLog.value = log
        saveTransactionLog()
    }

    private fun errorResultMap(error: String, cmd: String, amountMinor: Int, currency: String): Map<String, Any> {
        return mapOf(
            "txn_id" to UUID.randomUUID().toString(),
            "req_id" to UUID.randomUUID().toString(),
            "status" to "declined",
            "amount" to amountMinor,
            "currency" to currency,
            "card_last_four" to "0000",
            "error" to error
        )
    }

    // ---- Timeout tracking ----

    private fun startTimeoutTracking() {
        timeoutCheckJob?.cancel()
        timeoutCheckJob = scope.launch {
            while (isActive) {
                delay(5_000)
                val ack = lastAckTime
                if (ack != null && System.currentTimeMillis() - ack > TIMEOUT_THRESHOLD_MS) {
                    _showTimeoutPrompt.value = true
                }
            }
        }
    }

    // ---- Cancel / Continue ----

    override fun continueWaiting() {
        _showTimeoutPrompt.value = false
        log("Continuing to wait for terminal...")
    }

    override fun cancelCurrentOperation() {
        currentSaleJob?.cancel()
        currentRefundJob?.cancel()
        currentSaleJob = null
        currentRefundJob = null
        _showTimeoutPrompt.value = false
        timeoutCheckJob?.cancel()
        log("Cancelling in-flight operation...")
        scope.launch {
            try {
                terminal.cancelActiveTransaction()
                log("Cancel sent to terminal.")
            } catch (e: PathError) {
                log("Cancel: [${e.code}] ${e.message}")
            } catch (e: Exception) {
                log("Cancel: ${e.message}")
            }
        }
    }

    // ---- Cash ----

    override fun addCashTransaction(amountMinor: Int, currency: String) {
        val urn = "URN-${UUID.randomUUID().toString().take(8).uppercase()}"
        val entry = TerminalTransactionLogEntry(
            urn = urn,
            cardLastFour = "",
            amountMinor = amountMinor,
            currency = currency,
            type = TerminalTransactionType.SALE,
            status = TerminalTransactionLogStatus.SUCCESS,
            isCash = true
        )
        val log = _transactionLog.value.toMutableList()
        log.add(0, entry)
        _transactionLog.value = log
        saveTransactionLog()
    }

    override fun recordCashRefund(originalEntry: TerminalTransactionLogEntry) {
        val log = _transactionLog.value.toMutableList()
        val idx = log.indexOfFirst { it.id == originalEntry.id }
        if (idx < 0) return

        val refundedAt = System.currentTimeMillis()
        log[idx] = originalEntry.withRefundedAt(refundedAt)

        val refundEntry = TerminalTransactionLogEntry(
            urn = "URN-${UUID.randomUUID().toString().take(8).uppercase()}",
            dateMillis = refundedAt,
            cardLastFour = "",
            amountMinor = originalEntry.amountMinor,
            currency = originalEntry.currency,
            type = TerminalTransactionType.REFUND,
            status = TerminalTransactionLogStatus.SUCCESS,
            isCash = true
        )
        log.add(0, refundEntry)
        _transactionLog.value = log
        saveTransactionLog()
    }

    override fun clearTransactionLog() {
        _transactionLog.value = emptyList()
        prefs.edit().remove(KEY_TRANSACTION_LOG).apply()
    }

    // ---- Receipts ----

    override suspend fun getReceiptData(transactionId: String): ReceiptData? {
        return try {
            terminal.getReceiptData(transactionId)
        } catch (e: Exception) {
            log("Receipt fetch failed: ${e.message}")
            null
        }
    }

    // ---- Diagnostics ----

    override suspend fun queryTransactionStatus(requestId: String?) {
        val rid = requestId ?: _lastWireRequestId
        if (rid == null) {
            log("GetTransactionStatus: no request id -- run a Sale or Refund first.")
            return
        }
        if (!isReady) {
            log("GetTransactionStatus: not connected.")
            return
        }
        log("GetTransactionStatus... req_id=$rid")
        try {
            val result = terminal.getTransactionStatus(rid)
            log("GetTransactionStatus: state=${result.state} txn_id=${result.transactionId ?: "--"}")
        } catch (e: PathError) {
            log("GetTransactionStatus: [${e.code}] ${e.message}")
        } catch (e: Exception) {
            log("GetTransactionStatus: ${e.message}")
        }
    }

    override fun buildSupportBundleSnapshot(): SupportBundleSnapshotV1 {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val logFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val recent = _logEntries.value.takeLast(120).map { entry ->
            "${logFmt.format(Date(entry.dateMillis))}  ${entry.text}"
        }
        return SupportBundleSnapshotV1(
            generatedAtUtc = fmt.format(Date()),
            integration = integrationKind,
            sdkVersion = sdkVersion,
            protocolVersion = protocolVersion,
            connectionState = _connectionState.value.diagnosticsLabel,
            isReady = isReady,
            isBluetoothPoweredOn = _isBluetoothPoweredOn.value,
            lastError = _lastError.value,
            logLineCount = _logEntries.value.size,
            recentLogLines = recent,
            transactionLogCount = _transactionLog.value.size
        )
    }
}
