package io.github.maylton.bigscreen.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

object Actions {
    fun launch(activity: Activity, entry: AppEntry) {
        val pm = activity.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(entry.packageName)
            ?: pm.getLaunchIntentForPackage(entry.packageName)
            ?: Intent(Intent.ACTION_MAIN).setComponent(entry.component)
        try {
            activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Prefs(activity).pushRecent(entry.packageName)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, activity.getString(R.string.open_failed, entry.label), Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(activity, activity.getString(R.string.open_failed, entry.label), Toast.LENGTH_SHORT).show()
        }
    }

    /** Opens a settings screen, falling back to the main Android settings. */
    fun open(activity: Activity, intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            activity.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (e: SecurityException) {
            activity.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun options(activity: Activity, entry: AppEntry, onChanged: () -> Unit) {
        val prefs = Prefs(activity)
        val packageUri = Uri.fromParts("package", entry.packageName, null)
        val removable = entry.activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0
        val items = buildList<Pair<Int, () -> Unit>> {
            add(R.string.option_open to { launch(activity, entry) })
            val favorite = entry.packageName in prefs.favorites
            add((if (favorite) R.string.option_favorite_remove else R.string.option_favorite_add) to {
                prefs.toggleFavorite(entry.packageName)
                onChanged()
            })
            add(R.string.option_hide to {
                prefs.toggleHidden(entry.packageName)
                Toast.makeText(activity, activity.getString(R.string.hidden_toast, entry.label), Toast.LENGTH_LONG).show()
                onChanged()
            })
            add(R.string.option_info to { open(activity, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)) })
            if (removable) add(R.string.option_uninstall to { open(activity, Intent(Intent.ACTION_DELETE, packageUri)) })
        }
        AlertDialog.Builder(activity)
            .setTitle(entry.label)
            .setItems(items.map { activity.getString(it.first) }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }
}
