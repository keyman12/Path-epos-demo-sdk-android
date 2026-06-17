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
    // Terminal login is shared across backends — every backend now performs a
    // connect-time login (Verifone for real, the emulator as a faithful stand-in).
    private const val KEY_LOGIN_USERNAME = "terminal_login_username"
    private const val KEY_LOGIN_PASSWORD = "terminal_login_password"
    private const val KEY_LOGIN_SHIFT = "terminal_login_shift"
    private const val KEY_REFUND_PASSWORD = "verifone_refund_password"

    const val DEFAULT_VERIFONE_HOST = "192.168.1.88"
    // Proven test-terminal login (Path-PSDK-TestHarnesses gotcha 7: the test
    // VP100 accepts user/password123/shift123; the emulator accepts anything).
    // Real estates override these in Settings → Terminal Backend.
    const val DEFAULT_LOGIN_USERNAME = "user"
    const val DEFAULT_LOGIN_PASSWORD = "password123"
    const val DEFAULT_LOGIN_SHIFT = "shift123"

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

    /** Terminal login username — used by every backend's connect-time login. */
    fun loginUsername(context: Context): String =
        prefs(context).getString(KEY_LOGIN_USERNAME, DEFAULT_LOGIN_USERNAME) ?: DEFAULT_LOGIN_USERNAME

    fun setLoginUsername(context: Context, username: String) {
        prefs(context).edit().putString(KEY_LOGIN_USERNAME, username.trim()).apply()
    }

    /** Terminal login password. Not trimmed — passwords may be space-sensitive. */
    fun loginPassword(context: Context): String =
        prefs(context).getString(KEY_LOGIN_PASSWORD, DEFAULT_LOGIN_PASSWORD) ?: DEFAULT_LOGIN_PASSWORD

    fun setLoginPassword(context: Context, password: String) {
        prefs(context).edit().putString(KEY_LOGIN_PASSWORD, password).apply()
    }

    /** Terminal login shift identifier. */
    fun loginShift(context: Context): String =
        prefs(context).getString(KEY_LOGIN_SHIFT, DEFAULT_LOGIN_SHIFT) ?: DEFAULT_LOGIN_SHIFT

    fun setLoginShift(context: Context, shift: String) {
        prefs(context).edit().putString(KEY_LOGIN_SHIFT, shift.trim()).apply()
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
