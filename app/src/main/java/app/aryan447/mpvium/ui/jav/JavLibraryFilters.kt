package app.aryan447.mpvium.ui.jav

import androidx.annotation.StringRes
import app.aryan447.mpvium.R
import app.aryan447.mpvium.domain.jav.JavLibraryItem

/**
 * Watch-state filter for the JAV library, mirroring the Titles tab watch
 * filter without pulling its movie/series-specific matching into JAV.
 */
enum class JavWatchFilter(@StringRes val labelRes: Int) {
  ALL(R.string.jav_library_filter_all),
  UNWATCHED(R.string.jav_library_filter_unwatched),
  IN_PROGRESS(R.string.jav_library_filter_in_progress),
  WATCHED(R.string.jav_library_filter_watched);

  fun matches(item: JavLibraryItem): Boolean = when (this) {
    ALL -> true
    UNWATCHED -> !item.isWatched && item.resumePositionMs <= 0L
    IN_PROGRESS -> !item.isWatched && item.resumePositionMs > 0L
    WATCHED -> item.isWatched
  }
}

/**
 * Sort options for the JAV library. [RECENTLY_ADDED] uses the media index date
 * with the file timestamp as a fallback for direct-path scans.
 */
enum class JavLibrarySort(@StringRes val labelRes: Int) {
  RECENTLY_ADDED(R.string.jav_library_sort_recent),
  NAME(R.string.jav_library_sort_name),
  RELEASE_DATE(R.string.jav_library_sort_release_date),
  LONGEST(R.string.jav_library_sort_longest),
  SHORTEST(R.string.jav_library_sort_shortest),
  CODE(R.string.jav_library_sort_code),
}

fun List<JavLibraryItem>.filterJavItems(
  query: String,
  watchFilter: JavWatchFilter,
): List<JavLibraryItem> {
  val normalized = query.trim().lowercase()
  return filter { item ->
    watchFilter.matches(item) && (normalized.isEmpty() || item.matchesQuery(normalized))
  }
}

fun List<JavLibraryItem>.sortedByJavSort(sort: JavLibrarySort): List<JavLibraryItem> = when (sort) {
  JavLibrarySort.NAME -> sortedWith(compareBy<JavLibraryItem>({ it.title.lowercase() }, { it.code }))
  JavLibrarySort.CODE -> sortedWith(compareBy<JavLibraryItem> { it.code.lowercase() })
  JavLibrarySort.RECENTLY_ADDED -> sortedWith(
    compareByDescending<JavLibraryItem> {
      it.video.dateAdded.takeIf { date -> date > 0 } ?: it.file.mtimeMs
    }.thenBy { it.title.lowercase() },
  )
  JavLibrarySort.RELEASE_DATE -> sortedWith(
    compareByDescending<JavLibraryItem> { it.metadata?.releaseDate.orEmpty() }
      .thenBy { it.title.lowercase() },
  )
  JavLibrarySort.LONGEST -> sortedWith(
    compareByDescending<JavLibraryItem> { it.effectiveDurationMs }.thenBy { it.title.lowercase() },
  )
  JavLibrarySort.SHORTEST -> sortedWith(
    compareBy<JavLibraryItem> { it.effectiveDurationMs }.thenBy { it.title.lowercase() },
  )
}

private val JavLibraryItem.effectiveDurationMs: Long
  get() = video.duration.takeIf { it > 0 }
    ?: ((metadata?.runtimeMinutes ?: 0) * 60_000L)

private fun JavLibraryItem.matchesQuery(query: String): Boolean {
  if (code.lowercase().contains(query)) return true
  if (title.lowercase().contains(query)) return true
  if (actresses.any { it.lowercase().contains(query) }) return true
  if (genres.any { it.lowercase().contains(query) }) return true
  val meta = metadata ?: return false
  return listOf(meta.studio, meta.label, meta.series, meta.director)
    .any { value -> value?.lowercase()?.contains(query) == true }
}
