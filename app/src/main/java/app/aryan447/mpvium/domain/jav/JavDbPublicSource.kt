package app.aryan447.mpvium.domain.jav

import app.aryan447.mpvium.preferences.JavPreferences
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class JavDbPublicSource(
  private val preferences: JavPreferences,
  private val httpClientFactory: JavHttpClientFactory,
  private val json: Json,
) {
  companion object {
    private val API_HOSTS = listOf(
      "https://jdforrepam.com",
      "https://apidd.czssdgz.com",
      "https://apidd.spthgb.com",
    )
    private const val API_INTERVAL_MS = 500L
    private const val BLOCK_COOLDOWN_MS = 15L * 60 * 1000
    private const val FAILURE_COOLDOWN_MS = 60L * 1000
    private const val APP_MIDDLE = "lpw6vgqzsp"
    private const val APP_SUFFIX =
      "71cf27bb3c0bcdf207b64abecddc970098c7421ee7203b9cdae54478478a199e" +
        "7d5a6e1a57691123c1a931c057842fb73ba3b3c83bcd69c17ccf174081e3d8aa"
    private const val APP_USER_AGENT = "Dart/3.5 (dart:io)"
    private val APP_QUERY = mapOf(
      "platform" to "android",
      "app_channel" to "official",
      "app_version" to "official",
      "app_version_number" to "1.9.35",
      "system_version" to "13",
      "device_model" to "Pixel 6",
      "device_name" to "Pixel",
      "device_uuid" to "1d26b2df-f042-5138-90b3-28980fe1d98a",
    )
  }

  private val lock = Mutex()
  private val lastRequestAt = mutableMapOf<String, Long>()
  private val cooldownUntil = mutableMapOf<String, Long>()

  suspend fun fetch(code: String): JavMetadata = withContext(Dispatchers.IO) {
    val movieId = search(code)
    val movie = detail(movieId)
    movieToMetadata(code, movie)
  }

  fun coverHeaders(): Map<String, String> = buildMap {
    put("Referer", "https://javdb.com/")
    put("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
    put("User-Agent", preferences.customUserAgent.get().trim().ifBlank { APP_USER_AGENT })
  }

  private suspend fun search(code: String): String {
    val data = request(
      path = "/api/v2/search",
      query = mapOf("q" to searchQuery(code), "type" to "movie", "page" to "1"),
    )
    val rows = data["movies"]?.jsonArray ?: throw parseError("JavDB search response did not contain movies")
    val ids = rows.mapNotNull { row ->
      val movie = row as? JsonObject ?: return@mapNotNull null
      val number = movie.string("number")
      if (!JavCodeParser.sameReleaseCode(code, number)) return@mapNotNull null
      movie.string("id").takeIf { it.isNotBlank() }
    }.distinct()

    if (ids.isEmpty()) throw JavSourceException(JavSourceFailureKind.NOT_FOUND, "JavDB does not have $code")
    if (ids.size > 1) {
      throw JavSourceException(
        JavSourceFailureKind.PARSE,
        "JavDB has multiple identities for $code; choose one manually",
      )
    }
    return ids.first()
  }

  private suspend fun detail(movieId: String): JsonObject {
    val data = request(path = "/api/v4/movies/$movieId", query = null)
    return data["movie"]?.jsonObject
      ?: throw parseError("JavDB detail response did not contain a movie")
  }

  private suspend fun request(path: String, query: Map<String, String>?): JsonObject {
    val body = signedGet(path, query)
    val payload = runCatching { json.parseToJsonElement(body).jsonObject }
      .getOrElse { throw parseError("JavDB returned non-JSON data") }
    if (payload["success"]?.jsonPrimitive?.intOrNull != 1) {
      throw parseError("JavDB request was not successful")
    }
    return payload["data"]?.jsonObject ?: throw parseError("JavDB response had no data")
  }

  private suspend fun signedGet(path: String, query: Map<String, String>?): String {
    var lastError: JavSourceException? = null
    val now = System.currentTimeMillis()
    val hosts = API_HOSTS.filter { (cooldownUntil[it] ?: 0L) <= now }
    if (hosts.isEmpty()) {
      throw JavSourceException(JavSourceFailureKind.BLOCKED, "All JavDB API hosts are cooling down")
    }

    for (host in hosts) {
      try {
        awaitHostSlot(host)
        val url = (host + path).toHttpUrl().newBuilder().apply {
          query?.forEach { (name, value) -> addQueryParameter(name, value) }
          APP_QUERY.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        val request = Request.Builder()
          .url(url)
          .get()
          .header("Accept", "application/json")
          .header("Accept-Language", "zh")
          .header("User-Agent", APP_USER_AGENT)
          .header("jdsignature", signature())
          .build()

        httpClientFactory.build().newCall(request).execute().use { response ->
          when {
            response.code == 200 -> return response.body.string()
            response.code == 404 || response.code == 410 -> throw JavSourceException(
              JavSourceFailureKind.NOT_FOUND,
              "JavDB does not have this movie",
            )
            response.code == 403 || response.code == 429 -> {
              cooldownUntil[host] = System.currentTimeMillis() + BLOCK_COOLDOWN_MS
              lastError = JavSourceException(
                JavSourceFailureKind.BLOCKED,
                "JavDB API host returned HTTP ${response.code}",
              )
            }
            else -> {
              cooldownUntil[host] = System.currentTimeMillis() + FAILURE_COOLDOWN_MS
              lastError = JavSourceException(
                JavSourceFailureKind.NETWORK,
                "JavDB API host returned HTTP ${response.code}",
              )
            }
          }
        }
      } catch (error: JavSourceException) {
        if (error.kind == JavSourceFailureKind.NOT_FOUND) throw error
        lastError = error
      } catch (error: Exception) {
        cooldownUntil[host] = System.currentTimeMillis() + FAILURE_COOLDOWN_MS
        lastError = JavSourceException(
          JavSourceFailureKind.NETWORK,
          error.message ?: "JavDB request failed",
        )
      }
    }

    throw lastError ?: JavSourceException(JavSourceFailureKind.BLOCKED, "JavDB API is unavailable")
  }

  private suspend fun awaitHostSlot(host: String) = lock.withLock {
    val now = System.currentTimeMillis()
    val elapsed = now - (lastRequestAt[host] ?: (now - API_INTERVAL_MS))
    val waitMs = API_INTERVAL_MS - elapsed
    if (waitMs > 0) delay(waitMs)
    lastRequestAt[host] = System.currentTimeMillis()
  }

  private fun signature(): String {
    val stamp = (System.currentTimeMillis() / 1000).toString()
    val digest = MessageDigest.getInstance("MD5")
      .digest((stamp + APP_SUFFIX).toByteArray())
      .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return "$stamp.$APP_MIDDLE.$digest"
  }

  private fun movieToMetadata(code: String, movie: JsonObject): JavMetadata {
    val id = movie.string("id")
    val number = movie.string("number")
    if (id.isBlank() || !JavCodeParser.sameReleaseCode(code, number)) {
      throw parseError("JavDB returned the wrong movie identity")
    }
    val origin = movie.string("origin_title")
    val display = movie.string("title")
    val type = movie["type"]?.jsonPrimitive?.intOrNull
    val maker = movie.string("maker_name")

    return JavMetadata(
      code = number,
      title = display.ifBlank { origin }.ifBlank { number },
      originalTitle = origin.takeIf { it.isNotBlank() },
      releaseDate = movie.string("release_date").takeIf { it.isNotBlank() },
      runtimeMinutes = movie["duration"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 },
      studio = if (type == 3) null else maker.takeIf { it.isNotBlank() },
      label = movie.string("publisher_name").takeIf { it.isNotBlank() }
        ?: maker.takeIf { type == 3 && it.isNotBlank() },
      series = movie.string("series_name").takeIf { it.isNotBlank() },
      director = movie.string("director_name").takeIf { it.isNotBlank() },
      genres = tags(movie),
      actresses = actresses(movie),
      coverUrl = publicImageUrl(movie.string("cover_url"))
        ?: publicImageUrl(movie.string("thumb_url")),
      source = "JavDB",
      sourceUrl = "https://javdb.com/v/$id",
      sourceId = id,
    )
  }

  private fun actresses(movie: JsonObject): List<String> {
    val actors = movie["actors"] as? JsonArray ?: return emptyList()
    return actors.mapNotNull { element ->
      val actor = element as? JsonObject ?: return@mapNotNull null
      val gender = actor["gender"]
      val female = when {
        gender is JsonPrimitive && gender.intOrNull == 0 -> true
        gender is JsonPrimitive && gender.contentOrNull == "female" -> true
        else -> false
      }
      if (!female) return@mapNotNull null
      actor.string("name").takeIf { it.isNotBlank() }
    }.distinct()
  }

  private fun tags(movie: JsonObject): List<String> {
    val array = movie["tags"] as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
      when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonObject -> element.string("name").takeIf { it.isNotBlank() }
        else -> null
      }
    }.distinct()
  }

  private fun publicImageUrl(value: String): String? {
    if (value.isBlank()) return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    val allowed = host == "tp.spfcas.com" ||
      host.endsWith(".spfcas.com") ||
      host.endsWith(".jdbstatic.com") ||
      host == "javdb.com" ||
      host.endsWith(".javdb.com")
    return if (uri.scheme == "https" && uri.userInfo == null && allowed) value else null
  }

  private fun JsonObject.string(name: String): String =
    this[name]?.let { element ->
      (element as? JsonPrimitive)?.contentOrNull
    }.orEmpty().trim()

  private fun parseError(message: String) =
    JavSourceException(JavSourceFailureKind.PARSE, message)

  private fun searchQuery(code: String): String =
    if (code.startsWith("FC2-PPV-")) code.removePrefix("FC2-PPV-") else code
}
