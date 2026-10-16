package com.pverza.wortschatz

import android.content.Context
import android.content.SharedPreferences
import java.util.TimeZone

/**
 * Settings and learning progress, kept in SharedPreferences (all local, no network).
 *
 * Learning uses simple Leitner boxes: every word sits in a box, and each box has a review delay.
 * "I know it" moves a word one box up (longer delay), "Still learning" sends it back to box 0.
 */
class Store(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("wortschatz", Context.MODE_PRIVATE)

    data class Progress(val box: Int, val due: Long)

    // ---- settings ----

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean("notif", true)
        set(value) = prefs.edit().putBoolean("notif", value).apply()

    /** Hours between word cards. */
    var intervalHours: Int
        get() = prefs.getInt("interval", 3)
        set(value) = prefs.edit().putInt("interval", value).apply()

    /** Cards are only shown from this hour (inclusive)... */
    var startHour: Int
        get() = prefs.getInt("start", 8)
        set(value) = prefs.edit().putInt("start", value).apply()

    /** ...until this hour (exclusive). */
    var endHour: Int
        get() = prefs.getInt("end", 22)
        set(value) = prefs.edit().putInt("end", value).apply()

    var newPerDay: Int
        get() = prefs.getInt("newPerDay", 10)
        set(value) = prefs.edit().putInt("newPerDay", value).apply()

    var levels: Set<String>
        get() = (prefs.getStringSet("levels", null) ?: setOf("A1", "A2")).toSet()
        set(value) = prefs.edit().putStringSet("levels", HashSet(value)).apply()

    // ---- progress ----

    fun progress(id: Int): Progress? {
        val raw = prefs.getString("p$id", null) ?: return null
        val parts = raw.split("|")
        if (parts.size != 2) return null
        val box = parts[0].toIntOrNull() ?: return null
        val due = parts[1].toLongOrNull() ?: return null
        return Progress(box, due)
    }

    /** How many brand-new words were introduced today. */
    fun newToday(now: Long = System.currentTimeMillis()): Int {
        return if (prefs.getLong("newDay", -1L) == dayNumber(now)) prefs.getInt("newCount", 0) else 0
    }

    /** The user answered a card. */
    fun answer(id: Int, known: Boolean, now: Long = System.currentTimeMillis()) {
        val current = progress(id)
        val box = if (known) minOf((current?.box ?: 0) + 1, INTERVALS.size - 1) else 0
        write(id, Progress(box, now + INTERVALS[box]), isNew = current == null, now = now)
    }

    /** A card was shown (e.g. in a notification) but not answered yet: push it back a little. */
    fun markShown(id: Int, now: Long = System.currentTimeMillis()) {
        val current = progress(id)
        write(id, Progress(current?.box ?: 0, now + SHOWN_DELAY), isNew = current == null, now = now)
    }

    private fun write(id: Int, progress: Progress, isNew: Boolean, now: Long) {
        val editor = prefs.edit().putString("p$id", "${progress.box}|${progress.due}")
        if (isNew) {
            editor.putLong("newDay", dayNumber(now)).putInt("newCount", newToday(now) + 1)
        }
        editor.apply()
    }

    private fun dayNumber(now: Long): Long {
        return (now + TimeZone.getDefault().getOffset(now)) / DAY
    }

    companion object {
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE
        private const val DAY = 24 * HOUR

        /** Review delay per box: 10 min, 1 day, 3 days, 1 week, 2 weeks, 1 month. */
        val INTERVALS = longArrayOf(10 * MINUTE, DAY, 3 * DAY, 7 * DAY, 14 * DAY, 30 * DAY)

        /** Boxes from this number on count as "learned" in the statistics. */
        const val LEARNED_BOX = 3

        private const val SHOWN_DELAY = 4 * HOUR
    }
}

/** Decides which word comes next. */
object Picker {
    fun next(context: Context, store: Store, now: Long, allowAhead: Boolean): Word? {
        val levels = store.levels
        var bestDue: Word? = null
        var bestDueAt = Long.MAX_VALUE
        var firstNew: Word? = null
        var soonest: Word? = null
        var soonestAt = Long.MAX_VALUE

        for (word in WordRepository.all(context)) {
            if (word.level !in levels) continue
            val progress = store.progress(word.id)
            if (progress == null) {
                if (firstNew == null) firstNew = word
            } else {
                if (progress.due <= now && progress.due < bestDueAt) {
                    bestDue = word
                    bestDueAt = progress.due
                }
                if (progress.due < soonestAt) {
                    soonest = word
                    soonestAt = progress.due
                }
            }
        }

        // 1) reviews that are due, most overdue first
        if (bestDue != null) return bestDue
        // 2) a new word, as long as today's limit is not reached
        if (firstNew != null && store.newToday(now) < store.newPerDay) return firstNew
        // 3) when studying in the app, allow studying ahead; notifications stay quiet instead
        return if (allowAhead) (soonest ?: firstNew) else null
    }
}
