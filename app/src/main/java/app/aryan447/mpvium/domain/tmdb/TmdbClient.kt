package app.aryan447.mpvium.domain.tmdb

import app.aryan447.mpvium.preferences.TmdbPreferences
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class TmdbCollectionInfo(
  val id: Int,
  val name: String,
  val posterPath: String?,
  val backdropPath: String?,
)

data class TmdbMovieDetails(
  val id: Int,
  val title: String,
  val overview: String?,
  val posterPath: String?,
  val backdropPath: String?,
  val releaseDate: String?,
  val runtimeMinutes: Int?,
  val rating: Float?,
  val genres: List<String>,
  val studios: List<String>,
  val cast: List<String>,
  val collection: TmdbCollectionInfo?,
)

data class TmdbCollectionPart(
  val id: Int,
  val title: String,
  val releaseDate: String?,
  val posterPath: String?,
  val overview: String?,
)

data class TmdbCollection(
  val id: Int,
  val name: String,
  val posterPath: String?,
  val backdropPath: String?,
  val parts: List<TmdbCollectionPart>,
)

class TmdbClient(
  private val client: OkHttpClient,
  private val json: Json,
  private val preferences: TmdbPreferences,
) {
  private val apiKey: String
    get() = preferences.apiKey.get().trim()

  fun imageUrl(path: String?, size: String = "w500"): String? {
    val value = path?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    val clean = if (value.startsWith("/")) value else "/$value"
    return "https://image.tmdb.org/t/p/$size$clean"
  }

  suspend fun movieDetails(id: Int): TmdbMovieDetails? = withContext(Dispatchers.IO) {
    val key = apiKey
    if (key.isBlank()) return@withContext null
    val url = "$BASE/movie/$id?api_key=${encode(key)}&language=zh-CN&append_to_response=credits"
    val data = getJson(url) ?: return@withContext null
    TmdbMovieDetails(
      id = data.int("id") ?: id,
      title = data.string("title").ifBlank { data.string("original_title") },
      overview = data.string("overview").takeIf { it.isNotBlank() },
      posterPath = data.string("poster_path").takeIf { it.isNotBlank() },
      backdropPath = data.string("backdrop_path").takeIf { it.isNotBlank() },
      releaseDate = data.string("release_date").takeIf { it.isNotBlank() },
      runtimeMinutes = data.int("runtime")?.takeIf { it > 0 },
      rating = data.float("vote_average")?.takeIf { it > 0f },
      genres = data.objectArray("genres").mapNotNull { it.string("name").takeIf { name -> name.isNotBlank() } },
      studios = data.objectArray("production_companies")
        .mapNotNull { it.string("name").takeIf { name -> name.isNotBlank() } },
      cast = ((data["credits"] as? JsonObject)?.get("cast") as? JsonArray)
        ?.mapNotNull { (it as? JsonObject)?.string("name")?.takeIf { name -> name.isNotBlank() } }
        ?.distinct()
        ?.take(12)
        .orEmpty(),
      collection = (data["belongs_to_collection"] as? JsonObject)?.let { collection ->
        val collectionId = collection.int("id") ?: return@let null
        TmdbCollectionInfo(
          id = collectionId,
          name = collection.string("name"),
          posterPath = collection.string("poster_path").takeIf { it.isNotBlank() },
          backdropPath = collection.string("backdrop_path").takeIf { it.isNotBlank() },
        )
      },
    )
  }

  suspend fun collection(id: Int): TmdbCollection? = withContext(Dispatchers.IO) {
    val key = apiKey
    if (key.isBlank()) return@withContext null
    val url = "$BASE/collection/$id?api_key=${encode(key)}&language=zh-CN"
    val data = getJson(url) ?: return@withContext null
    TmdbCollection(
      id = data.int("id") ?: id,
      name = data.string("name"),
      posterPath = data.string("poster_path").takeIf { it.isNotBlank() },
      backdropPath = data.string("backdrop_path").takeIf { it.isNotBlank() },
      parts = data.objectArray("parts").map { part ->
        TmdbCollectionPart(
          id = part.int("id") ?: 0,
          title = part.string("title").ifBlank { part.string("original_title") },
          releaseDate = part.string("release_date").takeIf { it.isNotBlank() },
          posterPath = part.string("poster_path").takeIf { it.isNotBlank() },
          overview = part.string("overview").takeIf { it.isNotBlank() },
        )
      }.sortedBy { it.releaseDate.orEmpty() },
    )
  }

  private fun getJson(url: String): JsonObject? {
    return try {
      val request = Request.Builder().url(url).get().build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return null
        json.parseToJsonElement(response.body.string()).jsonObject
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

  private fun JsonObject.string(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

  private fun JsonObject.int(name: String): Int? =
    (this[name] as? JsonPrimitive)?.intOrNull

  private fun JsonObject.float(name: String): Float? =
    (this[name] as? JsonPrimitive)?.floatOrNull

  private fun JsonObject.objectArray(name: String): List<JsonObject> =
    (this[name] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

  companion object {
    private const val BASE = "https://api.themoviedb.org/3"
  }
}
