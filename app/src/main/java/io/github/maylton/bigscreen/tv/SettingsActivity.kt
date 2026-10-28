package io.github.maylton.bigscreen.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        list = findViewById(R.id.list)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        list.removeViews(1, list.childCount - 1)  // keep the title
        val hidden = Prefs(this).hidden
        add(R.drawable.ic_display, getString(R.string.settings_default), getString(R.string.settings_default_summary)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.default_title)
                .setMessage(R.string.default_text)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
        val hiddenSummary = if (hidden.isEmpty()) getString(R.string.settings_hidden_none)
        else getString(R.string.settings_hidden_summary, hidden.size)
        add(R.drawable.ic_apps, getString(R.string.settings_hidden), hiddenSummary) { showHidden(hidden) }
        add(R.drawable.ic_settings, getString(R.string.settings_android), null) {
            Actions.open(this, Intent(Settings.ACTION_SETTINGS))
        }
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        add(R.drawable.ic_info, getString(R.string.settings_about), getString(R.string.settings_about_summary, version)) {}
        list.getChildAt(1).requestFocus()
    }

    private fun showHidden(hidden: Set<String>) {
        if (hidden.isEmpty()) {
            Toast.makeText(this, R.string.settings_hidden_none, Toast.LENGTH_SHORT).show()
            return
        }
        val apps = AppRepository.load(this).filter { it.packageName in hidden }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_hidden_pick)
            .setItems(apps.map { it.label }.toTypedArray()) { _, which ->
                Prefs(this).toggleHidden(apps[which].packageName)
                build()
            }
            .show()
    }

    private fun add(icon: Int, title: String, summary: String?, action: () -> Unit) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_setting, list, false)
        row.findViewById<ImageView>(R.id.icon).setImageResource(icon)
        row.findViewById<TextView>(R.id.title).text = title
        row.findViewById<TextView>(R.id.summary).apply {
            text = summary
            visibility = if (summary == null) View.GONE else View.VISIBLE
        }
        row.setOnClickListener { action() }
        list.addView(row)
    }
}
