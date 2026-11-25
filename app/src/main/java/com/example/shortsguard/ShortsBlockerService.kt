package com.example.shortsguard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class ShortsBlockerService : AccessibilityService() {

    /** One on-screen window of the Instagram/YouTube app. */
    private class Win(val root: AccessibilityNodeInfo, val active: Boolean)

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private var watchingPkg: String? = null
    private var lastCheck = 0L
    private var lastBack = 0L
    private var backAttempts = 0
    private var lastDump = 0L
    private var lastHint = 0L
    private var lastCountAt = 0L
    private var lastShortSeen = 0L

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "remote_json" || key == "extra_ig" || key == "extra_yt") Rules.reload(prefs)
    }

    override fun onServiceConnected() {
        prefs = Prefs(this)
        prefs.pruneOld()
        Rules.reload(prefs)
        prefs.registerListener(prefListener)
        prefs.serviceWasOn = true

        Status.createChannels(this)
        Status.clearServiceOff(this)
        handler.removeCallbacks(statusLoop)
        handler.post(statusLoop)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopTicker()
        handler.removeCallbacks(statusLoop)
        if (::prefs.isInitialized) {
            prefs.unregisterListener(prefListener)
            Status.showServiceOff(this)
        }
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != Detector.INSTAGRAM && pkg != Detector.YOUTUBE) return

        // Swiping to the next Reel/Short fires scroll events. While one is being watched (allowed),
        // count one per swipe. Several events per swipe are merged by the 900 ms gap.
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val t = SystemClock.uptimeMillis()
            if (watchingPkg == pkg && t - lastCountAt > 900) {
                lastCountAt = t
                prefs.addCount(pkg)
            }
            return
        }

        // Instagram and YouTube fire many events per second; checking more often than this is wasted work.
        val now = SystemClock.uptimeMillis()
        if (now - lastCheck < 250) return
        lastCheck = now

        if (prefs.debug) dump(pkg)

        // Look at every window of the app, not only the focused one: a minimised, floating or
        // split-screen Shorts player is a separate window and is not the "active" window.
        val shortWin = rootsFor(pkg).firstOrNull { Detector.isShortForm(pkg, it.root) }
        if (shortWin == null || !isEnabledFor(pkg)) {
            stopTicker()
            return
        }

        val reason = blockReason()
        if (reason == null) {
            val t = SystemClock.uptimeMillis()
            // First Reel/Short of this visit (a gap of 3 s without one means a new visit).
            if (watchingPkg != pkg && t - lastShortSeen > 3000) {
                lastCountAt = t
                prefs.addCount(pkg)
            }
            lastShortSeen = t
            startTicker(pkg)
        } else {
            stopTicker()
            block(reason, shortWin.active)
        }
    }

    override fun onInterrupt() = stopTicker()

    override fun onDestroy() {
        stopTicker()
        handler.removeCallbacks(statusLoop)
        super.onDestroy()
    }

    // ---- windows ------------------------------------------------------------

    /** Every window belonging to [pkg], marking which one has focus. */
    private fun rootsFor(pkg: String): List<Win> {
        val result = mutableListOf<Win>()
        for (window in windows) {
            val root = window.root ?: continue
            if (root.packageName?.toString() == pkg) result += Win(root, window.isActive)
        }
        if (result.isEmpty()) {
            rootInActiveWindow
                ?.takeIf { it.packageName?.toString() == pkg }
                ?.let { result += Win(it, true) }
        }
        return result
    }

    private fun isEnabledFor(pkg: String) = when (pkg) {
        Detector.INSTAGRAM -> prefs.blockInstagram
        Detector.YOUTUBE -> prefs.blockYoutube
        else -> false
    }

    /** Returns why Reels/Shorts should be blocked right now, or null if watching is allowed. */
    private fun blockReason(): String? = when {
        prefs.sleepActive() -> "Sleep mode is on. Reels and Shorts are blocked."
        prefs.limitMinutes <= 0 -> "Reels and Shorts are blocked."
        prefs.exhausted() -> "Daily limit used up. Resets at ${formatHour(prefs.resetHour)}."
        else -> null
    }

    // ---- watch-time counting -----------------------------------------------

    /** While Reels/Shorts is on screen (in any window), add one second to today's total every second. */
    private val ticker = object : Runnable {
        override fun run() {
            val pkg = watchingPkg ?: return
            val stillWatching = rootsFor(pkg).any { Detector.isShortForm(pkg, it.root) }
            if (!stillWatching) {
                stopTicker()
                return
            }

            prefs.addSeconds(1)
            lastShortSeen = SystemClock.uptimeMillis()

            val reason = blockReason()
            if (reason != null) {
                val active = rootsFor(pkg).any { it.active && Detector.isShortForm(pkg, it.root) }
                stopTicker()
                block(reason, active)
                return
            }
            val left = prefs.secondsLeft()
            if (left == 60) toast("1 minute of Reels/Shorts left today")
            if (left % 10 == 0) Status.refresh(this@ShortsBlockerService, prefs)
            handler.postDelayed(this, 1000)
        }
    }

    private fun startTicker(pkg: String) {
        if (watchingPkg == pkg) return
        watchingPkg = pkg
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 1000)
    }

    private fun stopTicker() {
        watchingPkg = null
        handler.removeCallbacks(ticker)
    }

    /** Keeps the notification and widget fresh, and re-downloads the hosted rules once a day. */
    private val statusLoop = object : Runnable {
        override fun run() {
            Status.refresh(this@ShortsBlockerService, prefs)
            val stale = System.currentTimeMillis() - prefs.remoteFetchedAt > 24 * 60 * 60 * 1000L
            if (prefs.remoteUrl.isNotBlank() && stale) RemoteRules.fetch(prefs)
            handler.postDelayed(this, 30_000)
        }
    }

    // ---- blocking -----------------------------------------------------------

    private fun block(reason: String, canPressBack: Boolean) {
        val now = SystemClock.uptimeMillis()

        if (!canPressBack) {
            // The Shorts are in a small or background window. Back would act on whichever app has
            // focus, so don't press it. Just tell the user.
            if (now - lastHint > 10_000) {
                lastHint = now
                toast("$reason Close the small Shorts window.")
            }
            return
        }

        if (now - lastBack < 700) return
        backAttempts = if (now - lastBack > 4000) 1 else backAttempts + 1
        lastBack = now

        if (backAttempts > 3) {
            // Back keeps landing on Reels/Shorts (e.g. the app opened straight into it): go home instead.
            performGlobalAction(GLOBAL_ACTION_HOME)
            backAttempts = 0
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        prefs.addBlocked()
        if (backAttempts <= 1) toast(reason)
    }

    private fun toast(message: String) =
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()

    // ---- debugging ----------------------------------------------------------

    /** Writes the windows and view IDs currently on screen to Logcat and to Prefs.lastDump (shown in the app). */
    private fun dump(pkg: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastDump < 3000) return
        lastDump = now

        val text = StringBuilder()
        text.append("Windows: ").append(
            windows.joinToString { w ->
                "${w.root?.packageName}(active=${w.isActive}, pip=${w.isInPictureInPictureMode})"
            },
        ).append('\n')
        for (win in rootsFor(pkg)) {
            val ids = linkedSetOf<String>()
            collectIds(win.root, 0, ids)
            text.append(if (win.active) "[focused window] " else "[other window] ")
                .append(ids.joinToString()).append('\n')
        }
        val out = text.toString().take(3000)
        Log.d("ShortsGuard", out)
        prefs.lastDump = out
    }

    private fun collectIds(node: AccessibilityNodeInfo?, depth: Int, out: MutableSet<String>) {
        if (node == null || depth > 25) return
        node.viewIdResourceName?.let {
            out += it.substringAfter(":id/") + if (node.isSelected) "[selected]" else ""
        }
        for (i in 0 until node.childCount) collectIds(node.getChild(i), depth + 1, out)
    }
}

fun formatHour(hour: Int): String {
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return "$h12 ${if (hour < 12) "AM" else "PM"}"
}
