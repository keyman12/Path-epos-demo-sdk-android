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

    fun isTippingAllowed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ALLOW_TIPPING, /* default */ true)
    }

    fun setTippingAllowed(context: Context, allowed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ALLOW_TIPPING, allowed).apply()
    }
}
