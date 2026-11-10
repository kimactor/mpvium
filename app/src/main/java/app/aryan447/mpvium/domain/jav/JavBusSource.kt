package app.aryan447.mpvium.domain.jav

import app.aryan447.mpvium.preferences.JavPreferences
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request

class JavBusSource(
  private val preferences: JavPreferences,
  private val httpClientFactory: JavHttpClientFactory,
) {
  companion object {
    private const val BASE_URL = "https://www.javbus.com"
    private const val DEFAULT_USER_AGENT =
      "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    private const val RATE_LIMIT_MS = 2_000L
  }

  private val requestLock = Mutex()
  private var lastRequestAt = 0L

  suspend fun fetch(code: String): JavMetadata = withContext(Dispatchers.IO) {
    awaitRateLimit()
    val url = "$BASE_URL/${URLEncoder.encode(code, "UTF-8").replace("+", "%20")}"
    val builder = Request.Builder()
      .url(url)
      .get()
      .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
      .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
      .header("Referer", "$BASE_URL/")
      .header("User-Agent", userAgent())

    val cookie = preferences.javBusCookie.get().trim().replace("\r", "").replace("\n", "")
    if (cookie.isNotBlank()) builder.header("Cookie", cookie)

    httpClientFactory.build().newCall(builder.build()).execute().use { response ->
      when {
        response.code == 404 || response.code == 410 -> throw JavSourceException(
          JavSourceFailureKind.NOT_FOUND,
          "JavBus does not have $code",
        )
        response.code == 403 -> throw JavSourceException(
          JavSourceFailureKind.BLOCKED,
          "JavBus returned HTTP 403; check the cookie or proxy",
        )
        !response.isSuccessful -> throw JavSourceException(
          JavSourceFailureKind.NETWORK,
          "JavBus returned HTTP ${response.code}",
        )
      }
      JavBusParser.parse(code, response.body.string(), url, cookie.isBlank())
    }
  }

  fun coverHeaders(): Map<String, String> = buildMap {
    put("Referer", "$BASE_URL/")
    put("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
    put("User-Agent", userAgent())
    preferences.javBusCookie.get().trim().replace("\r", "").replace("\n", "").takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
  }

  private suspend fun awaitRateLimit() = requestLock.withLock {
    val now = System.currentTimeMillis()
    val elapsed = if (lastRequestAt == 0L) RATE_LIMIT_MS else now - lastRequestAt
    val waitMs = RATE_LIMIT_MS - elapsed
    if (waitMs > 0) delay(waitMs)
    lastRequestAt = System.currentTimeMillis()
  }

  private fun userAgent(): String =
    preferences.customUserAgent.get().trim().ifBlank { DEFAULT_USER_AGENT }
}

internal object JavBusParser {
  private val field = Regex(
    """<p><span\s+class="header">([^<:：]+)[:：]</span>(.*?)</p>""",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
  )
  private val title = Regex("""<h3[^>]*>(.*?)</h3>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
  private val coverTag = Regex("""<a\b[^>]*\bbigImage\b[^>]*>""", RegexOption.IGNORE_CASE)
  private val href = Regex("""\bhref="([^"]+)"""", RegexOption.IGNORE_CASE)
  private val actress = Regex(
    """<div[^>]*\bstar-name\b[^>]*>.*?<a[^>]*>(.*?)</a>""",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
  )

  fun parse(code: String, html: String, sourceUrl: String, cookieMissing: Boolean): JavMetadata {
    val fields = field.findAll(html).associate { match ->
      clean(match.groupValues[1]) to clean(match.groupValues[2])
    }
    val shown = fields["識別碼"].orEmpty().replace(" ", "")
    if (shown.isBlank()) {
      throw JavSourceException(
        JavSourceFailureKind.AUTH_REQUIRED,
        if (cookieMissing) {
          "JavBus returned an age confirmation page; add a JavBus cookie in JAV settings"
        } else {
          "JavBus returned an age confirmation page; the saved cookie may have expired"
        },
      )
    }
    if (!JavCodeParser.sameReleaseCode(code, shown)) {
      throw JavSourceException(JavSourceFailureKind.NOT_FOUND, "JavBus returned the wrong code: $shown")
    }

    val rawTitle = title.find(html)?.groupValues?.get(1)?.let(::clean).orEmpty()
    val displayTitle = rawTitle
      .takeIf { it.startsWith(shown, ignoreCase = true) }
      ?.substring(shown.length)
      ?.trim()
      ?.trimStart('-', ':', '：', ' ')
      ?.takeIf { it.isNotBlank() }
      ?: rawTitle.takeIf { it.isNotBlank() }
      ?: shown

    val cover = coverTag.find(html)?.value
      ?.let { tag -> href.find(tag)?.groupValues?.get(1) }
      ?.let(::resolveUrl)

    return JavMetadata(
      code = shown,
      title = displayTitle,
      releaseDate = fields["發行日期"]?.takeIf { it.isNotBlank() },
      runtimeMinutes = fields["長度"]?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() },
      studio = fields["製作商"]?.takeIf { it.isNotBlank() },
      label = fields["發行商"]?.takeIf { it.isNotBlank() },
      series = fields["系列"]?.takeIf { it.isNotBlank() },
      director = fields["導演"]?.takeIf { it.isNotBlank() },
      actresses = actress.findAll(html)
        .map { clean(it.groupValues[1]) }
        .filter { it.isNotBlank() }
        .distinct()
        .toList(),
      coverUrl = cover,
      source = "JavBus",
      sourceUrl = sourceUrl,
    )
  }

  private fun resolveUrl(value: String): String? = runCatching {
    URI("https://www.javbus.com/").resolve(value).toString()
  }.getOrNull()

  private fun clean(value: String): String {
    val withoutTags = value.replace(Regex("""<[^>]*>"""), " ")
    return decodeEntities(withoutTags).replace(Regex("""\s+"""), " ").trim()
  }

  private fun decodeEntities(value: String): String {
    var result = value
      .replace("&nbsp;", " ")
      .replace("&amp;", "&")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .replace("&apos;", "'")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
    result = Regex("""&#(\d+);""").replace(result) { match ->
      match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
    }
    result = Regex("""&#x([0-9a-fA-F]+);""").replace(result) { match ->
      match.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: match.value
    }
    return result
  }
}
