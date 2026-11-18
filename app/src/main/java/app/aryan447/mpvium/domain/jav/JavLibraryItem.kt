package app.aryan447.mpvium.domain.jav

import app.aryan447.mpvium.database.entities.JavFileEntity
import app.aryan447.mpvium.database.entities.JavItemEntity
import app.aryan447.mpvium.database.entities.PlaybackStateEntity
import app.aryan447.mpvium.domain.media.model.Video

data class JavLibraryItem(
  val file: JavFileEntity,
  val metadata: JavItemEntity?,
  val video: Video,
  val actresses: List<String> = emptyList(),
  val genres: List<String> = emptyList(),
  val playbackState: PlaybackStateEntity? = null,
  val hasChineseSubtitle: Boolean = false,
) {
  val code: String get() = file.code

  val title: String
    get() = metadata?.manualTitle?.takeIf { it.isNotBlank() }
      ?: metadata?.title?.takeIf { it.isNotBlank() }
      ?: code

  val coverUrl: String?
    get() = metadata?.manualCoverPath?.takeIf { it.isNotBlank() }
      ?: metadata?.localCoverPath?.takeIf { it.isNotBlank() }
      ?: metadata?.coverUrl?.takeIf { it.isNotBlank() }

  val subtitle: String
    get() = listOfNotNull(
      metadata?.releaseDate?.takeIf { it.isNotBlank() },
      metadata?.studio?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

  val resumePositionMs: Long
    get() = (playbackState?.lastPosition ?: 0).coerceAtLeast(0) * 1000L

  val isWatched: Boolean
    get() = playbackState?.hasBeenWatched == true

  /**
   * Fraction watched, based on the saved resume position. Returns 0 for finished
   * items so cards can hide the progress bar once the watched flag is set.
   */
  val progressPercentage: Float
    get() {
      if (isWatched) return 0f
      val durationMs = video.duration.takeIf { it > 0 } ?: run {
        val totalSeconds = (playbackState?.lastPosition ?: 0) + (playbackState?.timeRemaining ?: 0)
        totalSeconds.toLong() * 1000L
      }
      if (durationMs <= 0) return 0f
      return (resumePositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }
}
