package com.kareem.cortex.prime.understand

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kareem.cortex.prime.data.db.OpenLoop
import com.kareem.cortex.prime.data.db.Reminder
import com.kareem.cortex.prime.data.db.ReminderState
import com.kareem.cortex.prime.graph
import com.kareem.cortex.prime.ui.shell.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Exact alarms when allowed, inexact otherwise (spec §6.5). */
class AndroidReminderScheduler(private val context: Context) : ReminderScheduler {
    private val am get() = context.getSystemService(AlarmManager::class.java)

    fun exactAllowed() = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    private fun pi(reminderId: Long) = PendingIntent.getBroadcast(
        context, reminderId.toInt(), Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_REMINDER, reminderId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun schedule(r: Reminder, loop: OpenLoop) {
        if (exactAllowed()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.fireAt, pi(r.id))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.fireAt, pi(r.id))
    }

    override fun cancel(reminderId: Long) = am.cancel(pi(reminderId))

    companion object {
        const val EXTRA_REMINDER = "reminder_id"
        const val EXTRA_LOOP = "loop_id"
        const val CHANNEL = "reminders"

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null)
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Things you might forget", NotificationManager.IMPORTANCE_HIGH).apply { description = "Timed reminders Cortex found in your notes and screenshots" })
        }
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Shows the reminder with Done / Snooze actions; opens Now on tap. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val rid = intent.getLongExtra(AndroidReminderScheduler.EXTRA_REMINDER, -1)
        val pending = goAsync()
        receiverScope.launch {
            try {
                val g = context.graph
                val r = g.db.loops().reminder(rid) ?: return@launch
                if (r.state != ReminderState.SCHEDULED) return@launch
                val loop = g.db.loops().byId(r.openLoopId) ?: return@launch
                g.db.loops().setReminderState(rid, ReminderState.FIRED)
                AndroidReminderScheduler.ensureChannel(context)
                fun action(code: String, n: Int) = PendingIntent.getBroadcast(context, (rid * 10 + n).toInt(),
                    Intent(context, ReminderActionReceiver::class.java).setAction(code).putExtra(AndroidReminderScheduler.EXTRA_LOOP, loop.id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val open = PendingIntent.getActivity(context, loop.id.toInt(), Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                val n = android.app.Notification.Builder(context, AndroidReminderScheduler.CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(loop.action)
                    .setContentText("From your ${if (loop.sourceText.length > 0) "note" else "capture"} · tap to see the source")
                    .setStyle(android.app.Notification.BigTextStyle().bigText(loop.sourceText.substring(loop.spanStart.coerceIn(0, loop.sourceText.length), loop.spanEnd.coerceIn(0, loop.sourceText.length))))
                    .setContentIntent(open).setAutoCancel(true)
                    .addAction(android.app.Notification.Action.Builder(null, "Done", action(ACTION_DONE, 1)).build())
                    .addAction(android.app.Notification.Action.Builder(null, "In 1 hour", action(ACTION_SNOOZE_HOUR, 2)).build())
                    .addAction(android.app.Notification.Action.Builder(null, "Tomorrow", action(ACTION_SNOOZE_TOMORROW, 3)).build())
                    .build()
                context.getSystemService(NotificationManager::class.java).notify(loop.id.toInt(), n)
            } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_DONE = "com.kareem.cortex.prime.DONE"
        const val ACTION_SNOOZE_HOUR = "com.kareem.cortex.prime.SNOOZE_HOUR"
        const val ACTION_SNOOZE_TOMORROW = "com.kareem.cortex.prime.SNOOZE_TOMORROW"
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val loopId = intent.getLongExtra(AndroidReminderScheduler.EXTRA_LOOP, -1)
        val pending = goAsync()
        receiverScope.launch {
            try {
                val a = context.graph.loopActions
                when (intent.action) {
                    ReminderReceiver.ACTION_DONE -> a.done(loopId)
                    ReminderReceiver.ACTION_SNOOZE_HOUR -> a.snooze(loopId, System.currentTimeMillis() + 3_600_000)
                    ReminderReceiver.ACTION_SNOOZE_TOMORROW -> a.snooze(loopId, tomorrowAt(9))
                }
                context.getSystemService(NotificationManager::class.java).cancel(loopId.toInt())
            } finally { pending.finish() }
        }
    }
}

fun tomorrowAt(hour: Int): Long = java.time.LocalDate.now().plusDays(1).atTime(hour, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Alarms don't survive reboots or time changes; put every scheduled reminder back. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        receiverScope.launch {
            try {
                val g = context.graph
                for (r in g.db.loops().allScheduled()) {
                    val loop = g.db.loops().byId(r.openLoopId) ?: continue
                    if (r.fireAt > System.currentTimeMillis()) g.reminders.schedule(r, loop) else g.reminders.schedule(r.copy(fireAt = System.currentTimeMillis() + 60_000), loop)
                }
            } finally { pending.finish() }
        }
    }
}
