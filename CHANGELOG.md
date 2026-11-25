# Changelog

All notable changes to ShortsGuard are listed here, newest first.

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
