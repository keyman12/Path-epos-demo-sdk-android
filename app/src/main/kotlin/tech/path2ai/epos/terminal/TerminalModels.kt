package tech.path2ai.epos.terminal

import kotlinx.serialization.Serializable
import java.util.Date
import java.util.UUID

sealed class TerminalConnectionState {
    data object Disconnected : TerminalConnectionState()
    data object Connecting : TerminalConnectionState()
    data object Connected : TerminalConnectionState()
    data class Unavailable(val reason: String) : TerminalConnectionState()
}

data class TerminalDeviceInfo(
    val id: String,
    val name: String
)

data class TerminalSaleRequest(
    val orderReference: String,
    val amountPence: Int,
    val currencyCode: String,
    val lineItems: List<TerminalLineItem>,
    val operatorId: String
)

data class TerminalSaleResponse(
    val authorised: Boolean,
    val authorisationCode: String? = null,
    val cardScheme: String? = null,
    val maskedPan: String? = null,
    val terminalReference: String? = null,
    val cardReceiptData: TerminalCardReceipt? = null,
    val failureReason: String? = null
)

data class TerminalRefundRequest(
    val originalTerminalReference: String,
    val amountPence: Int,
    val currencyCode: String,
    val orderReference: String
)

data class TerminalRefundResponse(
    val succeeded: Boolean,
    val refundReference: String? = null,
    val failureReason: String? = null
)

data class TerminalTransactionStatus(
    val reference: String,
    val state: String,
    val amountPence: Int,
    val timestamp: Long
)

data class TerminalLineItem(
    val description: String,
    val quantity: Int,
    val unitAmountPence: Int
) {
    val totalAmountPence: Int get() = quantity * unitAmountPence
}

data class TerminalCardReceipt(
    val status: String,
    val timestamp: String,
    val txnRef: String,
    val terminalId: String,
    val merchantId: String,
    val authorisationCode: String,
    val verificationMethod: String,
    val aid: String,
    val entryMode: String,
    val maskedPan: String,
    val cardScheme: String
)

sealed class TerminalAdapterError(message: String) : Exception(message) {
    class DeviceNotAvailable(message: String) : TerminalAdapterError(message)
    class ConnectionFailed(message: String) : TerminalAdapterError(message)
    class TransactionFailed(message: String) : TerminalAdapterError(message)
    class InvalidRequest(message: String) : TerminalAdapterError(message)
}

// ---------------------------------------------------------------------------
// SDK transaction log types
// ---------------------------------------------------------------------------

@Serializable
enum class TerminalTransactionType {
    SALE,
    REFUND
}

@Serializable
enum class TerminalTransactionLogStatus {
    SUCCESS,
    DECLINE,
    TIMED_OUT
}

@Serializable
data class TerminalTransactionLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val urn: String,
    val dateMillis: Long = System.currentTimeMillis(),
    val cardLastFour: String,
    val amountMinor: Int,
    val currency: String,
    val type: TerminalTransactionType,
    val status: TerminalTransactionLogStatus,
    val reqId: String? = null,
    val transactionId: String? = null,
    val isCash: Boolean = false,
    val refundedAtMillis: Long? = null
) {
    /** Display: "Cash" for cash transactions, else masked PAN. */
    val cardMasked: String
        get() = if (isCash) "Cash" else "**** **** **** $cardLastFour"

    val amountMajor: Double
        get() = amountMinor / 100.0

    val formattedAmount: String
        get() = "\u00A3${"%.2f".format(amountMajor)}"

    fun withRefundedAt(millis: Long): TerminalTransactionLogEntry =
        copy(refundedAtMillis = millis)
}

/** Redacted support bundle for diagnostics (no PAN or full card numbers). */
@Serializable
data class SupportBundleSnapshotV1(
    val bundleVersion: String = "1",
    val generatedAtUtc: String,
    val integration: String,
    val sdkVersion: String?,
    val protocolVersion: String?,
    val connectionState: String,
    val isReady: Boolean,
    val isBluetoothPoweredOn: Boolean,
    val lastError: String?,
    val logLineCount: Int,
    val recentLogLines: List<String>,
    val transactionLogCount: Int
) {
    companion object {
        fun encodePrettyString(snapshot: SupportBundleSnapshotV1): String {
            val json = kotlinx.serialization.json.Json {
                prettyPrint = true
            }
            return json.encodeToString(serializer(), snapshot)
        }
    }
}

