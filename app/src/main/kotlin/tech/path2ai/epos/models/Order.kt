package tech.path2ai.epos.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PaymentMethod { @SerialName("cash") CASH, @SerialName("card") CARD }

@Serializable
enum class OrderStatus { @SerialName("completed") COMPLETED, @SerialName("declined") DECLINED, @SerialName("refunded") REFUNDED }

@Serializable
enum class OrderType { @SerialName("sale") SALE, @SerialName("refund") REFUND }

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
    /**
     * Base (pre-tip) amount in minor units. Null on legacy orders persisted
     * before tipping support — callers should fall back to [amountPence].
     */
    val baseAmountPence: Int? = null,
    /** Customer-added tip, minor units. Null or 0 = no tip. */
    val tipAmountPence: Int? = null
) {
    val formattedAmount: String
        get() = "£%.2f".format(amountPence / 100.0)

    val cardDisplay: String?
        get() = if (cardScheme != null && cardLastFour != null) "$cardScheme ****$cardLastFour" else null

    val canRefund: Boolean
        get() = status == OrderStatus.COMPLETED && orderType == OrderType.SALE && terminalReference != null

    /** True when this order carries a non-zero customer tip. */
    val hasTip: Boolean
        get() = (tipAmountPence ?: 0) > 0

    val formattedTip: String
        get() = "£%.2f".format((tipAmountPence ?: 0) / 100.0)
}
