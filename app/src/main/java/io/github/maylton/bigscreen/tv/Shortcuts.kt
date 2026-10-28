package io.github.maylton.bigscreen.tv

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class Shortcut(val titleRes: Int, val iconRes: Int, val intent: Intent, val permission: String? = null)

object Shortcuts {
    /** Opens the Google TV accessory pairing screen (remotes and gamepads). */
    private const val CONNECT_INPUT = "com.google.android.intent.action.CONNECT_INPUT"

    /** Android TV settings screens: each one lists alternatives, the first that exists is used. */
    private val candidates = listOf(
        Triple(R.string.shortcut_wifi, R.drawable.ic_wifi, listOf(Settings.ACTION_WIFI_SETTINGS)),
        Triple(R.string.shortcut_bluetooth, R.drawable.ic_bluetooth, listOf(Settings.ACTION_BLUETOOTH_SETTINGS, CONNECT_INPUT)),
        Triple(R.string.shortcut_display, R.drawable.ic_display, listOf(Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SOUND_SETTINGS)),
        Triple(R.string.shortcut_apps, R.drawable.ic_apps, listOf(Settings.ACTION_APPLICATION_SETTINGS)),
        Triple(R.string.shortcut_storage, R.drawable.ic_storage, listOf(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
        Triple(R.string.shortcut_date, R.drawable.ic_time, listOf(Settings.ACTION_DATE_SETTINGS)),
        Triple(R.string.shortcut_accessibility, R.drawable.ic_accessibility, listOf(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        Triple(R.string.shortcut_about, R.drawable.ic_info, listOf(Settings.ACTION_DEVICE_INFO_SETTINGS)),
        Triple(R.string.shortcut_android, R.drawable.ic_settings, listOf(Settings.ACTION_SETTINGS)),
    )

    fun available(context: Context): List<Shortcut> {
        val used = mutableSetOf<String>()
        val shortcuts = candidates.mapNotNull { (title, icon, actions) ->
            val intent = actions.firstNotNullOfOrNull { resolve(context, Intent(it)) } ?: return@mapNotNull null
            // Display and sound share one screen on Google TV; show it once
            if (!used.add(intent.component!!.flattenToString())) return@mapNotNull null
            val permission = Manifest.permission.BLUETOOTH_CONNECT
                .takeIf { intent.action == CONNECT_INPUT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
            Shortcut(title, icon, intent, permission)
        }
        return shortcuts + Shortcut(R.string.shortcut_bigscreen, R.drawable.ic_gamepad, Intent(context, SettingsActivity::class.java))
    }

    /** Picks the system settings app when several apps handle the action, so no chooser shows up. */
    @Suppress("DEPRECATION")
    private fun resolve(context: Context, intent: Intent): Intent? {
        val pm = context.packageManager
        val matches = pm.queryIntentActivities(intent, 0)
        if (matches.isEmpty()) return null
        val settingsPackage = pm.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)?.activityInfo?.packageName
        val pick = (matches.firstOrNull { it.activityInfo.packageName == settingsPackage } ?: matches.first()).activityInfo
        return Intent(intent).setClassName(pick.packageName, pick.name)
    }
}
