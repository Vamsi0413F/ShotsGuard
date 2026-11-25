# ShortsGuard

Android app that blocks Instagram Reels and YouTube Shorts using an Accessibility Service, with a daily watch-time allowance.

## Open and run
1. Android Studio: File > Open > select this folder, let Gradle sync (needs internet the first time).
2. Build > Generate App Bundles or APKs > Generate APKs, then install `app-debug.apk` on your phone.
   If Play Protect blocks it, turn off "Scan apps with Play Protect" briefly, install, then turn it back on.
3. Open the app. A first-run walkthrough guides you through the accessibility service, notifications and battery settings.

## What it does
- Per-app toggles: Instagram Reels, YouTube Shorts.
- Daily allowance (0-120 min, 5-min steps). 0 = blocked completely. Only time on the Reels/Shorts screen counts, including a minimised or floating Shorts window.
- Allowance resets at an hour you choose (default 12 AM).
- Sleep mode: full block between two hours, regardless of allowance.
- Strict mode: settings lock once your allowance is used up or sleep mode is on.
- Stats: minutes today, times blocked today, last 7 days.
- Live "N min left today" ongoing notification and a home-screen widget (long-press home screen > Widgets > ShortsGuard).
- First-run walkthrough and a setup checklist (accessibility, notifications, battery) on the main screen.
- Battery exemption prompt (set to Unrestricted) so phones do not kill the blocker.
- Alert notification if the accessibility service gets switched off (immediate, plus a 15-minute backup check).

## Detection rules
Instagram and YouTube rename internal view IDs, so detection can break after an app update. Fix it without rebuilding:
- Add IDs by hand under "Detection rules" in the app, or
- Host a JSON file and paste its https link (see [docs/rules.example.json](docs/rules.example.json)). The app re-downloads it daily.

To find the right IDs, turn on "Debug logging" under Troubleshooting, open Reels or Shorts, wait about 5 seconds and come back to the app: the captured windows and view IDs are shown there with a Copy button.

## License
MIT. See [LICENSE](LICENSE).

Not affiliated with, endorsed by or sponsored by Instagram, Meta, YouTube or Google.
