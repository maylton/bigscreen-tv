package io.github.maylton.bigscreen.tv

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Makes the Home button open Big Screen without adb: turned on in the Android accessibility
 * settings. Only the Home key and which app shows up are seen, never the screen content.
 */
class HomeButtonService : AccessibilityService() {

    /** Android stops Big Screen while updating it; come back instead of leaving the stock home on screen. */
    override fun onServiceConnected() {
        val updated = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        if (System.currentTimeMillis() - updated < 60_000 && !isDefaultHome(this)) openHome()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME || isDefaultHome(this)) return false
        if (event.action == KeyEvent.ACTION_UP) openHome()
        return true
    }

    /** The stock home can also show up without the Home button, e.g. after Back out of an app. */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || isDefaultHome(this)) return
        if (event.packageName?.toString() in otherHomes(this)) openHome()
    }

    override fun onInterrupt() {}

    private fun openHome() = startActivity(
        Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    )

    companion object {
        private val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        fun isDefaultHome(context: Context) =
            context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName == context.packageName

        /** Other launchers (the Google TV home), ignoring the system's fallback screens. */
        @Suppress("DEPRECATION")
        fun otherHomes(context: Context) = context.packageManager.queryIntentActivities(homeIntent, 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName && !it.name.endsWith("FallbackHome") && it.packageName != "android" }
            .map { it.packageName }
            .toSet()

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val self = ComponentName(context, HomeButtonService::class.java)
            return enabled?.split(':')?.any { ComponentName.unflattenFromString(it) == self } == true
        }
    }
}
