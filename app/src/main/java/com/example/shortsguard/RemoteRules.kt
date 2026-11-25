package com.example.shortsguard

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Downloads the hosted rules file (see [Rules] for the format) and caches it in [Prefs]. */
object RemoteRules {
    private val main = Handler(Looper.getMainLooper())

    /** Runs in the background. [onDone] is called on the main thread with a short human-readable result. */
    fun fetch(prefs: Prefs, onDone: ((String) -> Unit)? = null) {
        val link = prefs.remoteUrl.trim()
        if (!link.startsWith("https://")) {
            onDone?.invoke("Enter an https:// link first.")
            return
        }
        Thread {
            val message = try {
                val conn = URL(link).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.instanceFollowRedirects = true
                val code = conn.responseCode
                if (code != 200) {
                    "The server answered $code."
                } else {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }.take(65536)
                    val json = JSONObject(text)
                    if (json.optJSONObject("instagram") == null && json.optJSONObject("youtube") == null) {
                        "That file has no \"instagram\" or \"youtube\" section."
                    } else {
                        prefs.remoteJson = text
                        prefs.remoteFetchedAt = System.currentTimeMillis()
                        Rules.reload(prefs)
                        "Updated."
                    }
                }
            } catch (e: Exception) {
                "Could not load: ${e.message ?: e.javaClass.simpleName}"
            }
            main.post { onDone?.invoke(message) }
        }.start()
    }
}
