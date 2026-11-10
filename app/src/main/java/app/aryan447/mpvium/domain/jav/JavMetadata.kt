package app.aryan447.mpvium.domain.jav

data class JavMetadata(
  val code: String,
  val title: String,
  val originalTitle: String? = null,
  val overview: String? = null,
  val releaseDate: String? = null,
  val runtimeMinutes: Int? = null,
  val studio: String? = null,
  val label: String? = null,
  val series: String? = null,
  val director: String? = null,
  val genres: List<String> = emptyList(),
  val actresses: List<String> = emptyList(),
  val coverUrl: String? = null,
  val source: String,
  val sourceUrl: String? = null,
  val sourceId: String? = null,
)

enum class JavSourceFailureKind {
  NOT_FOUND,
  AUTH_REQUIRED,
  BLOCKED,
  NETWORK,
  PARSE,
}

class JavSourceException(
  val kind: JavSourceFailureKind,
  message: String,
) : Exception(message)
