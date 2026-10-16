package com.pverza.wortschatz

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * Battery-friendly scheduling:
 *  - one inexact, non-waking repeating alarm (Android batches it with other alarms)
 *  - no foreground service, no wake locks, no network, no background work between alarms
 *  - the receiver does a few microseconds of work and goes back to sleep
 */
object Reminders {
    private const val REQUEST_CODE = 100

    fun reschedule(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(app)
        alarmManager.cancel(pending)

        val store = Store(app)
        if (!store.notificationsEnabled) return

        val interval = store.intervalHours * AlarmManager.INTERVAL_HOUR
        alarmManager.setInexactRepeating(
            AlarmManager.RTC, // RTC (not RTC_WAKEUP): never wakes the phone just for us
            System.currentTimeMillis() + interval,
            interval,
            pending,
        )
    }

    private fun pendingIntent(app: Context): PendingIntent {
        val intent = Intent(app, ReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            app,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

object Notifier {
    private const val CHANNEL_ID = "words"
    const val NOTIFICATION_ID = 1

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        // IMPORTANCE_LOW: shows in the shade and on the lock screen, but no sound, vibration or pop-up.
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_description)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, word: Word) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val details = "${word.en}\n${word.ex}\n${word.exEn}"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_word)
            .setContentTitle(word.de)
            .setContentText(word.en)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter(12 * 60 * 60 * 1000L) // stale cards remove themselves
            .setContentIntent(open)
            .addAction(0, "Still learning", actionIntent(context, word.id, false))
            .addAction(0, "I know it", actionIntent(context, word.id, true))
            .build()

        // Always the same id: a new card replaces the old one, so they never pile up.
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun actionIntent(context: Context, wordId: Int, known: Boolean): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java)
            .putExtra("id", wordId)
            .putExtra("known", known)
        return PendingIntent.getBroadcast(
            context,
            wordId * 2 + if (known) 1 else 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store(context)
        if (!store.notificationsEnabled) return

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < store.startHour || hour >= store.endHour) return

        val word = Picker.next(context, store, System.currentTimeMillis(), allowAhead = false) ?: return
        store.markShown(word.id)
        Notifier.show(context, word)
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        if (id < 0) return
        Store(context).answer(id, intent.getBooleanExtra("known", false))
        NotificationManagerCompat.from(context).cancel(Notifier.NOTIFICATION_ID)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Reminders.reschedule(context)
        }
    }
}
