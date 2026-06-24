package tech.path2ai.epos.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TabStatus { @SerialName("open") OPEN, @SerialName("closed") CLOSED }

/**
 * A bar/café tab: a long-lived OPEN order backed by a card pre-authorization
 * (hold). Items accrue over multiple rounds without touching the hold; at close
 * the bill is reconciled against the hold (capture if within it, else void +
 * standard sale). Persisted by [tech.path2ai.epos.managers.TabManager].
 */
@Serializable
data class Tab(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val openedAt: Long = System.currentTimeMillis(),
    /** The pre-authorized hold amount, minor units. */
    val preAuthPence: Int,
    /**
     * The pre-auth's terminal transactionId — the handle for complete/void.
     * Null once the hold has been released (see [holdReleased]).
     */
    val terminalReference: String? = null,
    /**
     * True after an over-tab close voided the hold but the follow-up standard
     * sale still has to run (or be retried). Stops a retry re-voiding a hold
     * that's already gone — a retry goes straight to the standard-sale path.
     */
    val holdReleased: Boolean = false,
    val lineItems: List<OrderLineItem> = emptyList(),
    val currencyCode: String = "GBP",
    val status: TabStatus = TabStatus.OPEN,
    val closedAt: Long? = null,
    /** The CompletedOrder reference created when the tab was settled. */
    val closeOrderReference: String? = null
) {
    /** Current bill, minor units (sum of all accrued line items). */
    val totalPence: Int get() = lineItems.sumOf { it.lineTotal }

    val formattedHold: String get() = "£%.2f".format(preAuthPence / 100.0)
    val formattedTotal: String get() = "£%.2f".format(totalPence / 100.0)

    /** Whether the current bill is within the held funds. */
    val withinHold: Boolean get() = totalPence <= preAuthPence
}
