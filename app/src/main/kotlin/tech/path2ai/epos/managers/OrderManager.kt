package tech.path2ai.epos.managers

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import tech.path2ai.epos.models.*

class OrderManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefsKey = "completed_orders"

    private val _orders = MutableStateFlow<List<CompletedOrder>>(emptyList())
    val orders: StateFlow<List<CompletedOrder>> = _orders.asStateFlow()

    init { loadOrders() }

    fun recordSale(
        orderReference: String,
        lineItems: List<OrderLineItem>,
        amountPence: Int,
        currencyCode: String,
        paymentMethod: PaymentMethod,
        cardLastFour: String? = null,
        cardScheme: String? = null,
        terminalReference: String? = null,
        authCode: String? = null
    ) {
        val order = CompletedOrder(
            orderReference = orderReference,
            lineItems = lineItems,
            amountPence = amountPence,
            currencyCode = currencyCode,
            paymentMethod = paymentMethod,
            cardLastFour = cardLastFour,
            cardScheme = cardScheme,
            terminalReference = terminalReference,
            authCode = authCode,
            status = OrderStatus.COMPLETED
        )
        _orders.value = listOf(order) + _orders.value
        saveOrders()
    }

    fun recordDeclinedSale(
        orderReference: String,
        lineItems: List<OrderLineItem>,
        amountPence: Int,
        currencyCode: String,
        paymentMethod: PaymentMethod
    ) {
        val order = CompletedOrder(
            orderReference = orderReference,
            lineItems = lineItems,
            amountPence = amountPence,
            currencyCode = currencyCode,
            paymentMethod = paymentMethod,
            status = OrderStatus.DECLINED
        )
        _orders.value = listOf(order) + _orders.value
        saveOrders()
    }

    fun markRefunded(orderId: String) {
        _orders.value = _orders.value.map {
            if (it.id == orderId) it.copy(status = OrderStatus.REFUNDED, refundedAt = System.currentTimeMillis())
            else it
        }
        saveOrders()
    }

    fun recordRefund(originalOrder: CompletedOrder, refundReference: String?) {
        val refund = CompletedOrder(
            orderReference = generateReference(),
            lineItems = originalOrder.lineItems,
            amountPence = originalOrder.amountPence,
            currencyCode = originalOrder.currencyCode,
            paymentMethod = originalOrder.paymentMethod,
            orderType = OrderType.REFUND,
            terminalReference = refundReference,
            status = OrderStatus.COMPLETED
        )
        _orders.value = listOf(refund) + _orders.value
        saveOrders()
    }

    fun clearHistory() {
        _orders.value = emptyList()
        saveOrders()
    }

    fun generateReference(): String = "OC-${(10000..99999).random()}"

    private fun saveOrders() {
        val prefs = context.getSharedPreferences("epos", Context.MODE_PRIVATE)
        prefs.edit().putString(prefsKey, json.encodeToString(_orders.value)).apply()
    }

    private fun loadOrders() {
        val prefs = context.getSharedPreferences("epos", Context.MODE_PRIVATE)
        val data = prefs.getString(prefsKey, null) ?: return
        try {
            _orders.value = json.decodeFromString<List<CompletedOrder>>(data)
        } catch (_: Exception) { }
    }
}
