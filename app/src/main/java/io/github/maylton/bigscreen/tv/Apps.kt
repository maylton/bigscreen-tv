package io.github.maylton.bigscreen.tv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.text.Normalizer
import java.util.concurrent.Executors

class AppEntry(val activity: ActivityInfo, val label: String, val isGame: Boolean) {
    val packageName: String get() = activity.packageName
    val component = ComponentName(activity.packageName, activity.name)

    fun matches(query: String) = fold(label).contains(fold(query))

    companion object {
        /** Lowercase without accents, so "pokemon" finds "Pokémon". */
        fun fold(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
    }
}

object AppRepository {
    /** TV apps (Leanback) first; phone apps that only have a regular launcher entry after them. */
    @Suppress("DEPRECATION")
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, AppEntry>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (info in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                val activity = info.activityInfo
                if (activity.packageName == context.packageName || activity.packageName in found) continue
                found[activity.packageName] = AppEntry(activity, info.loadLabel(pm).toString(), isGame(activity.applicationInfo))
            }
        }
        return found.values.sortedBy { AppEntry.fold(it.label) }
    }

    @Suppress("DEPRECATION")
    private fun isGame(app: ApplicationInfo) =
        app.category == ApplicationInfo.CATEGORY_GAME || app.flags and ApplicationInfo.FLAG_IS_GAME != 0
}

/** Loads banners and icons off the main thread, with a small cache. */
object Art {
    class Result(val drawable: Drawable, val isBanner: Boolean)

    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val cache = LruCache<String, Result>(160)

    fun load(context: Context, entry: AppEntry, onLoaded: (Result) -> Unit) {
        cache.get(entry.packageName)?.let { onLoaded(it); return }
        val pm = context.applicationContext.packageManager
        executor.execute {
            val banner = entry.activity.loadBanner(pm) ?: entry.activity.applicationInfo.loadBanner(pm)
            val result = if (banner != null) Result(banner, true) else Result(entry.activity.loadIcon(pm), false)
            main.post {
                cache.put(entry.packageName, result)
                onLoaded(result)
            }
        }
    }

    /** Shows the art in the card, ignoring results that arrive after the view was recycled. */
    fun bind(context: Context, entry: AppEntry, banner: ImageView, icon: ImageView) {
        banner.tag = entry.packageName
        banner.setImageDrawable(null)
        icon.setImageDrawable(null)
        load(context, entry) { result ->
            if (banner.tag != entry.packageName) return@load
            if (result.isBanner) banner.setImageDrawable(result.drawable) else icon.setImageDrawable(result.drawable)
        }
    }

    fun clear() = cache.evictAll()
}
