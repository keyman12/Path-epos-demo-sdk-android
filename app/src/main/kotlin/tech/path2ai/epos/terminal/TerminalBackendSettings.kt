package tech.path2ai.epos.terminal

import android.content.Context

/**
 * Which terminal backend the demo drives. The headline product story: an
 * EPOS integrates against the Path SDK + emulator, then flips this setting
 * when a real Verifone terminal ships — and everything just works.
 */
enum class TerminalBackend {
    /** Pico emulator over Bluetooth LE (the default). */
    EMULATOR_BLE,

    /** Pico emulator in Wi-Fi mode — TCP :9700, the IP shown on its welcome screen. */
    EMULATOR_WIFI,

    /** Real Verifone terminal (e.g. VP100) over the Verifone PSDK, TCP/IP. */
    VERIFONE
}

/**
 * SharedPreferences-backed backend selection (same tiny-object pattern as
 * [PaymentSettings]). The Settings screen writes these, then calls
 * TerminalConnectionManager.applyBackend() to rebuild the adapter.
 */
object TerminalBackendSettings {

    private const val PREFS_NAME = "epos_terminal_backend"
    private const val KEY_BACKEND = "backend"
    private const val KEY_EMULATOR_HOST = "emulator_host"
    private const val KEY_VERIFONE_HOST = "verifone_host"
    private const val KEY_REFUND_PASSWORD = "verifone_refund_password"

    const val DEFAULT_VERIFONE_HOST = "192.168.1.88"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun backend(context: Context): TerminalBackend =
        prefs(context).getString(KEY_BACKEND, null)
            ?.let { runCatching { TerminalBackend.valueOf(it) }.getOrNull() }
            ?: TerminalBackend.EMULATOR_BLE

    fun setBackend(context: Context, backend: TerminalBackend) {
        prefs(context).edit().putString(KEY_BACKEND, backend.name).apply()
    }

    /** Emulator IP for Wi-Fi mode (shown on the Pico's welcome screen). */
    fun emulatorHost(context: Context): String =
        prefs(context).getString(KEY_EMULATOR_HOST, "") ?: ""

    fun setEmulatorHost(context: Context, host: String) {
        prefs(context).edit().putString(KEY_EMULATOR_HOST, host.trim()).apply()
    }

    fun verifoneHost(context: Context): String =
        prefs(context).getString(KEY_VERIFONE_HOST, DEFAULT_VERIFONE_HOST) ?: DEFAULT_VERIFONE_HOST

    fun setVerifoneHost(context: Context, host: String) {
        prefs(context).edit().putString(KEY_VERIFONE_HOST, host.trim()).apply()
    }

    /**
     * Manager/refund password the Verifone adapter auto-answers the
     * terminal's refund PASSWORD prompt with. Test estates accept empty.
     */
    fun refundPassword(context: Context): String =
        prefs(context).getString(KEY_REFUND_PASSWORD, "") ?: ""

    fun setRefundPassword(context: Context, password: String) {
        prefs(context).edit().putString(KEY_REFUND_PASSWORD, password).apply()
    }
}
