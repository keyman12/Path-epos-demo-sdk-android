package tech.path2ai.epos.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PaymentMethod { @SerialName("cash") CASH, @SerialName("card") CARD }

@Serializable
enum class OrderStatus { @SerialName("completed") COMPLETED, @SerialName("declined") DECLINED, @SerialName("refunded") REFUNDED, @SerialName("voided") VOIDED }

@Serializable
enum class OrderType { @SerialName("sale") SALE, @SerialName("refund") REFUND, @SerialName("void") VOID }

@Serializable
data class OrderLineItem(
    val name: String,
    val quantity: Int,
    val unitPrice: Int
) {
    val lineTotal: Int get() = quantity * unitPrice
}

@Serializable
data class CompletedOrder(
    val id: String = java.util.UUID.randomUUID().toString(),
    val orderReference: String,
    val date: Long = System.currentTimeMillis(),
    val lineItems: List<OrderLineItem>,
    /** Total in minor units (incl. tip if any). */
    val amountPence: Int,
    val currencyCode: String = "GBP",
    val paymentMethod: PaymentMethod,
    val orderType: OrderType = OrderType.SALE,
    val cardLastFour: String? = null,
    val cardScheme: String? = null,
    val terminalReference: String? = null,
    val authCode: String? = null,
    var status: OrderStatus = OrderStatus.COMPLETED,
    var refundedAt: Long? = null,
    var voidedAt: Long? = null,
    /**
     * Base (pre-tip) amount in minor units. Null on legacy orders persisted
     * before tipping support — callers should fall back to [amountPence].
     */
    val baseAmountPence: Int? = null,
    /** Customer-added tip, minor units. Null or 0 = no tip. */
    val tipAmountPence: Int? = null,
    /**
     * Indices into [lineItems] already refunded, for whole-line item refunds.
     * Grows as the cashier refunds more lines over multiple sittings; when every
     * line is covered the sale flips to [OrderStatus.REFUNDED]. Always empty on
     * non-sale orders and on sales that were never item-refunded.
     */
    val refundedLineIndices: List<Int> = emptyList()
) {
    val formattedAmount: String
        get() = "£%.2f".format(amountPence / 100.0)

    val cardDisplay: String?
        get() = if (cardScheme != null && cardLastFour != null) "$cardScheme ****$cardLastFour" else null

    /** Line items not yet refunded (whole-line granularity). */
    val unrefundedLineIndices: List<Int>
        get() = lineItems.indices.filter { it !in refundedLineIndices }

    /** Some — but not all — lines refunded. The sale stays COMPLETED meanwhile. */
    val isPartiallyRefunded: Boolean
        get() = orderType == OrderType.SALE &&
                refundedLineIndices.isNotEmpty() &&
                refundedLineIndices.size < lineItems.size

    /** Value (minor units) of the lines already refunded. */
    val refundedLineValuePence: Int
        get() = refundedLineIndices.mapNotNull { lineItems.getOrNull(it)?.lineTotal }.sum()

    val canRefund: Boolean
        get() = status == OrderStatus.COMPLETED && orderType == OrderType.SALE &&
                terminalReference != null && unrefundedLineIndices.isNotEmpty()

    /**
     * Void = full reversal of an approved sale (no amount, no card tap).
     * A completed card sale with a terminal reference — but only before any
     * item has been refunded (a part-refunded sale can no longer be voided).
     */
    val canVoid: Boolean
        get() = status == OrderStatus.COMPLETED && orderType == OrderType.SALE &&
                terminalReference != null && refundedLineIndices.isEmpty()

    /** True when this order carries a non-zero customer tip. */
    val hasTip: Boolean
        get() = (tipAmountPence ?: 0) > 0

    val formattedTip: String
        get() = "£%.2f".format((tipAmountPence ?: 0) / 100.0)
}
