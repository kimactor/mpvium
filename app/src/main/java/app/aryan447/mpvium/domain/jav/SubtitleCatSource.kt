package app.aryan447.mpvium.domain.jav

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class SubtitleCatCandidate(
  val title: String,
  val detailUrl: String,
  val simplifiedUrl: String?,
  val traditionalUrl: String?,
) {
  val preferredUrl: String? get() = simplifiedUrl ?: traditionalUrl
}

/**
 * Keyless Chinese subtitle lookup. SubtitleCat creates zh-CN/zh-TW SRT files from user uploads and
 * serves them from the subtitle detail page; no account or API key is involved.
 */
class SubtitleCatSource(
  private val httpClientFactory: JavHttpClientFactory,
) {
  companion object {
    const val BASE_URL = "https://www.subtitlecat.com/"
    private const val MAX_RESULTS = 6
    private const val MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024
    private const val BROWSER_UA =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
  }

  suspend fun searchChinese(
    code: String,
    limit: Int = MAX_RESULTS,
  ): Result<List<SubtitleCatCandidate>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val client = httpClientFactory.build().newBuilder()
          .readTimeout(60, TimeUnit.SECONDS)
          .build()
        val searchUrl = BASE_URL + "index.php?search=" + URLEncoder.encode(code, "UTF-8")
        val searchHtml = fetch(client, searchUrl, referer = null)
        SubtitleCatParser
          .parseSearchResults(searchHtml)
          .take(limit)
          .mapNotNull { path ->
            val detailUrl = URI(BASE_URL).resolve(path).toString()
            val detailHtml = runCatching { fetch(client, detailUrl, referer = searchUrl) }.getOrNull()
              ?: return@mapNotNull null
            val links = SubtitleCatParser.parseChineseDownloads(detailHtml)
            if (links.simplified == null && links.traditional == null) {
              null
            } else {
              SubtitleCatCandidate(
                title = SubtitleCatParser.resultTitle(path),
                detailUrl = detailUrl,
                simplifiedUrl = links.simplified?.let { URI(BASE_URL).resolve(it).toString() },
                traditionalUrl = links.traditional?.let { URI(BASE_URL).resolve(it).toString() },
              )
            }
          }
      }
    }

  suspend fun download(
    candidate: SubtitleCatCandidate,
    traditional: Boolean = false,
  ): Result<ByteArray> =
    withContext(Dispatchers.IO) {
      runCatching {
        val url = if (traditional) candidate.traditionalUrl else candidate.preferredUrl
        requireNotNull(url) { "No Chinese subtitle for this result" }
        val client = httpClientFactory.build().newBuilder()
          .readTimeout(150, TimeUnit.SECONDS)
          .build()
        val bytes = fetchBytes(client, url, referer = candidate.detailUrl)
        require(bytes.size in 16..MAX_DOWNLOAD_BYTES) { "Unexpected subtitle size" }
        require(JavSubtitleEncoding.looksLikeSubtitle(bytes)) { "Downloaded file is not a subtitle" }
        bytes
      }
    }

  private fun fetch(
    client: OkHttpClient,
    url: String,
    referer: String?,
  ): String = fetchBytes(client, url, referer).toString(Charsets.UTF_8)

  private fun fetchBytes(
    client: OkHttpClient,
    url: String,
    referer: String?,
  ): ByteArray {
    val request = Request.Builder()
      .url(url)
      .header("User-Agent", BROWSER_UA)
      .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
      .apply { referer?.let { header("Referer", it) } }
      .build()
    client.newCall(request).execute().use { response ->
      check(response.isSuccessful) { "HTTP ${response.code}" }
      return response.body?.bytes() ?: error("Empty response")
    }
  }
}

object SubtitleCatParser {
  private val resultHref =
    Regex("""href=\x22(?:https?://www\.subtitlecat\.com/|/)?(subs/\d+/[^\x22]+\.html)\x22""")
  private val simplifiedHref =
    Regex("""id=\x22download_zh-CN\x22[^>]*?href=\x22([^\x22]+)\x22""")
  private val traditionalHref =
    Regex("""id=\x22download_zh-TW\x22[^>]*?href=\x22([^\x22]+)\x22""")

  fun parseSearchResults(html: String): List<String> =
    resultHref.findAll(html)
      .map { it.groupValues[1] }
      .distinct()
      .toList()

  fun parseChineseDownloads(html: String): ChineseDownloads =
    ChineseDownloads(
      simplified = simplifiedHref.find(html)?.groupValues?.get(1),
      traditional = traditionalHref.find(html)?.groupValues?.get(1),
    )

  fun resultTitle(path: String): String =
    runCatching {
      URLDecoder.decode(path.substringAfterLast('/').removeSuffix(".html"), "UTF-8")
        .replace('_', ' ')
        .trim()
    }.getOrDefault(path)
}

data class ChineseDownloads(
  val simplified: String?,
  val traditional: String?,
)
