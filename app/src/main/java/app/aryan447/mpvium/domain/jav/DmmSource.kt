package app.aryan447.mpvium.domain.jav

import app.aryan447.mpvium.preferences.JavPreferences
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * DMM / FANZA public GraphQL catalogue.
 *
 * This is a no-account source for standard censored codes: it needs no cookie, no age gate,
 * and no VPN. It is deliberately limited to [JavCodeKind.STANDARD]; FC2, amateur and dated
 * codes are not in this catalogue.
 */
class DmmSource(
  private val preferences: JavPreferences,
  private val httpClientFactory: JavHttpClientFactory,
  private val json: Json,
) {
  companion object {
    private const val ENDPOINT = "https://api.video.dmm.co.jp/graphql"
    private const val CONTENT_URL = "https://video.dmm.co.jp/av/content/?id="
    private const val RATE_LIMIT_MS = 2_000L
    private const val SEARCH_LIMIT = 30
    private const val DETAIL_ATTEMPTS = 2
    private const val DEFAULT_USER_AGENT =
      "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private const val DETAIL_QUERY =
      "query PeachContent(\$id: ID!) { ppvContent(id: \$id) { id title description makerContentId" +
        " makerReleasedAt duration maker { id name } label { id name } series { id name }" +
        " actresses { id name } directors { id name } genres { id name }" +
        " packageImage { largeUrl mediumUrl } } }"

    private const val SEARCH_QUERY =
      "query PeachSearch(\$limit: Int!, \$sort: ContentSearchPPVSort!, \$queryWord: String)" +
        " { legacySearchPPV(limit: \$limit, sort: \$sort, queryWord: \$queryWord)" +
        " { result { contents { id } } } }"
  }

  private val requestLock = Mutex()
  private var lastRequestAt = 0L

  suspend fun fetch(code: String): JavMetadata = withContext(Dispatchers.IO) {
    val parsed = JavCodeParser.parse(code)
      ?: throw JavSourceException(JavSourceFailureKind.NOT_FOUND, "DMM cannot read $code")
    if (parsed.kind != JavCodeKind.STANDARD) {
      throw JavSourceException(JavSourceFailureKind.NOT_FOUND, "DMM does not index ${parsed.code}")
    }
    val normalized = parsed.code
    val item = findDetail(normalized)
      ?: throw JavSourceException(JavSourceFailureKind.NOT_FOUND, "DMM does not have $normalized")
    toMetadata(normalized, item)
  }

  fun coverHeaders(): Map<String, String> = mapOf(
    "Accept" to "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
    "User-Agent" to userAgent(),
  )

  private suspend fun findDetail(code: String): JsonObject? {
    val guessed = DmmCodes.guessedCid(code)
    if (guessed != null) {
      val item = requestDetail(guessed)
      if (item != null && DmmCodes.matches(code, item)) return item
    }

    var attempts = 0
    for (cid in DmmCodes.searchCandidates(code, searchCids(code))) {
      if (cid == guessed) continue
      if (attempts >= DETAIL_ATTEMPTS) break
      attempts++
      val item = requestDetail(cid) ?: continue
      if (DmmCodes.matches(code, item)) return item
    }
    return null
  }

  private suspend fun searchCids(code: String): List<String> {
    val parts = DmmCodes.codeParts(code) ?: return emptyList()
    val data = graphql(
      SEARCH_QUERY,
      buildJsonObject {
        put("limit", SEARCH_LIMIT)
        put("sort", "RELEASE_DATE")
        put("queryWord", "${parts.letters} ${parts.digits}${parts.suffix}")
      },
    )
    val contents = ((data["legacySearchPPV"] as? JsonObject)?.get("result") as? JsonObject)
      ?.get("contents") as? JsonArray
      ?: return emptyList()
    return contents.mapNotNull { row ->
      (row as? JsonObject)?.string("id")?.takeIf { it.isNotBlank() }
    }
  }

  private suspend fun requestDetail(cid: String): JsonObject? {
    val data = graphql(DETAIL_QUERY, buildJsonObject { put("id", cid) })
    return data["ppvContent"] as? JsonObject
  }

  private suspend fun graphql(query: String, variables: JsonObject): JsonObject {
    awaitRateLimit()
    val body = buildJsonObject {
      put("query", query)
      put("variables", variables)
    }.toString()
    val request = Request.Builder()
      .url(ENDPOINT)
      .post(body.toRequestBody(JSON_MEDIA_TYPE))
      .header("Accept", "application/json")
      .header("User-Agent", userAgent())
      .build()

    try {
      httpClientFactory.build().newCall(request).execute().use { response ->
        when {
          response.code == 404 -> throw JavSourceException(
            JavSourceFailureKind.NOT_FOUND,
            "DMM does not have this movie",
          )
          response.code == 403 || response.code == 429 -> throw JavSourceException(
            JavSourceFailureKind.BLOCKED,
            "DMM returned HTTP ${response.code}",
          )
          !response.isSuccessful -> throw JavSourceException(
            JavSourceFailureKind.NETWORK,
            "DMM returned HTTP ${response.code}",
          )
        }

        val payload = try {
          json.parseToJsonElement(response.body.string()) as? JsonObject
        } catch (error: Exception) {
          null
        } ?: throw JavSourceException(JavSourceFailureKind.PARSE, "DMM returned non-JSON data")

        val data = payload["data"] as? JsonObject
        if (data == null) {
          val firstError = (payload["errors"] as? JsonArray)?.firstOrNull() as? JsonObject
          throw JavSourceException(
            JavSourceFailureKind.PARSE,
            firstError?.string("message")?.takeIf { it.isNotBlank() } ?: "DMM refused the query",
          )
        }
        return data
      }
    } catch (error: JavSourceException) {
      throw error
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      throw JavSourceException(JavSourceFailureKind.NETWORK, error.message ?: "DMM request failed")
    }
  }

  private fun toMetadata(code: String, item: JsonObject): JavMetadata {
    val cid = item.string("id")
    val title = item.string("title")
    if (cid.isBlank() || title.isBlank()) {
      throw JavSourceException(JavSourceFailureKind.PARSE, "DMM returned incomplete metadata")
    }
    val durationSeconds = (item["duration"] as? JsonPrimitive)?.intOrNull ?: 0

    return JavMetadata(
      code = code,
      title = title,
      overview = item.string("description").ifBlank { null },
      releaseDate = DmmCodes.releaseDate(item.string("makerReleasedAt")),
      runtimeMinutes = durationSeconds.takeIf { it > 0 }?.let { (it + 30) / 60 },
      studio = item.objectName("maker"),
      label = item.objectName("label"),
      series = item.objectName("series"),
      director = (item["directors"] as? JsonArray)
        ?.firstOrNull()
        ?.let { it as? JsonObject }
        ?.string("name")
        ?.ifBlank { null },
      genres = item.names("genres"),
      actresses = item.names("actresses"),
      coverUrl = item.coverUrl(),
      source = "DMM",
      sourceUrl = CONTENT_URL + cid,
      sourceId = cid,
    )
  }

  private fun userAgent(): String =
    preferences.customUserAgent.get().trim().ifBlank { DEFAULT_USER_AGENT }

  private suspend fun awaitRateLimit() = requestLock.withLock {
    val now = System.currentTimeMillis()
    val elapsed = if (lastRequestAt == 0L) RATE_LIMIT_MS else now - lastRequestAt
    val waitMs = RATE_LIMIT_MS - elapsed
    if (waitMs > 0) delay(waitMs)
    lastRequestAt = System.currentTimeMillis()
  }

  private fun JsonObject.string(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

  private fun JsonObject.objectName(name: String): String? =
    (this[name] as? JsonObject)?.string("name")?.ifBlank { null }

  private fun JsonObject.names(name: String): List<String> =
    (this[name] as? JsonArray).orEmpty()
      .mapNotNull { (it as? JsonObject)?.string("name")?.takeIf { value -> value.isNotBlank() } }
      .distinct()

  private fun JsonObject.coverUrl(): String? {
    val image = this["packageImage"] as? JsonObject ?: return null
    return image.string("largeUrl").ifBlank { image.string("mediumUrl") }.ifBlank { null }
  }
}

internal object DmmCodes {
  private val CODE = Regex("""^(\d{3})?([A-Z]{2,8})-?(\d{1,5})([A-Z]{0,2})$""")
  private val CID = Regex("""^(?:h_\d+|\d+)?([a-z]+)(\d+)([a-z]*)$""")

  data class Parts(
    val prefix: String,
    val letters: String,
    val digits: String,
    val suffix: String,
  )

  fun codeParts(code: String): Parts? {
    val match = CODE.matchEntire(code.trim().uppercase()) ?: return null
    return Parts(
      prefix = match.groupValues[1],
      letters = match.groupValues[2],
      digits = match.groupValues[3],
      suffix = match.groupValues[4],
    )
  }

  fun guessedCid(code: String): String? {
    val parts = codeParts(code) ?: return null
    val number = parts.digits.toIntOrNull() ?: return null
    return buildString {
      append(parts.prefix)
      append(parts.letters.lowercase())
      append(number.toString().padStart(5, '0'))
      append(parts.suffix.lowercase())
    }
  }

  fun searchCandidates(code: String, cids: List<String>): List<String> {
    val parts = codeParts(code) ?: return emptyList()
    val number = parts.digits.toIntOrNull() ?: return emptyList()
    return cids.mapNotNull { raw ->
      val text = raw.lowercase()
      val match = CID.matchEntire(text) ?: return@mapNotNull null
      if (match.groupValues[1] != parts.letters.lowercase()) return@mapNotNull null
      if (match.groupValues[2].toIntOrNull() != number) return@mapNotNull null
      val suffix = match.groupValues[3]
      if (parts.suffix.isNotEmpty() && suffix != parts.suffix.lowercase()) return@mapNotNull null
      if (parts.prefix.isNotEmpty() && !text.startsWith(parts.prefix)) return@mapNotNull null
      raw to suffix.isNotEmpty()
    }.sortedBy { it.second }.map { it.first }.distinct()
  }

  fun matches(code: String, item: JsonObject): Boolean {
    val makerCode = (item["makerContentId"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    if (makerCode.isNotBlank() && JavCodeParser.sameReleaseCode(code, makerCode)) return true

    val parts = codeParts(code) ?: return false
    val id = (item["id"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    val match = CID.matchEntire(id.lowercase()) ?: return false
    if (match.groupValues[1] != parts.letters.lowercase()) return false
    if (match.groupValues[2].toIntOrNull() != parts.digits.toIntOrNull()) return false
    return parts.suffix.isEmpty() || match.groupValues[3] == parts.suffix.lowercase()
  }

  fun releaseDate(value: String): String? {
    val text = value.trim()
    if (text.isBlank()) return null
    return runCatching {
      Instant.parse(text).atZone(ZoneId.of("Asia/Tokyo")).toLocalDate().toString()
    }.getOrElse { text.take(10).takeIf { it.length == 10 } }
  }
}
