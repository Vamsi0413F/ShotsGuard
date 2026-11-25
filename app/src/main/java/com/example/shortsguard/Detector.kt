package com.example.shortsguard

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Decides whether the foreground screen is the Reels / Shorts player.
 *
 * Instagram and YouTube change their internal view IDs from time to time. When
 * detection stops working, turn on "Debug logging" in the app, open Reels or
 * Shorts, read Logcat (tag ShortsGuard) and put the new ID in the lists below.
 * That is the only file you should need to touch.
 */
object Detector {
    const val INSTAGRAM = "com.instagram.android"
    const val YOUTUBE = "com.google.android.youtube"

    // Verify these on your own phone: they are best-effort and may be outdated.
    private val instagramIds = listOf(
        "clips_viewer_view_pager",
        "clips_viewer_video_layout",
        "clips_swipe_refresh_container",
    )
    private val youtubeIds = listOf(
        "reel_recycler",
        "reel_player_page_container",
        "reel_watch_fragment_root",
    )

    // Fallback: the bottom-bar tab for the section is currently selected (English UI only).
    private const val INSTAGRAM_TAB = "Reels"
    private const val YOUTUBE_TAB = "Shorts"

    fun isShortForm(pkg: String, root: AccessibilityNodeInfo): Boolean {
        val ids: List<String>
        val tabLabel: String
        when (pkg) {
            INSTAGRAM -> { ids = instagramIds; tabLabel = INSTAGRAM_TAB }
            YOUTUBE -> { ids = youtubeIds; tabLabel = YOUTUBE_TAB }
            else -> return false
        }

        for (id in ids) {
            if (root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").isNotEmpty()) return true
        }

        return root.findAccessibilityNodeInfosByText(tabLabel).any { node ->
            val label = node.contentDescription?.toString() ?: node.text?.toString()
            node.isSelected && label.equals(tabLabel, ignoreCase = true)
        }
    }
}
