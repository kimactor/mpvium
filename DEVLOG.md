# DEVLOG.md - Development Log

> Every code change MUST be logged here: create an entry before starting (title + known fields), backfill the rest after finishing. Newest on top. Mistakes, user corrections, and overlooked items must be recorded under "Lesson" - never silently skipped.
> Entries are written in English (ASCII: immune to Windows GBK/UTF-8 corruption and renders identically in any editor/terminal). User-facing docs under docs/ stay Chinese.
> Template:
>
> ```markdown
> ## [YYYY-MM-DD HH:MM] Change #N: <one-line title>
> - **Purpose**: what problem to solve
> - **Approach**: analysis, options considered
> - **Proposal**: agent suggestion and rationale
> - **Decision**: what was done and why
> - **Files**: list of changed files
> - **Risks**: impact, compatibility, rollback
> - **Verification**: how validated + actual result
> - **Lesson**: what went wrong / missed, next time (omit if none)
> ```
>
> Archiving: when entries > 30 or file > 64 KB, or on a major-version release, move older entries to DEVLOG-archive/DEVLOG-<date>_<batch>.md and keep <= 30 entries / <= 64 KB. Look up history with rg in DEVLOG.md and DEVLOG-archive/ (never read whole files).
>
> Older entries: see DEVLOG-archive/ (once archiving starts).

## [2026-11-10 20:23] Change #5: Rework JAV library card layout
- **Purpose**: The JAV library forced three poster columns, the code was drawn over the cover, and long titles were truncated, so covers and metadata were hard to read.
- **Approach**: Considered 2-column grid only, 1-column list only, and a persisted grid/list toggle. User chose the top-bar toggle with a list default and asked that each item show the complete title, code, and actresses without overlapping text.
- **Proposal**: Default to a single-column list (large cover left, untruncated text right), keep an optional grid with 2 phone / 3 landscape / 4-5 tablet columns, and move all text below the cover.
- **Decision**: Defaulted the library to a persisted LIST layout, added a top-bar `MediaLayoutToggle`, and rebuilt both modes around a shared poster and info column. List rows use a 96 dp (phone) / 128 dp (tablet) 2:3 poster with a full-width title, code, and actress block; the grid now uses 2 phone / 3 landscape / 4-5 tablet columns and keeps all text below the artwork. The code line is hidden when the title already equals the code.
- **Files**: `app/src/main/java/app/aryan447/mpvium/preferences/JavPreferences.kt`, `app/src/main/java/app/aryan447/mpvium/ui/jav/JavLibraryScreen.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh-rCN/strings.xml`, `CHANGELOG.md`
- **Risks**: Long Japanese titles and large actress lists make list rows tall; grid rows with full text can become uneven. Layout preference defaults to list for existing users.
- **Verification**: `git diff --check` reports only the expected LF/CRLF warning. Both `strings.xml` files parse as valid UTF-8 XML. Brace and parenthesis balance checked on the screen file. No Kotlin/Gradle build or device check was possible: `java`, Android SDK, and the Gradle wrapper distribution are not installed locally, and project policy forbids downloading them without approval. CI/device verification is still pending.
- **Lesson**: PowerShell 5.1 `Get-Content` decoded the UTF-8 `strings.xml` as ANSI and produced false XML parse errors; use `.NET UTF8.GetString(ReadAllBytes(...))` for XML checks on this machine.

## [2026-11-10 17:29] Change #4: Add language selection and repair cover prefetch
- **Purpose**: Make the app language selectable and fix `Prefetch covers` reporting no targets when scraped metadata lacked a cover URL.
- **Approach**: Add a persisted `AppLanguage` preference with a context locale wrapper for MainActivity, MediaInfoActivity, and PlayerActivity; add partial `values-zh-rCN` resources for the new JAV/metadata/collections/Library surfaces. Replace the prefetch filter with a repair pass that checks local files, retries scraping when `coverUrl` is missing, downloads covers, and records success/failure counts.
- **Proposal**: Keep the source chain unchanged; distinguish "already cached", "no scraped metadata", "no cover URL", and download failures in the batch UI instead of a single no-targets message.
- **Decision**: Implemented language selection plus batch result cards and last-error display. Prefetch now verifies file existence and can re-scrape missing cover URLs before downloading.
- **Files**: `app/src/main/java/app/aryan447/mpvium/preferences/AppearancePreferences.kt`, `app/src/main/java/app/aryan447/mpvium/utils/locale/LocaleHelper.kt`, `App.kt`, `MainActivity.kt`, `MediaInfoActivity.kt`, `PlayerActivity.kt`, `ui/preferences/AppearancePreferencesScreen.kt`, `ui/jav/JavBatchScreen.kt`, `ui/jav/JavDetailScreen.kt`, `domain/jav/JavBatchWorker.kt`, `domain/jav/JavBatchScheduler.kt`, `ui/streaming/more/MoreLibraryScreen.kt`, `AndroidManifest.xml`, `res/xml/locales_config.xml`, `res/values/strings.xml`, `res/values-zh-rCN/strings.xml`, `CHANGELOG.md`
- **Risks**: Chinese resources are intentionally partial and legacy screens fall back to English; prefetch can now trigger source requests for items without a cover URL; JavBus/JavDB success still depends on cookie/signature/network.
- **Verification**: CI run 42791413306 passed (3m11s) and produced `app-standard-release-apks-ci-signed-f82c3a0`; final APK is `dist-apk/app-standard-release.apk` (100,841,596 bytes, valid ZIP, APK Signature Scheme v2 block present, Chinese resources included). Static XML parsing, string-reference checks, and `git diff --check` pass locally.
- **Lesson**: AGP already auto-generates the per-app locale config from translated resource folders; adding `android:localeConfig` manually makes manifest processing fail with "Locale config generation was requested but user locale config is present". Rely on the generated config. Chinese resources are currently partial, so untranslated legacy screens fall back to English.

## [2026-11-10 12:23] Change #3: Add JavDB, WorkManager batches, and TMDb collections
- **Purpose**: Complete the planned JAV and metadata pipeline through JavDB fallback, background batch jobs, TMDb enrichment, and movie collections.
- **Approach**: Keep each source behind a client/repository boundary; persist scrape state in Room; use WorkManager for long-running batches; extend the existing metadata cache with optional TMDb fields so old caches remain readable.
- **Proposal**: JavBus remains primary for standard codes; JavDB is used first for FC2/amateur/date codes and as fallback/enrichment elsewhere. TMDb direct details are additive and fall back to the existing Wyzie/Cinemeta path.
- **Decision**: Implemented JavDB public API client with host cooling and App signature, JAV scrape/cover WorkManager jobs, TMDb client and enrichment worker, collection cache fields, and collection list/detail screens.
- **Files**: `app/src/main/java/app/aryan447/mpvium/domain/jav/`, `app/src/main/java/app/aryan447/mpvium/domain/tmdb/`, `app/src/main/java/app/aryan447/mpvium/ui/jav/`, `app/src/main/java/app/aryan447/mpvium/ui/tmdb/`, `app/src/main/java/app/aryan447/mpvium/domain/streaming/StreamingMetadataRepository.kt`, `app/src/main/java/app/aryan447/mpvium/domain/streaming/model/StreamingModels.kt`, `app/src/main/java/app/aryan447/mpvium/di/DomainModule.kt`, `app/src/main/java/app/aryan447/mpvium/di/PreferencesModule.kt`, `app/src/main/java/app/aryan447/mpvium/preferences/`, `app/src/main/java/app/aryan447/mpvium/ui/preferences/SearchablePreference.kt`, `app/src/main/java/app/aryan447/mpvium/ui/streaming/more/MoreLibraryScreen.kt`, `app/src/main/java/app/aryan447/mpvium/ui/streaming/movies/MovieDetailScreen.kt`, `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/res/values/strings.xml`, `CHANGELOG.md`
- **Risks**: JavDB App signature constants are fixed and may expire; JavDB host policy may change; WorkManager foreground needs notification permission; TMDb enrichment is network-heavy and may overwrite automatic matches (manual/custom fields are preserved); API keys are stored in normal SharedPreferences.
- **Verification**: Final fork CI build 42758388719 passed (2m38s) and produced `app-standard-release-apks-ci-signed-7299257`; the downloaded APK is at `dist-apk/app-standard-release.apk` (100,817,184 bytes, valid ZIP, APK Signature Scheme v2 block present). Room v11 schema was generated by CI, compared with `MIGRATION_10_11`, and committed as `app/schemas/app.aryan447.mpvium.database.MpviumDatabase/11.json`. Live JavDB smoke checks returned HTTP 200 for search and detail on two API hosts, with expected number/title/cover/actors/tags fields. Local Gradle/JUnit still cannot run because the machine has no Java/Kotlin/Android SDK or Gradle distribution.

## [2026-11-10 11:44] Change #2: Add JavBus source and JAV library screens
- **Purpose**: Deliver the first usable JAV slice: scrape JavBus metadata, cache covers locally, and browse/play detected files in a dedicated library.
- **Approach**: Keep the network layer behind `JavBusSource` and `JavHttpClientFactory`; store metadata in the existing Room tables; build library/detail/settings screens from the existing Compose design system. JavDB remains disabled until the next phase.
- **Proposal**: Use JavBus as the primary source, require a user-supplied cookie for the age gate, and expose HTTP/SOCKS proxy plus custom User-Agent settings.
- **Decision**: Implemented the vertical slice with Room-backed metadata, local cover downloads, a JAV grid/detail flow, and a settings screen; registered a More Library entry and settings search entries.
- **Files**: `app/src/main/java/app/aryan447/mpvium/domain/jav/`, `app/src/main/java/app/aryan447/mpvium/ui/jav/`, `app/src/main/java/app/aryan447/mpvium/preferences/JavPreferences.kt`, `app/src/main/java/app/aryan447/mpvium/domain/streaming/StreamingArtworkStore.kt`, `app/src/main/java/app/aryan447/mpvium/di/DomainModule.kt`, `app/src/main/java/app/aryan447/mpvium/ui/streaming/more/MoreLibraryScreen.kt`, `app/src/main/java/app/aryan447/mpvium/ui/preferences/SearchablePreference.kt`, `app/src/main/res/values/strings.xml`, `app/src/test/java/app/aryan447/mpvium/domain/jav/JavBusSourceTest.kt`, `CHANGELOG.md`
- **Risks**: JavBus HTML/age gate can change; the saved cookie and proxy password use normal SharedPreferences; SOCKS proxy authentication is not implemented; no JavDB fallback yet; no age-confirmation screen yet; no device build was run.
- **Verification**: `git diff --check` and `strings.xml` XML parsing pass; all new `R.string.jav_*` references resolve. JUnit/Gradle and Room schema generation were not run because no local Android SDK, Java/Kotlin compiler, or Gradle distribution is installed and the project forbids downloading them without approval.

## [2026-11-10 11:29] Change #1: Add JAV code detection and Room v11 foundation
- **Purpose**: Start Peach-style JAV library support with code parsing, persistence, and separation from the normal movie/TV library.
- **Approach**: Parse high-confidence Japanese AV release codes from filenames/folders, persist code-to-file mappings in Room, and filter matched files from the normal library when the JAV library is enabled. Use two tables (`jav_items`, `jav_files`) so scraped metadata and file identity remain independent.
- **Proposal**: Implement the P0 slice before network sources or UI: parser, entities/DAO, migration 10->11, and library filtering.
- **Decision**: Added `JavCodeParser` for standard, FC2, amateur, dated, HEYZO, and Tokyo-Hot shapes; added `jav_items`/`jav_files` Room tables with DAO and migration 10->11; `LibraryIndexRepository` now indexes high-confidence JAV files and optionally removes them from normal grouping.
- **Files**: `app/src/main/java/app/aryan447/mpvium/domain/jav/JavCodeParser.kt`, `app/src/main/java/app/aryan447/mpvium/database/entities/JavItemEntity.kt`, `app/src/main/java/app/aryan447/mpvium/database/entities/JavFileEntity.kt`, `app/src/main/java/app/aryan447/mpvium/database/dao/JavDao.kt`, `app/src/main/java/app/aryan447/mpvium/domain/streaming/LibraryIndexRepository.kt`, `app/src/main/java/app/aryan447/mpvium/database/MpviumDatabase.kt`, `app/src/main/java/app/aryan447/mpvium/di/DatabaseModule.kt`, `app/src/main/java/app/aryan447/mpvium/di/DomainModule.kt`, `app/src/main/java/app/aryan447/mpvium/preferences/JavPreferences.kt`, `app/src/main/java/app/aryan447/mpvium/di/PreferencesModule.kt`, `app/src/test/java/app/aryan447/mpvium/domain/jav/JavCodeParserTest.kt`, `CHANGELOG.md`
- **Risks**: False positives in code detection; Room schema 4/9/10 are absent from `app/schemas`; existing destructive fallback must not be relied on; JAV library is disabled by default until its UI and settings land.
- **Verification**: `git diff --check` passes. Regex behavior cross-checked with an equivalent Python harness for standard/FC2/amateur/dated/HEYZO/Tokyo-Hot and common false-positive filenames. Gradle/JUnit and Room schema generation were not run: no Android SDK, Gradle distribution, or Java/Kotlin compiler is installed locally, and project policy requires approval before downloading them. `11.json` will be generated by the first authorized/CI build.
