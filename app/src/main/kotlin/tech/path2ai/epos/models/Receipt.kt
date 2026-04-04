package tech.path2ai.epos.models

import tech.path2ai.epos.terminal.TerminalCardReceipt

data class ReceiptLineItem(
    val name: String,
    val quantity: Int,
    val unitPrice: Int
) {
    val lineTotal: Int get() = quantity * unitPrice
}

data class CardReceiptBlock(
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
) {
    companion object {
        fun from(receipt: TerminalCardReceipt) = CardReceiptBlock(
            status = receipt.status,
            timestamp = receipt.timestamp,
            txnRef = receipt.txnRef,
            terminalId = receipt.terminalId,
            merchantId = receipt.merchantId,
            authorisationCode = receipt.authorisationCode,
            verificationMethod = receipt.verificationMethod,
            aid = receipt.aid,
            entryMode = receipt.entryMode,
            maskedPan = receipt.maskedPan,
            cardScheme = receipt.cardScheme
        )
    }
}

data class FullReceipt(
    val merchantName: String,
    val merchantAddress: String,
    val orderNumber: String,
    val tillNumber: String,
    val cashierName: String,
    val orderDate: String,
    val lineItems: List<ReceiptLineItem>,
    val subtotal: Int,
    val vatAmount: Int,
    val total: Int,
    val currency: String = "GBP",
    val cardReceiptBlock: CardReceiptBlock? = null,
    val footerLines: List<String> = listOf("Thank you for visiting!", "path2ai.tech")
)
