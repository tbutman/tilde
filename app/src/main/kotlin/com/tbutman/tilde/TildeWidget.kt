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
 * that card. While an event name is set to clear itself, it also redraws just after midnight, so the
 * code stops carrying yesterday's event.
 */
class TildeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
        redrawAtMidnight(context, ids)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val prefs = Prefs(context)
        ids.forEach { prefs.setWidgetCard(it, null) }
    }

    companion object {
        /** Redraws every Tilde widget. Cheap when there are none. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TildeWidget::class.java))
            ids.forEach { update(context, manager, it) }
            redrawAtMidnight(context, ids)
        }

        /** An inexact alarm (no permission needed) just after midnight, only while a self-clearing event name is set. */
        private fun redrawAtMidnight(context: Context, ids: IntArray) {
            val alarms = context.getSystemService(android.app.AlarmManager::class.java) ?: return
            val redraw = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, TildeWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val prefs = Prefs(context)
            if (ids.isEmpty() || prefs.event.isEmpty() || !prefs.eventAutoClear) return alarms.cancel(redraw)
            val midnight = java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            alarms.set(android.app.AlarmManager.RTC, midnight + 60_000, redraw)
        }

        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = Prefs(context)
            val bound = prefs.widgetCard(id)
            val card = prefs.cards.firstOrNull { it.id == bound } ?: prefs.activeCard
            val views = RemoteViews(context.packageName, R.layout.widget_qr)
            views.setTextViewText(R.id.widget_label, context.getString(R.string.widget_label, card.profile.handle.ifBlank { context.getString(R.string.brand_name) }, card.label))
            prefs.qrTextFor(card).takeIf { it.isNotEmpty() }?.let { text ->
                val code = QrCode.bitmap(context, text, scale = 8)
                views.setImageViewBitmap(R.id.widget_qr, code)
                if (code == null) views.setTextViewText(R.id.widget_label, context.getString(R.string.qr_too_long))
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
