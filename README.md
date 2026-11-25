<div align="center">

# 🛡️ ShortsGuard

**Block Instagram Reels and YouTube Shorts. Keep the rest of the apps.**

[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Version](https://img.shields.io/badge/version-1.2-0F766E)](CHANGELOG.md)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

</div>

ShortsGuard is a small Android app that stops the endless-scroll video feeds without making you delete Instagram or YouTube. Messages, search, subscriptions and regular videos keep working. Only the Reels and Shorts screens are blocked, completely or with a daily time allowance that resets on its own.

> **Status:** early personal project with limited testing. The Reels/Shorts detection depends on Instagram's and YouTube's internal screen IDs, which change over time. See [Detection rules](#detection-rules) for how to fix that without rebuilding.

## Features

| | |
|---|---|
| ⏱️ **Daily allowance** | 0 to 120 minutes in 5-minute steps. `0` blocks Reels and Shorts completely. Only time actually spent on those screens counts. |
| 🔄 **Custom reset time** | Pick the hour the allowance resets, e.g. 4 AM so late-night scrolling counts toward the previous day. |
| 🎛️ **Per-app switches** | Block Instagram Reels, YouTube Shorts, or both. |
| 🌙 **Sleep mode** | A full block between two hours, regardless of your allowance. |
| 🔒 **Strict mode** | Settings lock once your allowance is used up, so you cannot just raise the limit. |
| 🔢 **Watched counter** | How many Reels and Shorts you swiped through today, shown separately or combined. |
| 📊 **Stats** | Minutes used today, times blocked, and a 7-day chart. |
| 🔔 **Live status** | An ongoing "N min left today" notification and a home-screen widget. |
| 🪟 **Mini-player aware** | Minimised or floating Shorts windows are detected and their time is counted. |
| 🚨 **Switch-off alert** | A notification if the accessibility service gets turned off. |
| 🔋 **Battery guard** | Prompts you to set the app to unrestricted battery use so phones do not kill it. |
| 🧭 **Setup guide** | A first-run walkthrough for the accessibility, notification and battery steps. |
| 🛠️ **Fixable detection** | Add view IDs in the app or load them from a hosted JSON file. No rebuild needed. |

## How it works

ShortsGuard runs as an [Accessibility Service](https://developer.android.com/guide/topics/ui/accessibility/service) limited to two packages (`com.instagram.android` and `com.google.android.youtube`).

1. On screen changes it checks every window of those apps for a Reels/Shorts player, using known view IDs with a selected-tab check as a fallback.
2. While one is on screen and your allowance is not used up, it adds one second per second to today's total.
3. When time runs out (or the block is total), it presses **Back**. If Back keeps landing on Reels/Shorts, it goes **Home** instead.

It only looks at the screen structure of those two apps. It does not read, store or send messages, passwords or video content, and the app has no analytics. The only network request is the optional rules-file download you configure yourself.

## Install

There is no store release. Build the APK yourself:

1. Install [Android Studio](https://developer.android.com/studio).
2. Clone this repository and open the folder (**File > Open**). Let Gradle sync; Android Studio creates the Gradle wrapper if it is missing.
3. **Build > Generate App Bundles or APKs > Generate APKs**, then use **locate** to find `app-debug.apk`.
4. Copy the APK to your phone and install it. If **Play Protect** blocks it (it flags any sideloaded app that asks for the Accessibility permission), you can briefly turn off *Scan apps with Play Protect* in the Play Store settings, install, then turn it back on.

### First run

The setup guide walks you through three things:

1. **Accessibility service:** Settings > Accessibility > Installed apps > ShortsGuard > turn on. On Android 13+, if the switch is greyed out, open the app's **App info**, tap the three-dot menu and choose **Allow restricted settings**, then try again.
2. **Notifications:** for the live status and the switch-off alert.
3. **Battery:** set to *Unrestricted*. Some phones (Xiaomi, Oppo, Vivo, Samsung) also need *Autostart* or *Background activity* allowed in App info, otherwise they can quietly stop the service.

Reels and Shorts start out fully blocked. Use the **Daily allowance** slider if you want some time per day.

## Detection rules

Instagram and YouTube rename their internal view IDs from time to time, and the built-in IDs are best-effort. If blocking stops working, or a minimised Short is not counted:

1. In the app, open **Troubleshooting** and turn on **Debug logging**.
2. Open Reels or Shorts (or minimise a Short), wait about 5 seconds, then return to ShortsGuard.
3. The captured windows and view IDs are shown there, with a **Copy** button. Pick IDs that appear only on that screen.
4. Paste them into **Extra Instagram / YouTube view IDs** under **Detection rules**.

You can also host a rules file and paste its `https://` link into the app (it re-downloads daily, and **Update now** forces it). See [`docs/rules.example.json`](docs/rules.example.json):

```json
{
  "instagram": { "ids": ["clips_viewer_view_pager"], "tab": "Reels" },
  "youtube":   { "ids": ["reel_recycler"],           "tab": "Shorts" }
}
```

IDs from the file are added to the built-in ones. `tab` is optional: the bottom-bar label, useful when your phone is not set to English. A raw GitHub file or Gist link works well.

## Project structure

```
app/src/main/java/com/example/shortsguard/
├── ShortsBlockerService.kt   Accessibility service: detection, time counting, blocking
├── Detector.kt               Is this window the Reels/Shorts player?
├── Rules.kt                  Built-in + manual + hosted view IDs
├── RemoteRules.kt            Downloads the hosted rules file
├── Prefs.kt                  Settings and daily usage counters
├── Status.kt                 Notification, widget, switch-off alert
├── ShortsWidget.kt           Home-screen widget
├── AlertWorker.kt            15-minute backup check that the service is still on
├── Util.kt                   Permission and settings helpers
└── MainActivity.kt           Jetpack Compose UI and first-run guide
docs/rules.example.json       Example hosted rules file
```

## Known limitations

- The built-in view IDs were not verified against every Instagram/YouTube version.
- The tab-label fallback only works in English unless `tab` is set in the rules.
- Reels opened from DMs use the same player and are blocked too. Reels playing inline in the Instagram home feed are not detected yet.
- The watched count is based on swipes, so it can be off by a few.
- Anyone can still switch the service off in Android settings. Strict mode adds friction, not a hard lock. You will get an alert if it happens.
- Google Play restricts accessibility apps, so sideloading is the practical route.
- If a minimised Shorts window is not the focused window, ShortsGuard shows a toast instead of pressing Back, because Back would act on a different app.

## Roadmap

- [ ] Detect and optionally skip Reels playing inline in the Instagram home feed
- [ ] Facebook Reels, Snapchat Spotlight and TikTok
- [ ] Friction countdown before an allowance session starts
- [ ] Separate weekday and weekend limits
- [ ] Verified view-ID lists per app version

## Contributing

Issues and pull requests are welcome. If detection breaks after an app update, a captured list of view IDs from the debug screen (with the Instagram/YouTube version number) is the most useful thing to include.

## License

[MIT](LICENSE) © 2026 Vamsi0413F

ShortsGuard is not affiliated with, endorsed by or sponsored by Instagram, Meta, YouTube or Google. Those names are used only to say which apps the blocker acts on.
