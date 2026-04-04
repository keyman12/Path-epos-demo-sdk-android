package tech.path2ai.epos.terminal

import kotlinx.coroutines.flow.StateFlow

/**
 * Communication contract between the EPOS and a payment terminal.
 * Implementations handle device discovery, connection, and transactions.
 *
 * To replace the current integration:
 * 1. Create a new class implementing this interface
 * 2. Inject it into TerminalManager at the app entry point
 */
interface PaymentTerminalAdapter {
    val adapterName: String
    val connectionState: StateFlow<TerminalConnectionState>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun scanForDevices(): List<TerminalDeviceInfo>
    suspend fun connectToDevice(id: String)
    suspend fun submitSale(request: TerminalSaleRequest): TerminalSaleResponse
    suspend fun submitRefund(request: TerminalRefundRequest): TerminalRefundResponse
    suspend fun getTransactionStatus(reference: String): TerminalTransactionStatus
}
