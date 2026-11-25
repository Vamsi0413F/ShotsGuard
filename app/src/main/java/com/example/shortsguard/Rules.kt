package com.example.shortsguard

import org.json.JSONObject

/**
 * The view IDs and tab labels used to recognise Reels / Shorts.
 *
 * Final list = built-in IDs + IDs from the hosted rules file (if any) + IDs you typed in the app.
 *
 * Hosted rules file format (put it anywhere reachable over https, e.g. a GitHub raw link):
 *
 *   {
 *     "instagram": { "ids": ["clips_viewer_view_pager"], "tab": "Reels" },
 *     "youtube":   { "ids": ["reel_recycler"],           "tab": "Shorts" }
 *   }
 *
 * "tab" is optional. Remote IDs are added to the built-in ones, never replace them.
 */
object Rules {
    // Best-effort defaults: verify on your own phone using Debug logging.
    private val builtinInstagram = listOf(
        "clips_viewer_view_pager",
        "clips_viewer_video_layout",
        "clips_swipe_refresh_container",
    )
    private val builtinYoutube = listOf(
        "reel_recycler",
        "reel_player_page_container",
        "reel_watch_fragment_root",
    )

    private val idPattern = Regex("[A-Za-z0-9_]+")

    @Volatile var instagramIds: List<String> = builtinInstagram
        private set
    @Volatile var youtubeIds: List<String> = builtinYoutube
        private set
    @Volatile var instagramTab: String = "Reels"
        private set
    @Volatile var youtubeTab: String = "Shorts"
        private set

    fun reload(prefs: Prefs) {
        var ig = builtinInstagram
        var yt = builtinYoutube
        var igTab = "Reels"
        var ytTab = "Shorts"

        runCatching {
            val root = JSONObject(prefs.remoteJson)
            root.optJSONObject("instagram")?.let { o ->
                ig = ig + idsOf(o)
                o.optString("tab").trim().takeIf { it.isNotEmpty() }?.let { igTab = it }
            }
            root.optJSONObject("youtube")?.let { o ->
                yt = yt + idsOf(o)
                o.optString("tab").trim().takeIf { it.isNotEmpty() }?.let { ytTab = it }
            }
        }

        instagramIds = (ig + parse(prefs.extraInstagramIds)).distinct()
        youtubeIds = (yt + parse(prefs.extraYoutubeIds)).distinct()
        instagramTab = igTab
        youtubeTab = ytTab
    }

    private fun idsOf(o: JSONObject): List<String> {
        val arr = o.optJSONArray("ids") ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it).trim() }.filter { idPattern.matches(it) }
    }

    /** Accepts "a, b" or full IDs like "com.google.android.youtube:id/a". */
    private fun parse(text: String): List<String> =
        text.split(',', ' ', '\n')
            .map { it.trim().substringAfter(":id/") }
            .filter { idPattern.matches(it) }
}
