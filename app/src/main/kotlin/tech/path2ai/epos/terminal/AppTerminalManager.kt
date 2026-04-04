package tech.path2ai.epos.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.first

/**
 * ViewModel that wraps SDKTerminalManager and exposes its state to Compose.
 * Uses Kotlin delegation to forward all TerminalConnectionManager methods.
 * Compose screens collect StateFlows from this ViewModel.
 */
class AppTerminalManager(
    private val sdkManager: SDKTerminalManager
) : ViewModel(), TerminalConnectionManager by sdkManager {

    // Any additional UI-specific state can be added here.
    // The delegation pattern (by sdkManager) forwards all TerminalConnectionManager
    // properties and methods automatically.

    /** Convenience name for the adapter, used in Settings screens. */
    val adapterName: String get() = sdkManager.integrationKind

    /** Human-readable label for current connection state. */
    val connectionLabel: String
        get() = when (connectionState.value) {
            is TerminalConnectionState.Disconnected -> "Disconnected"
            is TerminalConnectionState.Connecting -> "Connecting\u2026"
            is TerminalConnectionState.Connected -> "Connected"
            is TerminalConnectionState.Unavailable -> "Unavailable: ${(connectionState.value as TerminalConnectionState.Unavailable).reason}"
        }

    /** No-arg connect: starts BLE scan (auto-connects to first discovered device). */
    fun connect() {
        sdkManager.startScan()
    }

    /** Bridge: calls the Path SDK directly for a sale transaction. */
    suspend fun submitSale(request: TerminalSaleRequest): TerminalSaleResponse {
        val envelope = tech.path2ai.sdk.core.RequestEnvelope.create(
            merchantReference = request.orderReference,
            sdkVersion = "0.1.0",
            adapterVersion = "0.1.0"
        )
        val txnRequest = tech.path2ai.sdk.core.TransactionRequest.sale(
            amountMinor = request.amountPence,
            currency = request.currencyCode,
            envelope = envelope
        )
        val result = sdkManager.terminal.sale(txnRequest)
        return when (result.state) {
            tech.path2ai.sdk.core.TransactionState.APPROVED -> {
                // Fetch receipt data from the terminal after approval
                val txnId = result.transactionId
                val cardReceipt = if (result.receiptAvailable && txnId != null) {
                    try {
                        val receiptData = sdkManager.getReceiptData(txnId)
                        receiptData?.merchantReceipt?.let { fields ->
                            TerminalCardReceipt(
                                status = fields.status,
                                timestamp = fields.timestamp,
                                txnRef = fields.txnRef,
                                terminalId = fields.terminalId,
                                merchantId = fields.merchantId,
                                authorisationCode = fields.authCode,
                                verificationMethod = fields.verification,
                                aid = fields.aid,
                                entryMode = fields.entryMode,
                                maskedPan = fields.maskedPan,
                                cardScheme = fields.cardScheme
                            )
                        }
                    } catch (e: Exception) { null }
                } else null

                TerminalSaleResponse(
                    authorised = true,
                    authorisationCode = result.transactionId,
                    cardScheme = result.cardLastFour?.let { "Card" },
                    maskedPan = result.cardLastFour?.let { "****$it" },
                    terminalReference = result.transactionId,
                    cardReceiptData = cardReceipt,
                    failureReason = null
                )
            }
            else -> TerminalSaleResponse(
                authorised = false,
                failureReason = result.error?.message ?: "Transaction ${result.state.value}"
            )
        }
    }

    /** Bridge: calls the Path SDK directly for a refund transaction. */
    suspend fun submitRefund(request: TerminalRefundRequest): TerminalRefundResponse {
        val envelope = tech.path2ai.sdk.core.RequestEnvelope.create(
            sdkVersion = "0.1.0",
            adapterVersion = "0.1.0"
        )
        val txnRequest = tech.path2ai.sdk.core.TransactionRequest.refund(
            amountMinor = request.amountPence,
            currency = request.currencyCode,
            originalTransactionId = request.originalTerminalReference,
            envelope = envelope
        )
        val result = sdkManager.terminal.refund(txnRequest)
        return when (result.state) {
            tech.path2ai.sdk.core.TransactionState.REFUNDED -> TerminalRefundResponse(
                succeeded = true,
                refundReference = result.transactionId
            )
            else -> TerminalRefundResponse(
                succeeded = false,
                failureReason = result.error?.message ?: "Refund ${result.state.value}"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel is being destroyed; disconnect cleanly
        sdkManager.stop()
    }
}

/**
 * Factory for creating AppTerminalManager with an SDKTerminalManager dependency.
 * Use with ViewModelProvider in Activity/Fragment:
 *
 *   val appManager: AppTerminalManager by viewModels {
 *       AppTerminalManagerFactory(sdkManager)
 *   }
 */
class AppTerminalManagerFactory(
    private val sdkManager: SDKTerminalManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppTerminalManager::class.java)) {
            return AppTerminalManager(sdkManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
