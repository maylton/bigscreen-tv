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
    private var rowList = emptyList<Row>()
    private var focusedCard: Card? = null
    private var focusedView: View? = null
    private var moving: RowsAdapter.Holder? = null
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
        Updates.resume(this)
        // Looks for a new release at most once a day; errors (offline TV) are ignored here
        val now = System.currentTimeMillis()
        if (now - prefs.lastUpdateCheck > UPDATE_INTERVAL) {
            prefs.lastUpdateCheck = now
            Updates.check(this) { release, _ -> if (release != null && !isFinishing) Updates.offer(this, release) }
        }
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

    /** While moving a card, left and right move it and every other key finishes. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val row = moving ?: return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true
        val adapter = row.list.adapter as CardAdapter
        val from = adapter.items.indexOfFirst { it.key == adapter.movingKey }
        val to = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> from - 1
            KeyEvent.KEYCODE_DPAD_RIGHT -> from + 1
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> return true
            else -> return finishMove(row).let { true }
        }
        if (to in adapter.items.indices) {
            adapter.move(from, to)
            row.list.scrollToPosition(to)
            row.list.post { row.list.findViewHolderForAdapterPosition(to)?.itemView?.findViewById<View>(R.id.card)?.requestFocus() }
        }
        return true
    }

    private fun startMove() {
        val view = focusedView ?: return
        val row = rows.findContainingViewHolder(view) as? RowsAdapter.Holder ?: return
        val card = focusedCard ?: return
        (row.list.adapter as CardAdapter).movingKey = card.key
        moving = row
        android.widget.Toast.makeText(this, R.string.move_hint, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun finishMove(row: RowsAdapter.Holder) {
        moving = null
        val adapter = row.list.adapter as CardAdapter
        adapter.movingKey = null
        val packages = adapter.items.map { it.key }
        when (val id = rowList[row.bindingAdapterPosition].id) {
            ROW_FAVORITES -> prefs.saveFavorites(packages)
            else -> prefs.saveOrder(id, packages)
        }
        reload()
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

    private fun goHome() = focusCard(0, 0)

    private fun focusCard(rowIndex: Int, cardIndex: Int) {
        rows.scrollToPosition(rowIndex)
        rows.post {
            val row = rows.findViewHolderForAdapterPosition(rowIndex) as? RowsAdapter.Holder ?: return@post
            row.list.scrollToPosition(cardIndex)
            row.list.post {
                row.list.findViewHolderForAdapterPosition(cardIndex)?.itemView?.findViewById<View>(R.id.card)?.requestFocus()
            }
        }
    }

    private fun openSearch() = startActivity(Intent(this, SearchActivity::class.java))

    private fun reload() {
        val apps = AppRepository.load(this)
        val hidden = prefs.hidden
        val visible = apps.filter { it.packageName !in hidden }
        val byPackage = visible.associateBy { it.packageName }
        val recents = prefs.recents
        val recentRank = { entry: AppEntry -> recents.indexOf(entry.packageName).let { if (it < 0) Int.MAX_VALUE else it } }

        // Saved order first, then the rest in their default order
        val arrange = { items: List<AppEntry>, row: String ->
            val saved = prefs.order(row)
            saved.mapNotNull { key -> items.find { it.packageName == key } } + items.filter { it.packageName !in saved }
        }

        val list = buildList {
            // Continue lists only games; recently opened apps lead the Apps row instead
            val recentGames = recents.mapNotNull { byPackage[it] }.filter { it.isGame }
            if (recentGames.isNotEmpty()) add(Row(ROW_CONTINUE, getString(R.string.row_continue), recentGames.map(Card::App)))
            val favorites = prefs.favorites.mapNotNull { byPackage[it] }
            if (favorites.isNotEmpty()) {
                add(Row(ROW_FAVORITES, getString(R.string.row_favorites), favorites.map(Card::App), movable = true))
            }
            val apps = arrange(visible.filter { !it.isGame }.sortedBy(recentRank), ROW_APPS)
            add(Row(ROW_APPS, getString(R.string.row_apps), apps.map(Card::App), movable = true))
            val games = arrange(visible.filter { it.isGame }, ROW_GAMES)
            if (games.isNotEmpty()) add(Row(ROW_GAMES, getString(R.string.row_games), games.map(Card::App), movable = true))
            add(Row(ROW_SETTINGS, getString(R.string.row_settings), Shortcuts.available(this@HomeActivity).map(Card::Action)))
        }
        // Rebuilding the same rows would only move the focus away
        val newSignature = list.joinToString("|") { row -> row.id + ":" + row.cards.joinToString(",") { it.key } }
        if (newSignature == signature) return
        val firstLoad = signature.isEmpty()
        val previousRow = focusedView?.let { rows.findContainingViewHolder(it) }?.bindingAdapterPosition
            ?.let { rowList.getOrNull(it)?.id }
        signature = newSignature
        rowList = list
        rows.adapter = RowsAdapter(list, this)
        if (firstLoad) return goHome()
        // Keep the focus on the same card (or at least the same row) after favoriting, hiding or moving
        val rowIndex = list.indexOfFirst { it.id == previousRow }.takeIf { it >= 0 } ?: 0
        val cardIndex = list[rowIndex].cards.indexOfFirst { it.key == focusedCard?.key }.coerceAtLeast(0)
        focusCard(rowIndex, cardIndex)
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
        if (card !is Card.App) return
        val row = focusedView?.let { rows.findContainingViewHolder(it) }?.bindingAdapterPosition?.let(rowList::getOrNull)
        Actions.options(this, card.entry, onMove = if (row?.movable == true) ::startMove else null) { reload() }
    }

    override fun onCardFocus(card: Card, view: View) {
        focusedCard = card
        focusedView = view
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
        private const val UPDATE_INTERVAL = 24 * 60 * 60 * 1000L
        private const val ROW_CONTINUE = "continue"
        private const val ROW_FAVORITES = "favorites"
        private const val ROW_APPS = "apps"
        private const val ROW_GAMES = "games"
        private const val ROW_SETTINGS = "settings"
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
