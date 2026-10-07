package com.tbutman.tilde

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * The home-screen widget: one card's QR code, or the active card's. It redraws whenever a Tilde
 * screen closes (see TildeApp), so it follows changes made in the app. Tapping it opens Tilde on
 * that card.
 */
class TildeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val prefs = Prefs(context)
        ids.forEach { prefs.setWidgetCard(it, null) }
    }

    companion object {
        /** Redraws every Tilde widget. Cheap when there are none. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            manager.getAppWidgetIds(ComponentName(context, TildeWidget::class.java)).forEach { update(context, manager, it) }
        }

        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = Prefs(context)
            val bound = prefs.widgetCard(id)
            val card = prefs.cards.firstOrNull { it.id == bound } ?: prefs.activeCard
            val views = RemoteViews(context.packageName, R.layout.widget_qr)
            views.setTextViewText(R.id.widget_label, context.getString(R.string.widget_label, card.profile.handle.ifBlank { context.getString(R.string.brand_name) }, card.label))
            prefs.qrTextFor(card).takeIf { it.isNotEmpty() }?.let { text ->
                views.setImageViewBitmap(R.id.widget_qr, QrCode.bitmap(context, text, scale = 8))
            }
            // Opens Tilde on this card (a widget for "the active card" just opens Tilde).
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { if (bound != null) putExtra(MainActivity.EXTRA_CARD, card.id) }
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            manager.updateAppWidget(id, views)
        }
    }
}
