package com.example.shortsguard

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class ShortsBlockerService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private var watchingPkg: String? = null
    private var lastCheck = 0L
    private var lastBack = 0L
    private var backAttempts = 0
    private var lastDump = 0L

    override fun onServiceConnected() {
        prefs = Prefs(this)
        prefs.pruneOld()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != Detector.INSTAGRAM && pkg != Detector.YOUTUBE) return

        // Instagram and YouTube fire many events per second; checking more often than this is wasted work.
        val now = SystemClock.uptimeMillis()
        if (now - lastCheck < 250) return
        lastCheck = now

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        if (prefs.debug) dumpIds(root)

        if (!isEnabledFor(pkg) || !Detector.isShortForm(pkg, root)) {
            stopTicker()
            return
        }

        val reason = blockReason()
        if (reason != null) {
            stopTicker()
            block(reason)
        } else {
            startTicker(pkg)
        }
    }

    override fun onInterrupt() = stopTicker()

    override fun onDestroy() {
        stopTicker()
        super.onDestroy()
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

    /** While Reels/Shorts is on screen, add one second to today's total every second. */
    private val ticker = object : Runnable {
        override fun run() {
            val pkg = watchingPkg ?: return
            val root = rootInActiveWindow
            val stillWatching = root != null &&
                root.packageName?.toString() == pkg &&
                Detector.isShortForm(pkg, root)
            if (!stillWatching) {
                stopTicker()
                return
            }

            prefs.addSeconds(1)

            val reason = blockReason()
            if (reason != null) {
                stopTicker()
                block(reason)
                return
            }
            if (prefs.secondsLeft() == 60) toast("1 minute of Reels/Shorts left today")
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

    // ---- blocking -----------------------------------------------------------

    private fun block(reason: String) {
        val now = SystemClock.uptimeMillis()
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

    private fun dumpIds(root: AccessibilityNodeInfo) {
        val now = SystemClock.uptimeMillis()
        if (now - lastDump < 3000) return
        lastDump = now

        val ids = linkedSetOf<String>()
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 25) return
            node.viewIdResourceName?.let {
                ids += it.substringAfter(":id/") + if (node.isSelected) "[selected]" else ""
            }
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(root, 0)
        Log.d("ShortsGuard", "${root.packageName}: ${ids.joinToString()}")
    }
}

fun formatHour(hour: Int): String {
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return "$h12 ${if (hour < 12) "AM" else "PM"}"
}
