package com.example.shortsguard

import android.content.Context
import android.content.SharedPreferences
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
    val liveNotification: Boolean,
    val alertWhenOff: Boolean,
    val remoteFetchedAt: Long,
    val lastDump: String,
    val usedSeconds: Int,
    val blockedToday: Int,
    val igToday: Int,
    val ytToday: Int,
    val combinedCount: Boolean,
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

    /** Logs view IDs of the foreground Instagram/YouTube screen to Logcat and stores them for the app to show. */
    var debug: Boolean
        get() = sp.getBoolean("debug", false)
        set(v) = sp.edit().putBoolean("debug", v).apply()

    // ---- setup, notifications, alerts -----------------------------------------

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    /** Ongoing "N min left today" notification. */
    var liveNotification: Boolean
        get() = sp.getBoolean("live_notif", true)
        set(v) = sp.edit().putBoolean("live_notif", v).apply()

    /** Notify when the accessibility service gets switched off. */
    var alertWhenOff: Boolean
        get() = sp.getBoolean("alert_off", true)
        set(v) = sp.edit().putBoolean("alert_off", v).apply()

    /** Show one combined "watched" count instead of separate Reels and Shorts counts. */
    var combinedCount: Boolean
        get() = sp.getBoolean("count_combined", false)
        set(v) = sp.edit().putBoolean("count_combined", v).apply()

    /** True once the service has been enabled at least once, so we only alert about a service that was on. */
    var serviceWasOn: Boolean
        get() = sp.getBoolean("service_was_on", false)
        set(v) = sp.edit().putBoolean("service_was_on", v).apply()

    // ---- detection rules ------------------------------------------------------

    var remoteUrl: String
        get() = sp.getString("remote_url", "") ?: ""
        set(v) = sp.edit().putString("remote_url", v).apply()

    var remoteJson: String
        get() = sp.getString("remote_json", "") ?: ""
        set(v) = sp.edit().putString("remote_json", v).apply()

    var remoteFetchedAt: Long
        get() = sp.getLong("remote_fetched_at", 0L)
        set(v) = sp.edit().putLong("remote_fetched_at", v).apply()

    /** Extra Instagram view IDs added by hand, comma separated. */
    var extraInstagramIds: String
        get() = sp.getString("extra_ig", "") ?: ""
        set(v) = sp.edit().putString("extra_ig", v).apply()

    /** Extra YouTube view IDs added by hand, comma separated. */
    var extraYoutubeIds: String
        get() = sp.getString("extra_yt", "") ?: ""
        set(v) = sp.edit().putString("extra_yt", v).apply()

    /** Last capture of on-screen view IDs (only written while debug logging is on). */
    var lastDump: String
        get() = sp.getString("last_dump", "") ?: ""
        set(v) = sp.edit().putString("last_dump", v).apply()

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(listener)

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

    private fun countKey(pkg: String) =
        if (pkg == Detector.INSTAGRAM) "count_ig_${today()}" else "count_yt_${today()}"

    /** How many Reels (Instagram) or Shorts (YouTube) were watched today. */
    fun countToday(pkg: String): Int = sp.getInt(countKey(pkg), 0)

    fun addCount(pkg: String) {
        val key = countKey(pkg)
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
                key.startsWith("count_ig_") -> key.removePrefix("count_ig_")
                key.startsWith("count_yt_") -> key.removePrefix("count_yt_")
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
            liveNotification = liveNotification,
            alertWhenOff = alertWhenOff,
            remoteFetchedAt = remoteFetchedAt,
            lastDump = lastDump,
            usedSeconds = usedSeconds(),
            blockedToday = blockedToday(),
            igToday = countToday(Detector.INSTAGRAM),
            ytToday = countToday(Detector.YOUTUBE),
            combinedCount = combinedCount,
            week = (6 downTo 0).map { back ->
                val d = t.minusDays(back.toLong())
                d to sp.getInt("used_$d", 0)
            },
            sleepActive = sleepActive(),
            locked = locked(),
        )
    }
}
