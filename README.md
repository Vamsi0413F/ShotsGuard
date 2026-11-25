# ShortsGuard

Android app that blocks Instagram Reels and YouTube Shorts using an Accessibility Service, with a daily watch-time allowance.

## Open and run
1. Android Studio: File > Open > select this folder, let Gradle sync (needs internet the first time).
2. Run on a physical phone.
3. On the phone: open ShortsGuard > "Open Accessibility settings" > Installed apps > ShortsGuard > turn on.
   - Android 13+ with a sideloaded build: the switch may be greyed out. Open App info > three-dot menu > "Allow restricted settings", then try again.

## What it does
- Per-app toggles: Instagram Reels, YouTube Shorts.
- Daily allowance (0-120 min, 5-min steps). 0 = blocked completely. Only time on the Reels/Shorts screen counts.
- Allowance resets at an hour you choose (default 12 AM).
- Sleep mode: full block between two hours, regardless of allowance.
- Strict mode: settings lock once your allowance is used up or sleep mode is on.
- Stats: minutes today, times blocked today, last 7 days.
- Warning toast at 1 minute left. When time is up, the service presses Back (then Home if Back keeps landing on Reels/Shorts).

## License
MIT. See [LICENSE](LICENSE).
