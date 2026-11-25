package com.example.shortsguard

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Decides whether a window is showing the Reels / Shorts player.
 *
 * Instagram and YouTube change their internal view IDs from time to time. The ID lists live in
 * [Rules], which also accepts IDs you add in the app or load from a hosted file, so you should not
 * need to edit code when detection breaks.
 */
object Detector {
    const val INSTAGRAM = "com.instagram.android"
    const val YOUTUBE = "com.google.android.youtube"

    fun isShortForm(pkg: String, root: AccessibilityNodeInfo): Boolean {
        val ids: List<String>
        val tabLabel: String
        when (pkg) {
            INSTAGRAM -> { ids = Rules.instagramIds; tabLabel = Rules.instagramTab }
            YOUTUBE -> { ids = Rules.youtubeIds; tabLabel = Rules.youtubeTab }
            else -> return false
        }

        for (id in ids) {
            if (root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").isNotEmpty()) return true
        }

        // Fallback: the bottom-bar tab for the section is currently selected (English UI only unless "tab" is set in the rules).
        return root.findAccessibilityNodeInfosByText(tabLabel).any { node ->
            val label = node.contentDescription?.toString() ?: node.text?.toString()
            node.isSelected && label.equals(tabLabel, ignoreCase = true)
        }
    }
}
