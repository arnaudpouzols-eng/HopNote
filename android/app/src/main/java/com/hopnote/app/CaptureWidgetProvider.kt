package com.hopnote.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class CaptureWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ids.forEach { id ->
        val intent = Intent(context, MainActivity::class.java).putExtra(MainActivity.START_VOICE, true)
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_capture).apply { setOnClickPendingIntent(R.id.capture_widget_button, pending) })
    }
}
