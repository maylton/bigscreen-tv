package io.github.maylton.bigscreen.tv

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SearchActivity : Activity(), CardListener {
    private lateinit var results: RecyclerView
    private lateinit var empty: View
    private var apps = emptyList<AppEntry>()
    private var matches = emptyList<AppEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        results = findViewById(R.id.results)
        empty = findViewById(R.id.empty)
        val metrics = resources.displayMetrics
        val cardWidth = resources.getDimension(R.dimen.card_width) + resources.getDimension(R.dimen.card_spacing)
        val usable = metrics.widthPixels - 2 * resources.getDimension(R.dimen.safe_horizontal)
        results.layoutManager = GridLayoutManager(this, maxOf(1, (usable / cardWidth).toInt()))
        results.itemAnimator = null

        val query = findViewById<EditText>(R.id.query)
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = filter(s?.toString().orEmpty())
        })
        query.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH && matches.isNotEmpty()) onCardClick(Card.App(matches[0]))
            action == EditorInfo.IME_ACTION_SEARCH
        }
        load()
        query.requestFocus()
    }

    private fun load() {
        val hidden = Prefs(this).hidden
        apps = AppRepository.load(this).filter { it.packageName !in hidden }
        filter(findViewById<EditText>(R.id.query).text.toString())
    }

    private fun filter(text: String) {
        matches = if (text.isBlank()) apps else apps.filter { it.matches(text.trim()) }
        empty.visibility = if (matches.isEmpty()) View.VISIBLE else View.GONE
        results.adapter = CardAdapter(matches.map(Card::App), this)
    }

    override fun onCardClick(card: Card) {
        if (card is Card.App) {
            Actions.launch(this, card.entry)
            finish()
        }
    }

    override fun onCardLongClick(card: Card) {
        if (card is Card.App) Actions.options(this, card.entry, onChanged = ::load)
    }
}
