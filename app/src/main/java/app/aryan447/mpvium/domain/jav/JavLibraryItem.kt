package app.aryan447.mpvium.domain.jav

import app.aryan447.mpvium.database.entities.JavFileEntity
import app.aryan447.mpvium.database.entities.JavItemEntity
import app.aryan447.mpvium.domain.media.model.Video

data class JavLibraryItem(
  val file: JavFileEntity,
  val metadata: JavItemEntity?,
  val video: Video,
  val actresses: List<String> = emptyList(),
  val genres: List<String> = emptyList(),
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
}
