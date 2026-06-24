package tech.path2ai.epos.terminal

import android.content.Context

/**
 * Small helper around SharedPreferences for payment-related toggles.
 *
 * Currently just "Allow tipping" — when on, each card sale tells the terminal
 * to show the customer-facing tip prompt before the card tap. Off by default
 * would be fine too; we default to ON so fresh installs demo the full feature.
 *
 * Kept deliberately tiny (plain functions, not a class / flow) so the
 * Settings toggle and the CardPaymentScreen can read/write without any
 * DI plumbing.
 */
object PaymentSettings {

    private const val PREFS_NAME = "epos_payment_settings"
    private const val KEY_ALLOW_TIPPING = "allow_tipping"
    private const val KEY_ALLOW_PREAUTH = "allow_preauth"
    private const val KEY_TAB_CAP_PENCE = "tab_cap_pence"

    fun isTippingAllowed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ALLOW_TIPPING, /* default */ true)
    }

    fun setTippingAllowed(context: Context, allowed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ALLOW_TIPPING, allowed).apply()
    }

    /**
     * Whether pre-authorization (open a tab / hold, adjust, complete, void) is
     * offered in the UI. Mirrors the emulator's `Config -> Pre-auth` toggle and a
     * real terminal's `Merchant.PreAuthEnabled`. When off, the Pre-auth entry
     * point is hidden. Defaults ON so fresh installs demo the feature.
     */
    fun isPreAuthAllowed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ALLOW_PREAUTH, /* default */ true)
    }

    fun setPreAuthAllowed(context: Context, allowed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ALLOW_PREAUTH, allowed).apply()
    }

    /**
     * Upper limit (minor units) a bar/café tab may accrue to before more items
     * are blocked — independent of the pre-auth hold (the tab can exceed the
     * hold; it's reconciled at close). Defaults to £200.
     */
    fun tabCapPence(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_TAB_CAP_PENCE, /* default £200 */ 20_000)
    }

    fun setTabCapPence(context: Context, pence: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_TAB_CAP_PENCE, pence).apply()
    }
}
