package tech.path2ai.epos.managers

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import tech.path2ai.epos.models.*

/**
 * Persists bar/café tabs. Mirrors [OrderManager] — a StateFlow backed by the
 * shared "epos" SharedPreferences (JSON). Tabs survive app restarts; the backing
 * card hold lives on the terminal (volatile on the emulator's ring buffer).
 */
class TabManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefsKey = "open_tabs"

    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()

    init { load() }

    /** Tabs still open, newest first. */
    val openTabs: List<Tab> get() = _tabs.value.filter { it.status == TabStatus.OPEN }

    fun tabById(id: String): Tab? = _tabs.value.firstOrNull { it.id == id }

    /** Open a new tab once the pre-auth hold has been approved. Returns it. */
    fun openTab(name: String, preAuthPence: Int, terminalReference: String?): Tab {
        val tab = Tab(name = name, preAuthPence = preAuthPence, terminalReference = terminalReference)
        _tabs.value = listOf(tab) + _tabs.value
        save()
        return tab
    }

    /** Append a round of items to an open tab. */
    fun addItems(tabId: String, items: List<OrderLineItem>) {
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) it.copy(lineItems = it.lineItems + items) else it
        }
        save()
    }

    /** Mark a tab's hold as released (after a void in an over-tab close). */
    fun markHoldReleased(tabId: String) {
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) it.copy(holdReleased = true, terminalReference = null) else it
        }
        save()
    }

    /** Close a settled tab, linking the CompletedOrder that recorded the settlement. */
    fun closeTab(tabId: String, closeOrderReference: String?) {
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) it.copy(
                status = TabStatus.CLOSED,
                closedAt = System.currentTimeMillis(),
                closeOrderReference = closeOrderReference
            ) else it
        }
        save()
    }

    private fun save() {
        val prefs = context.getSharedPreferences("epos", Context.MODE_PRIVATE)
        prefs.edit().putString(prefsKey, json.encodeToString(_tabs.value)).apply()
    }

    private fun load() {
        val prefs = context.getSharedPreferences("epos", Context.MODE_PRIVATE)
        val data = prefs.getString(prefsKey, null) ?: return
        try {
            _tabs.value = json.decodeFromString<List<Tab>>(data)
        } catch (_: Exception) { }
    }
}
