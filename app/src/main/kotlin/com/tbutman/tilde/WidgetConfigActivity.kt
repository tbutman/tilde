package com.tbutman.tilde

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Adding (or reconfiguring) the widget: which card it shows, or always the active one. */
class WidgetConfigActivity : AppCompatActivity() {
    private val widgetId by lazy {
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Backing out without choosing doesn't add the widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        val prefs = Prefs(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(24), 0, dp(24))
            fitsSystemWindows = true
        }
        list.addView(TextView(this).apply {
            setText(R.string.widget_choose)
            setTextColor(getColor(R.color.text))
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(20), dp(8), dp(20), dp(16))
        })
        val current = prefs.widgetCard(widgetId)
        // "The active card" first: it follows whichever card is active.
        list.addView(TextView(this).apply {
            setText(R.string.widget_active_card)
            setTextColor(getColor(if (current == null) R.color.accent else R.color.text))
            textSize = 16f
            minHeight = dp(56)
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            val ripple = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            setBackgroundResource(ripple.resourceId)
            setOnClickListener { choose(prefs, null) }
        })
        warnIfPersonal(list, prefs, prefs.activeCard)
        for (card in prefs.cards) {
            list.addView(CardDialogs.row(this, card, active = card.id == current).apply { setOnClickListener { choose(prefs, card.id) } })
            warnIfPersonal(list, prefs, card)
        }
        setContentView(androidx.core.widget.NestedScrollView(this).apply {
            setBackgroundColor(getColor(R.color.bg))
            addView(list)
        })
    }

    /**
     * Under a card whose code is the contact card (phone number and email) or Guest Wi-Fi (the
     * password): the widget puts that code on the home screen, for anyone who sees it.
     */
    private fun warnIfPersonal(list: LinearLayout, prefs: Prefs, card: Card) {
        if (prefs.sharing(card) !in setOf(Presets.CONTACT, Presets.WIFI)) return
        list.addView(TextView(this).apply {
            setText(R.string.widget_home_screen_warning)
            setTextColor(getColor(R.color.accent))
            textSize = 13f
            setPadding(dp(50), 0, dp(20), dp(10))
        })
    }

    private fun choose(prefs: Prefs, cardId: String?) {
        prefs.setWidgetCard(widgetId, cardId)
        TildeWidget.update(this, AppWidgetManager.getInstance(this), widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
