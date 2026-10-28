package io.github.maylton.bigscreen.tv

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

sealed class Card {
    abstract val key: String

    class App(val entry: AppEntry) : Card() {
        override val key get() = entry.packageName
    }

    class Action(val shortcut: Shortcut) : Card() {
        override val key get() = "action:${shortcut.titleRes}"
    }
}

class Row(val title: String, val cards: List<Card>)

interface CardListener {
    fun onCardClick(card: Card)
    fun onCardLongClick(card: Card)
    fun onCardFocus(card: Card, view: View) {}
}

class CardAdapter(private val cards: List<Card>, private val listener: CardListener) :
    RecyclerView.Adapter<CardAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val card: FrameLayout = view.findViewById(R.id.card)
        val banner: ImageView = view.findViewById(R.id.banner)
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)
    }

    override fun getItemCount() = cards.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val holder = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false))
        val context = parent.context
        holder.card.clipToOutline = true
        holder.card.outlineAmbientShadowColor = Color.TRANSPARENT
        holder.card.outlineSpotShadowColor = Color.TRANSPARENT
        holder.card.setOnClickListener { cardAt(holder)?.let(listener::onCardClick) }
        holder.card.setOnLongClickListener { cardAt(holder)?.let(listener::onCardLongClick); true }
        holder.card.setOnFocusChangeListener { view, focused ->
            val scale = if (focused) 1.1f else 1f
            view.animate().scaleX(scale).scaleY(scale).translationZ(if (focused) dp(context, 8f) else 0f)
                .setDuration(150).start()
            holder.label.setTextColor(context.getColor(if (focused) R.color.on_surface else R.color.on_surface_variant))
            if (focused) cardAt(holder)?.let { listener.onCardFocus(it, view) }
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val context = holder.itemView.context
        when (val card = cards[position]) {
            is Card.App -> {
                holder.label.text = card.entry.label
                holder.icon.imageTintList = null
                Art.bind(context, card.entry, holder.banner, holder.icon)
            }
            is Card.Action -> {
                holder.label.text = context.getString(card.shortcut.titleRes)
                holder.banner.tag = null
                holder.banner.setImageDrawable(null)
                holder.icon.setImageResource(card.shortcut.iconRes)
                holder.icon.imageTintList = context.getColorStateList(R.color.on_surface)
            }
        }
    }

    private fun cardAt(holder: Holder) = cards.getOrNull(holder.bindingAdapterPosition)

    companion object {
        fun dp(context: Context, value: Float) = value * context.resources.displayMetrics.density
    }
}

class RowsAdapter(private val rows: List<Row>, private val listener: CardListener) :
    RecyclerView.Adapter<RowsAdapter.Holder>() {

    private val pool = RecyclerView.RecycledViewPool()

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.title)
        val list: RowView = view.findViewById(R.id.list)
    }

    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val holder = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false))
        holder.list.layoutManager = LinearLayoutManager(parent.context, LinearLayoutManager.HORIZONTAL, false)
        holder.list.setRecycledViewPool(pool)
        holder.list.itemAnimator = null
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.title.text = rows[position].title
        holder.list.adapter = CardAdapter(rows[position].cards, listener)
    }
}

/** Horizontal row that keeps the focus when the end is reached, instead of jumping to another row. */
class RowView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : RecyclerView(context, attrs) {
    override fun focusSearch(focused: View, direction: Int): View? {
        val next = super.focusSearch(focused, direction)
        val horizontal = direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT
        return if (horizontal && (next == null || !contains(next))) focused else next
    }

    private fun contains(view: View): Boolean {
        var parent: ViewParent? = view.parent
        while (parent != null) {
            if (parent === this) return true
            parent = parent.parent
        }
        return false
    }
}
