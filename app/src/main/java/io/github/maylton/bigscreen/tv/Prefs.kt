package io.github.maylton.bigscreen.tv

import android.content.Context

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("bigscreen", Context.MODE_PRIVATE)

    val favorites get() = list(FAVORITES)
    val recents get() = list(RECENTS)
    val hidden get() = list(HIDDEN).toSet()

    /** Returns true if the package is now a favorite. */
    fun toggleFavorite(packageName: String) = toggle(FAVORITES, packageName)

    fun toggleHidden(packageName: String) = toggle(HIDDEN, packageName)

    fun pushRecent(packageName: String) =
        save(RECENTS, (listOf(packageName) + recents.filter { it != packageName }).take(MAX_RECENTS))

    private fun list(key: String) = prefs.getString(key, "").orEmpty().split('\n').filter { it.isNotEmpty() }

    private fun save(key: String, items: List<String>) = prefs.edit().putString(key, items.joinToString("\n")).apply()

    private fun toggle(key: String, item: String): Boolean {
        val items = list(key)
        val added = item !in items
        save(key, if (added) listOf(item) + items else items - item)
        return added
    }

    companion object {
        private const val FAVORITES = "favorites"
        private const val RECENTS = "recents"
        private const val HIDDEN = "hidden"
        private const val MAX_RECENTS = 12
    }
}
