# Changelog

All notable changes to ShortsGuard are listed here, newest first.

## [1.1] - 2026-11-24

### Added
- Live "N min left today" ongoing notification and a home-screen widget.
- App icon (adaptive shield icon with a themed monochrome version).
- First-run setup walkthrough and a setup status checklist on the main screen.
- Battery optimisation exemption prompt so manufacturers do not kill the service.
- Alert notification when the accessibility service is switched off (immediate plus a 15-minute background check).
- Detection rules: add view IDs by hand or load them from a hosted JSON file (refreshed daily), so detection can be fixed without rebuilding. Example in `docs/rules.example.json`.
- Debug capture: the windows and view IDs on screen are stored and shown in the app with a Copy button.

### Fixed
- Minimised, floating or split-screen YouTube Shorts were not detected or counted. The service now scans every window of Instagram and YouTube instead of only the focused one. If the small window is not focused, ShortsGuard shows a toast instead of pressing Back (which would act on a different app).

## [1.0] - 2026-11-24

First working version.

### Added
- Accessibility service that detects the Instagram Reels and YouTube Shorts screens and presses Back.
- Per-app toggles for Instagram Reels and YouTube Shorts.
- Daily allowance from 0 to 120 minutes (0 = blocked completely) with a configurable reset hour.
- Sleep mode: full block between two chosen hours.
- Strict mode: settings lock once the allowance is used up or sleep mode is active.
- Stats: minutes used today, times blocked today, last 7 days chart.
- Warning toast at 1 minute left; falls back to Home if Back keeps landing on Reels or Shorts.
- Debug logging of on-screen view IDs to Logcat.
