package com.migul.treningsprogram.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.migul.treningsprogram.MainActivity
import com.migul.treningsprogram.R
import com.migul.treningsprogram.data.preferences.PreferencesManager
import com.migul.treningsprogram.data.repository.WorkoutRepository
import com.migul.treningsprogram.data.repository.currentDayOfWeek
import com.migul.treningsprogram.data.repository.thisMonday
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * F2 — home-screen widget showing today's planned workout. Read-only glance
 * surface: eyebrow (day), headline (session state), and up to four exercise
 * lines; tapping anywhere opens the app. Refreshes on the 30-min system cycle
 * plus an explicit nudge from MainActivity whenever the app is used.
 */
@AndroidEntryPoint
class TodayWorkoutWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var workoutRepository: WorkoutRepository

    // Injected for its side effect: constructing PreferencesManager seeds the process-wide
    // DayBoundary cutoff, so "today" in this (possibly app-less) process matches the app.
    @Inject lateinit var prefsManager: PreferencesManager

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val views = buildViews(context)
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today)

        val day = com.migul.treningsprogram.domain.DayBoundary.today()
        views.setTextViewText(
            R.id.tv_widget_eyebrow,
            "TODAY · " + day.dayOfWeek.name.take(3) + " ${day.dayOfMonth}/${day.monthValue}",
        )

        val plan = runCatching {
            workoutRepository.getPlannedForDay(thisMonday(), currentDayOfWeek()).first()
        }.getOrDefault(emptyList())

        when {
            plan.isEmpty() -> {
                views.setTextViewText(R.id.tv_widget_title, "Rest day")
                views.setTextViewText(R.id.tv_widget_lines, "No session planned — recover well.")
            }
            plan.all { it.isLogged } -> {
                views.setTextViewText(R.id.tv_widget_title, "Completed ✓")
                views.setTextViewText(
                    R.id.tv_widget_lines,
                    "${plan.size} exercises logged. Nice work.",
                )
            }
            else -> {
                views.setTextViewText(R.id.tv_widget_title, sessionTitle(plan.size))
                val shown = plan.sortedBy { it.orderInDay }.take(4).joinToString("\n") { p ->
                    val weight = if (p.targetWeightKg > 0f) {
                        " @ " + formatKg(p.targetWeightKg)
                    } else ""
                    "•  ${p.exerciseName} — ${p.sets}×${p.targetReps}$weight"
                }
                val more = plan.size - 4
                views.setTextViewText(
                    R.id.tv_widget_lines,
                    if (more > 0) "$shown\n+ $more more" else shown,
                )
            }
        }

        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        return views
    }

    private fun sessionTitle(count: Int) =
        if (count == 1) "1 exercise planned" else "$count exercises planned"

    private fun formatKg(kg: Float): String =
        if (kg == kg.toInt().toFloat()) "${kg.toInt()} kg"
        else String.format(Locale.US, "%.1f kg", kg)

    companion object {
        /** Nudge every placed widget to re-render (no-op when none are placed). */
        fun requestRefresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TodayWorkoutWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, TodayWorkoutWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }
    }
}
