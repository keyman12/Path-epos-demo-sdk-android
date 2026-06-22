package tech.path2ai.epos.terminal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Customer-display branding for the connected terminal — a merchant logo shown
 * on connect and re-shown between transactions ("attract mode").
 *
 * Backed by SharedPreferences (the on/off toggle + an optional caption) plus a
 * PNG file in the app's private storage (the logo bitmap). When no custom logo
 * has been uploaded the bundled Path Cafe logo (assets/pathcafe-logo.png) is
 * used, so the feature works out of the box.
 *
 * The uploaded image is scaled down to fit the small, low-resolution customer
 * screen and re-encoded as PNG, keeping the base64 HTML payload pushed to the
 * terminal small (Path-PSDK-TestHarnesses gotcha 11).
 */
object CustomerDisplaySettings {

    private const val PREFS_NAME = "epos_customer_display"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CAPTION = "caption"
    private const val LOGO_FILE = "customer_display_logo.png"
    private const val DEFAULT_ASSET = "pathcafe-logo.png"

    /** Longest edge (px) the uploaded logo is scaled down to — the VP100's
     *  customer screen is small, and the whole HTML payload stays well under 64 KB. */
    const val MAX_DIMENSION = 480

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    fun caption(context: Context): String = prefs(context).getString(KEY_CAPTION, "") ?: ""

    fun setCaption(context: Context, caption: String) {
        prefs(context).edit().putString(KEY_CAPTION, caption.trim()).apply()
    }

    private fun logoFile(context: Context) = File(context.filesDir, LOGO_FILE)

    /** True once a custom logo has been uploaded (otherwise the bundled default is used). */
    fun hasCustomLogo(context: Context): Boolean = logoFile(context).exists()

    /** The current logo bytes — the uploaded one, or the bundled Path Cafe default. */
    fun logoBytes(context: Context): ByteArray =
        if (hasCustomLogo(context)) logoFile(context).readBytes()
        else context.assets.open(DEFAULT_ASSET).use { it.readBytes() }

    /** Forget the uploaded logo and fall back to the bundled Path Cafe default. */
    fun resetLogo(context: Context) {
        logoFile(context).delete()
    }

    /**
     * Scale [source] to fit within [MAX_DIMENSION] (aspect preserved), re-encode
     * as PNG, and store it as the custom logo. Returns the stored bytes, or null
     * if [source] couldn't be decoded as an image.
     */
    fun saveLogo(context: Context, source: ByteArray): ByteArray? {
        val bmp = BitmapFactory.decodeByteArray(source, 0, source.size) ?: return null
        val scaled = scaleToFit(bmp, MAX_DIMENSION)
        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
        logoFile(context).writeBytes(bytes)
        return bytes
    }

    private fun scaleToFit(bmp: Bitmap, max: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        if (w <= max && h <= max) return bmp
        val ratio = minOf(max.toFloat() / w, max.toFloat() / h)
        return Bitmap.createScaledBitmap(
            bmp,
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
