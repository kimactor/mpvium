package app.aryan447.mpvium.domain.jav

import app.aryan447.mpvium.database.dao.JavDao
import app.aryan447.mpvium.database.entities.JavItemEntity
import app.aryan447.mpvium.domain.streaming.StreamingArtworkStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class JavScrapeResult(
  val item: JavItemEntity,
  val errorKind: JavSourceFailureKind? = null,
  val errorMessage: String? = null,
)

class JavMetadataRepository(
  private val javDao: JavDao,
  private val javBusSource: JavBusSource,
  private val artworkStore: StreamingArtworkStore,
  private val json: Json,
) {
  companion object {
    private const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
    private const val NOT_FOUND_RETRY_MS = 7L * 24 * 60 * 60 * 1000
    private const val AUTH_RETRY_MS = 6L * 60 * 60 * 1000
    private const val BLOCKED_RETRY_MS = 6L * 60 * 60 * 1000
    private const val NETWORK_RETRY_MS = 15L * 60 * 1000
    private const val PARSE_RETRY_MS = 60L * 60 * 1000
  }

  suspend fun getItem(code: String): JavItemEntity? = withContext(Dispatchers.IO) {
    javDao.getItem(code)
  }

  suspend fun scrape(code: String, force: Boolean = false): JavScrapeResult = withContext(Dispatchers.IO) {
    val existing = javDao.getItem(code)
    val now = System.currentTimeMillis()
    if (!force && existing != null && existing.isFresh(now)) {
      return@withContext JavScrapeResult(existing)
    }

    try {
      val metadata = javBusSource.fetch(code)
      val localCoverPath = metadata.coverUrl?.let { coverUrl ->
        artworkStore.downloadToLocal(
          url = coverUrl,
          headers = javBusSource.coverHeaders(),
          keyHint = code,
        )
      }
      val item = metadata.toEntity(
        existing = existing,
        localCoverPath = localCoverPath,
        now = now,
      )
      javDao.upsertItem(item)
      JavScrapeResult(item)
    } catch (error: JavSourceException) {
      val item = (existing ?: JavItemEntity(code = code)).copy(
        status = if (error.kind == JavSourceFailureKind.NOT_FOUND) {
          JavItemEntity.STATUS_NOT_FOUND
        } else {
          JavItemEntity.STATUS_FAILED
        },
        lastError = error.message,
        nextRetryAt = now + retryDelay(error.kind),
        updatedAt = now,
      )
      javDao.upsertItem(item)
      JavScrapeResult(item, error.kind, error.message)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      val item = (existing ?: JavItemEntity(code = code)).copy(
        status = JavItemEntity.STATUS_FAILED,
        lastError = error.message ?: "JavBus request failed",
        nextRetryAt = now + NETWORK_RETRY_MS,
        updatedAt = now,
      )
      javDao.upsertItem(item)
      JavScrapeResult(item, JavSourceFailureKind.NETWORK, item.lastError)
    }
  }

  private fun JavItemEntity.isFresh(now: Long): Boolean {
    val fetched = fetchedAt ?: return false
    val age = now - fetched
    return status == JavItemEntity.STATUS_OK &&
      !localCoverPath.isNullOrBlank() &&
      age >= 0 &&
      age < CACHE_TTL_MS
  }

  private fun JavMetadata.toEntity(
    existing: JavItemEntity?,
    localCoverPath: String?,
    now: Long,
  ): JavItemEntity {
    val resolvedTitle = title.trim().ifBlank { existing?.title.orEmpty().ifBlank { code } }
    val resolvedCover = existing?.manualCoverPath?.takeIf { it.isNotBlank() }
      ?: localCoverPath
      ?: existing?.localCoverPath

    return (existing ?: JavItemEntity(code = code)).copy(
      code = code,
      title = resolvedTitle,
      originalTitle = originalTitle ?: existing?.originalTitle,
      overview = overview ?: existing?.overview,
      releaseDate = releaseDate ?: existing?.releaseDate,
      runtimeMinutes = runtimeMinutes ?: existing?.runtimeMinutes,
      studio = studio ?: existing?.studio,
      label = label ?: existing?.label,
      series = series ?: existing?.series,
      director = director ?: existing?.director,
      genresJson = json.encodeToString(genres).takeIf { genres.isNotEmpty() } ?: existing?.genresJson,
      actressesJson = json.encodeToString(actresses).takeIf { actresses.isNotEmpty() } ?: existing?.actressesJson,
      coverUrl = coverUrl ?: existing?.coverUrl,
      localCoverPath = resolvedCover,
      source = source,
      sourceUrl = sourceUrl ?: existing?.sourceUrl,
      sourceId = sourceId ?: existing?.sourceId,
      status = if (resolvedTitle.isNotBlank() && !resolvedCover.isNullOrBlank()) {
        JavItemEntity.STATUS_OK
      } else {
        JavItemEntity.STATUS_PARTIAL
      },
      lastError = null,
      nextRetryAt = null,
      fetchedAt = now,
      updatedAt = now,
    )
  }

  private fun retryDelay(kind: JavSourceFailureKind): Long = when (kind) {
    JavSourceFailureKind.NOT_FOUND -> NOT_FOUND_RETRY_MS
    JavSourceFailureKind.AUTH_REQUIRED -> AUTH_RETRY_MS
    JavSourceFailureKind.BLOCKED -> BLOCKED_RETRY_MS
    JavSourceFailureKind.NETWORK -> NETWORK_RETRY_MS
    JavSourceFailureKind.PARSE -> PARSE_RETRY_MS
  }
}
