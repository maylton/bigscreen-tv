package io.github.maylton.bigscreen.tv

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextClock
import android.window.OnBackInvokedDispatcher
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class HomeActivity : Activity(), CardListener {
    private lateinit var rows: RecyclerView
    private lateinit var backdrop: ImageView
    private lateinit var network: ImageView
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private var signature = ""
    private var focusedCard: Card? = null
    private var backdropKey: String? = null
    private var pendingShortcut: Shortcut? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Art.clear()
            reload()
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            showNetwork(capabilities)

        override fun onLost(network: Network) = showNetwork(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        prefs = Prefs(this)
        rows = findViewById(R.id.rows)
        backdrop = findViewById(R.id.backdrop)
        network = findViewById(R.id.network)

        findViewById<TextClock>(R.id.clock).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
        }
        findViewById<TextClock>(R.id.date).apply {
            val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM")
            format12Hour = pattern
            format24Hour = pattern
        }
        findViewById<View>(R.id.search).setOnClickListener { openSearch() }
        findViewById<View>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Rows are aligned to the top by hand in onCardFocus, like the Google TV home
        rows.layoutManager = object : LinearLayoutManager(this) {
            override fun requestChildRectangleOnScreen(
                parent: RecyclerView, child: View, rect: android.graphics.Rect,
                immediate: Boolean, focusedChildVisible: Boolean,
            ) = false
        }
        rows.itemAnimator = null
        rows.clipChildren = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backdrop.setRenderEffect(RenderEffect.createBlurEffect(48f, 48f, Shader.TileMode.CLAMP))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { goHome() }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(packageReceiver, filter)
        }
        val connectivity = getSystemService(ConnectivityManager::class.java)
        showNetwork(connectivity.getNetworkCapabilities(connectivity.activeNetwork))
        connectivity.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        super.onDestroy()
    }

    /** The Home button while Big Screen is open goes back to the first row. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        goHome()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> goHome()
            KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_BUTTON_Y -> openSearch()
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_X -> (focusedCard as? Card.App)?.let(::onCardLongClick)
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun goHome() {
        rows.scrollToPosition(0)
        rows.post { rows.findViewHolderForAdapterPosition(0)?.itemView?.findViewById<View>(R.id.card)?.requestFocus() }
    }

    private fun openSearch() = startActivity(Intent(this, SearchActivity::class.java))

    private fun reload() {
        val apps = AppRepository.load(this)
        val hidden = prefs.hidden
        val visible = apps.filter { it.packageName !in hidden }
        val byPackage = visible.associateBy { it.packageName }
        val recents = prefs.recents
        val recentRank = { entry: AppEntry -> recents.indexOf(entry.packageName).let { if (it < 0) Int.MAX_VALUE else it } }

        val list = buildList {
            // Continue lists only games; recently opened apps lead the Apps row instead
            val recentGames = recents.mapNotNull { byPackage[it] }.filter { it.isGame }
            if (recentGames.isNotEmpty()) add(Row(getString(R.string.row_continue), recentGames.map(Card::App)))
            val favorites = prefs.favorites.mapNotNull { byPackage[it] }
            if (favorites.isNotEmpty()) add(Row(getString(R.string.row_favorites), favorites.map(Card::App)))
            add(Row(getString(R.string.row_apps), visible.filter { !it.isGame }.sortedBy(recentRank).map(Card::App)))
            val games = visible.filter { it.isGame }
            if (games.isNotEmpty()) add(Row(getString(R.string.row_games), games.map(Card::App)))
            add(Row(getString(R.string.row_settings), Shortcuts.available(this@HomeActivity).map(Card::Action)))
        }
        // Rebuilding the same rows would only move the focus away
        val newSignature = list.joinToString("|") { row -> row.title + ":" + row.cards.joinToString(",") { it.key } }
        if (newSignature == signature) return
        val firstLoad = signature.isEmpty()
        signature = newSignature
        rows.adapter = RowsAdapter(list, this)
        if (firstLoad) goHome()
    }

    override fun onCardClick(card: Card) {
        when (card) {
            is Card.App -> Actions.launch(this, card.entry)
            is Card.Action -> openShortcut(card.shortcut)
        }
    }

    private fun openShortcut(shortcut: Shortcut) {
        val permission = shortcut.permission
        if (permission != null && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            pendingShortcut = shortcut
            requestPermissions(arrayOf(permission), PERMISSION_REQUEST)
        } else {
            Actions.open(this, shortcut.intent)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val shortcut = pendingShortcut ?: return
        pendingShortcut = null
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        Actions.open(this, if (granted) shortcut.intent else Intent(Settings.ACTION_SETTINGS))
    }

    override fun onCardLongClick(card: Card) {
        if (card is Card.App) Actions.options(this, card.entry) { reload() }
    }

    override fun onCardFocus(card: Card, view: View) {
        focusedCard = card
        rows.findContainingItemView(view)?.let { rows.smoothScrollBy(0, it.top - rows.paddingTop) }
        if (card is Card.App) {
            Art.load(this, card.entry) { setBackdrop(card.key, if (it.isBanner) it.drawable else null) }
        } else {
            setBackdrop(null, null)
        }
    }

    /** The focused app's banner, blurred behind everything (Android 12+). */
    private fun setBackdrop(key: String?, drawable: Drawable?) {
        if (key == backdropKey) return
        backdropKey = key
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (drawable == null) {
                backdrop.animate().alpha(0f).setDuration(250).start()
                return@postDelayed
            }
            backdrop.setImageDrawable(drawable.constantState?.newDrawable(resources)?.mutate() ?: drawable)
            backdrop.alpha = 0f
            val target = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.6f else 0.25f
            backdrop.animate().alpha(target).setDuration(350).start()
        }, 180)
    }

    companion object {
        private const val PERMISSION_REQUEST = 1
    }

    private fun showNetwork(capabilities: NetworkCapabilities?) {
        val icon = when {
            capabilities == null -> R.drawable.ic_wifi_off
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> R.drawable.ic_ethernet
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> R.drawable.ic_wifi
            else -> R.drawable.ic_ethernet
        }
        runOnUiThread { network.setImageResource(icon) }
    }
}
