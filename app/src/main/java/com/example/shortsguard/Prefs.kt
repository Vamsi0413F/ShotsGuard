package com.example.shortsguard

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime

/** Everything the UI needs, read in one go. */
data class Snapshot(
    val blockInstagram: Boolean,
    val blockYoutube: Boolean,
    val limitMinutes: Int,
    val resetHour: Int,
    val sleepEnabled: Boolean,
    val sleepStart: Int,
    val sleepEnd: Int,
    val strict: Boolean,
    val debug: Boolean,
    val usedSeconds: Int,
    val blockedToday: Int,
    val week: List<Pair<LocalDate, Int>>,
    val sleepActive: Boolean,
    val locked: Boolean,
)

/**
 * All settings and usage counters. Backed by SharedPreferences so the
 * accessibility service and the UI always see the same values.
 *
 * "Today" is not the calendar day: it starts at [resetHour]. With a reset
 * hour of 4, 02:00 still counts as the previous day.
 */
class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("shortsguard", Context.MODE_PRIVATE)

    var blockInstagram: Boolean
        get() = sp.getBoolean("block_ig", true)
        set(v) = sp.edit().putBoolean("block_ig", v).apply()

    var blockYoutube: Boolean
        get() = sp.getBoolean("block_yt", true)
        set(v) = sp.edit().putBoolean("block_yt", v).apply()

    /** Daily allowance in minutes. 0 means Reels/Shorts are blocked completely. */
    var limitMinutes: Int
        get() = sp.getInt("limit_min", 0)
        set(v) = sp.edit().putInt("limit_min", v).apply()

    /** Hour (0-23) at which the daily allowance resets. */
    var resetHour: Int
        get() = sp.getInt("reset_hour", 0)
        set(v) = sp.edit().putInt("reset_hour", v).apply()

    var sleepEnabled: Boolean
        get() = sp.getBoolean("sleep_on", false)
        set(v) = sp.edit().putBoolean("sleep_on", v).apply()

    var sleepStart: Int
        get() = sp.getInt("sleep_start", 23)
        set(v) = sp.edit().putInt("sleep_start", v).apply()

    var sleepEnd: Int
        get() = sp.getInt("sleep_end", 7)
        set(v) = sp.edit().putInt("sleep_end", v).apply()

    /** Strict mode: settings lock once your allowance is used up (or during sleep mode). */
    var strict: Boolean
        get() = sp.getBoolean("strict", false)
        set(v) = sp.edit().putBoolean("strict", v).apply()

    /** Logs view IDs of the foreground Instagram/YouTube screen to Logcat. */
    var debug: Boolean
        get() = sp.getBoolean("debug", false)
        set(v) = sp.edit().putBoolean("debug", v).apply()

    // ---- usage --------------------------------------------------------------

    private fun today(): LocalDate =
        LocalDateTime.now().minusHours(resetHour.toLong()).toLocalDate()

    fun usedSeconds(): Int = sp.getInt("used_${today()}", 0)

    fun addSeconds(n: Int) {
        val key = "used_${today()}"
        sp.edit().putInt(key, sp.getInt(key, 0) + n).apply()
    }

    fun blockedToday(): Int = sp.getInt("blocked_${today()}", 0)

    fun addBlocked() {
        val key = "blocked_${today()}"
        sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply()
    }

    fun secondsLeft(): Int = limitMinutes * 60 - usedSeconds()

    fun sleepActive(): Boolean {
        if (!sleepEnabled || sleepStart == sleepEnd) return false
        val h = LocalDateTime.now().hour
        return if (sleepStart < sleepEnd) h in sleepStart until sleepEnd
        else h >= sleepStart || h < sleepEnd
    }

    fun exhausted(): Boolean = limitMinutes > 0 && usedSeconds() >= limitMinutes * 60

    fun locked(): Boolean = strict && (sleepActive() || exhausted())

    /** Drops usage entries older than two weeks. */
    fun pruneOld() {
        val cutoff = today().minusDays(14)
        val editor = sp.edit()
        for (key in sp.all.keys) {
            val date = when {
                key.startsWith("used_") -> key.removePrefix("used_")
                key.startsWith("blocked_") -> key.removePrefix("blocked_")
                else -> continue
            }
            val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: continue
            if (parsed.isBefore(cutoff)) editor.remove(key)
        }
        editor.apply()
    }

    fun snapshot(): Snapshot {
        val t = today()
        return Snapshot(
            blockInstagram = blockInstagram,
            blockYoutube = blockYoutube,
            limitMinutes = limitMinutes,
            resetHour = resetHour,
            sleepEnabled = sleepEnabled,
            sleepStart = sleepStart,
            sleepEnd = sleepEnd,
            strict = strict,
            debug = debug,
            usedSeconds = usedSeconds(),
            blockedToday = blockedToday(),
            week = (6 downTo 0).map { back ->
                val d = t.minusDays(back.toLong())
                d to sp.getInt("used_$d", 0)
            },
            sleepActive = sleepActive(),
            locked = locked(),
        )
    }
}
