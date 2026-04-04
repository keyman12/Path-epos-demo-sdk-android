package tech.path2ai.epos.terminal

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simulated OCPay P400 terminal adapter for development/testing.
 * Returns deterministic approved responses without real hardware.
 */
class OCPayTerminalAdapter : PaymentTerminalAdapter {

    override val adapterName = "OCPay P400 (simulated)"

    private val _connectionState = MutableStateFlow<TerminalConnectionState>(TerminalConnectionState.Disconnected)
    override val connectionState: StateFlow<TerminalConnectionState> = _connectionState.asStateFlow()

    override suspend fun connect() {
        _connectionState.value = TerminalConnectionState.Connecting
        delay(1_400)
        _connectionState.value = TerminalConnectionState.Connected
    }

    override suspend fun disconnect() {
        _connectionState.value = TerminalConnectionState.Disconnected
    }

    override suspend fun scanForDevices(): List<TerminalDeviceInfo> = emptyList()
    override suspend fun connectToDevice(id: String) {}

    override suspend fun submitSale(request: TerminalSaleRequest): TerminalSaleResponse {
        delay(2_500)
        val authCode = "%06d".format((100_000..999_999).random())
        val txnRef = "OCP-${(System.currentTimeMillis() / 1000) % 1_000_000}-${(1_000..9_999).random()}"
        val timestamp = java.time.Instant.now().toString()

        return TerminalSaleResponse(
            authorised = true,
            authorisationCode = authCode,
            cardScheme = "VISA",
            maskedPan = "****1234",
            terminalReference = txnRef,
            cardReceiptData = TerminalCardReceipt(
                status = "APPROVED",
                timestamp = timestamp,
                txnRef = txnRef,
                terminalId = "OC-TILL-01",
                merchantId = "OC-MERCHANT-001",
                authorisationCode = authCode,
                verificationMethod = "CONTACTLESS",
                aid = "A0000000031010",
                entryMode = "CONTACTLESS",
                maskedPan = "****1234",
                cardScheme = "VISA"
            ),
            failureReason = null
        )
    }

    override suspend fun submitRefund(request: TerminalRefundRequest): TerminalRefundResponse {
        delay(1_500)
        val refundRef = "OCP-REF-${(System.currentTimeMillis() / 1000) % 1_000_000}-${(1_000..9_999).random()}"
        return TerminalRefundResponse(
            succeeded = true,
            refundReference = refundRef,
            failureReason = null
        )
    }

    override suspend fun getTransactionStatus(reference: String): TerminalTransactionStatus {
        delay(500)
        return TerminalTransactionStatus(
            reference = reference,
            state = "APPROVED",
            amountPence = 0,
            timestamp = System.currentTimeMillis()
        )
    }
}
