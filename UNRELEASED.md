## Unreleased

<!-- Scratch pad. One short line per user-facing change while you work.
     At release time these are rewritten into the announcement style used in
     CHANGELOG.md — see AGENTS.md → "What's New". -->

### Added

- Custom Lua and mpv script buttons in player overlay with single tap and long-press actions, plus XML import and export.
- Theme Studio card opens My Canvas with full primary, secondary, and tertiary color customization, continuous HSV and hex color pickers, and live player controls and homepage previews.
- Holding any custom theme in the theme picker allows editing its colors or deleting it.
- JAV library with automatic release-code detection, a one-time 18+ confirmation, and optional hiding from the normal library.
- JAV metadata scraping from JavBus, JavDB, and DMM/FANZA, with batch scrape and cover prefetch jobs and per-item failure reasons.
- JAV library recognizes T28/T38, S2M/S2MBD/S2MCR, CW3D2DBD, MKD/MKBD, and IBW-483z release codes.
- JAV library layout: persisted grid/list toggle, single-column list rows with full titles and actresses, and landscape covers that show the whole artwork.
- App language setting: follow system, English, or Simplified Chinese, with Chinese resources for the JAV, metadata, collections, and Library screens.
- TMDb metadata and collections: optional API key, cast and studio enrichment, collection browsing with missing-part rows, and a background refresh job.
- Cover prefetch retries missing metadata and cover URLs, verifies local files, and reports downloaded, failed, missing-URL, and cached counts.
- JAV Chinese subtitles: sidecar files named by release code load automatically, local files can be imported from the detail screen, and missing ones can be searched and downloaded from SubtitleCat without an account or API key.
- JAV batch job for subtitles that scans the whole library and fills in missing Chinese subtitles.
- JAV library search, watch-state filters, sort options, and a result count.
- JAV cards show resume progress, watched, resolution, size, duration, and Chinese-subtitle badges.
- Long-press JAV cards to select several videos and mark them watched, in progress, or unwatched.
- JAV detail screens show the scraped overview, file facts, and actress, studio, series, and genre chips.

### Changed

- JAV metadata status now reads as plain language instead of raw scrape states.
- Modern is the default player layout; Classic is one tap away in Settings → Appearance → Player Layout.
- Player button backgrounds are now opt-in by default, giving controls clean floating icons that can be re-enabled in Settings → Player controls.

### Fixed

- Modern player layout custom action buttons scale consistently with built-in controls.
- Shader peek upscale chip no longer covers top-right player buttons when smart skip is absent.
- JAV scraping works without a manual JavBus cookie or JavDB login; JavBus age-gate cookies are sent automatically and auth failures are reported once instead of retried for every item.
- JAV scraping falls back to anonymous JavDB pages when its API asks for a login, restoring metadata and covers for FC2, amateur, and dated codes.
- JAV source cooldowns and JavDB API host bans survive app restarts instead of retrying a blocked site immediately.
- Cover prefetch skips JAV items whose retry time has not arrived and reports how many are waiting.
- JAV subtitle matching no longer applies a longer release code's subtitle to a shorter code, or marks Japanese-named subtitles as Chinese.
- JAV covers no longer crop away the sides of landscape artwork in the library.
- JAV library keeps its scroll position when you come back from a video instead of jumping to the top.
