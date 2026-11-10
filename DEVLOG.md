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

## [2026-11-10 12:23] Change #3: Add JavDB, WorkManager batches, and TMDb collections
- **Purpose**: Complete the planned JAV and metadata pipeline through JavDB fallback, background batch jobs, TMDb enrichment, and movie collections.
- **Approach**: Keep each source behind a client/repository boundary; persist scrape state in Room; use WorkManager for long-running batches; extend the existing metadata cache with optional TMDb fields so old caches remain readable.
- **Proposal**: JavBus remains primary for standard codes; JavDB is used first for FC2/amateur/date codes and as fallback/enrichment elsewhere. TMDb direct details are additive and fall back to the existing Wyzie/Cinemeta path.
- **Decision**: Implemented JavDB public API client with host cooling and App signature, JAV scrape/cover WorkManager jobs, TMDb client and enrichment worker, collection cache fields, and collection list/detail screens.
- **Files**: `app/src/main/java/app/aryan447/mpvium/domain/jav/`, `app/src/main/java/app/aryan447/mpvium/domain/tmdb/`, `app/src/main/java/app/aryan447/mpvium/ui/jav/`, `app/src/main/java/app/aryan447/mpvium/ui/tmdb/`, `app/src/main/java/app/aryan447/mpvium/domain/streaming/StreamingMetadataRepository.kt`, `app/src/main/java/app/aryan447/mpvium/domain/streaming/model/StreamingModels.kt`, `app/src/main/java/app/aryan447/mpvium/di/DomainModule.kt`, `app/src/main/java/app/aryan447/mpvium/di/PreferencesModule.kt`, `app/src/main/java/app/aryan447/mpvium/preferences/`, `app/src/main/java/app/aryan447/mpvium/ui/preferences/SearchablePreference.kt`, `app/src/main/java/app/aryan447/mpvium/ui/streaming/more/MoreLibraryScreen.kt`, `app/src/main/java/app/aryan447/mpvium/ui/streaming/movies/MovieDetailScreen.kt`, `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/res/values/strings.xml`, `CHANGELOG.md`
- **Risks**: JavDB App signature constants are fixed and may expire; JavDB host policy may change; WorkManager foreground needs notification permission; TMDb enrichment is network-heavy and may overwrite automatic matches (manual/custom fields are preserved); API keys are stored in normal SharedPreferences.
- **Verification**: Pending CI compile and artifact build. Previous P0/P1 commit passed the fork CI build.

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
