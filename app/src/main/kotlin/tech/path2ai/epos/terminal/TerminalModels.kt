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
    val operatorId: String,
    /**
     * When true, the SDK tells the terminal to show the customer-facing tip
     * prompt before the card tap. The result's `tipAmountPence` reflects
     * what the customer picked (or 0 if they chose "No tip").
     * Has no effect when the emulator has tipping switched off locally.
     */
    val promptForTip: Boolean = false
)

data class TerminalSaleResponse(
    val authorised: Boolean,
    val authorisationCode: String? = null,
    val cardScheme: String? = null,
    val maskedPan: String? = null,
    val terminalReference: String? = null,
    val cardReceiptData: TerminalCardReceipt? = null,
    val failureReason: String? = null,
    /** Base (pre-tip) amount in minor units. Defaults to the request amount. */
    val baseAmountPence: Int = 0,
    /** Customer-added tip in minor units. 0 if no tip (or tipping wasn't prompted). */
    val tipAmountPence: Int = 0,
    /** Card-charged total in minor units (= base + tip). */
    val totalAmountPence: Int = 0,
    /** Preset percentage × 10 (100 = 10%, 150 = 15%, 200 = 20%); null if not a preset. */
    val tipPercentX10: Int? = null,
    /**
     * True when the customer walked away from the tip prompt (result state
     * was CUSTOMER_TIMEOUT). The UI should offer Retry / Cancel, not the
     * usual "declined" flow.
     */
    val customerTimedOut: Boolean = false
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
    /** Total amount in minor units (incl. tip if any). */
    val amountMinor: Int,
    val currency: String,
    val type: TerminalTransactionType,
    val status: TerminalTransactionLogStatus,
    val reqId: String? = null,
    val transactionId: String? = null,
    val isCash: Boolean = false,
    val refundedAtMillis: Long? = null,
    /**
     * Base (pre-tip) amount in minor units. Null on legacy entries written
     * before tipping support landed — callers should fall back to amountMinor.
     */
    val baseAmountMinor: Int? = null,
    /** Customer-added tip, minor units. Null or 0 = no tip. */
    val tipAmountMinor: Int? = null
) {
    /** Display: "Cash" for cash transactions, else masked PAN. */
    val cardMasked: String
        get() = if (isCash) "Cash" else "**** **** **** $cardLastFour"

    val amountMajor: Double
        get() = amountMinor / 100.0

    val formattedAmount: String
        get() = "\u00A3${"%.2f".format(amountMajor)}"

    /** True when this entry has a non-zero customer tip. */
    val hasTip: Boolean
        get() = (tipAmountMinor ?: 0) > 0

    /** Tip in major units (pounds). Zero when no tip. */
    val tipMajor: Double
        get() = (tipAmountMinor ?: 0) / 100.0

    val formattedTip: String
        get() = "\u00A3${"%.2f".format(tipMajor)}"

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

