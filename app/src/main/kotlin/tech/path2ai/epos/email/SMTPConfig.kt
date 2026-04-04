package tech.path2ai.epos.email

import android.content.Context

/**
 * SMTP configuration — mirrors iOS SMTPConfig / SMTPConfigView.
 * Persisted in SharedPreferences with the same keys used by iOS UserDefaults.
 */
data class SMTPConfigData(
    val host: String = "",
    val port: Int = 587,
    val useTls: Boolean = true,
    val username: String = "",
    val password: String = "",
    val fromEmail: String = "noreply@path2ai.tech",
    val fromName: String = "Path Dashboard"
) {
    /** True only when a host has been entered. Mirrors iOS SMTPConfig.current != nil. */
    val isConfigured: Boolean get() = host.isNotBlank()
}

object SMTPConfig {
    private const val PREFS = "smtp_config"

    fun load(context: Context): SMTPConfigData {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return SMTPConfigData(
            host      = p.getString("smtp_host", "")  ?: "",
            port      = p.getInt("smtp_port", 587),
            useTls    = p.getBoolean("smtp_use_tls", true),
            username  = p.getString("smtp_username", "") ?: "",
            password  = p.getString("smtp_password", "") ?: "",
            fromEmail = p.getString("smtp_from_email", "noreply@path2ai.tech") ?: "noreply@path2ai.tech",
            fromName  = p.getString("smtp_from_name",  "Path Dashboard") ?: "Path Dashboard"
        )
    }

    fun save(context: Context, config: SMTPConfigData) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString("smtp_host",       config.host)
            putInt("smtp_port",          config.port)
            putBoolean("smtp_use_tls",   config.useTls)
            putString("smtp_username",   config.username)
            putString("smtp_password",   config.password)
            putString("smtp_from_email", config.fromEmail)
            putString("smtp_from_name",  config.fromName)
            apply()
        }
    }
}
