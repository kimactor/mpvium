package app.aryan447.mpvium.domain.jav

import app.aryan447.mpvium.preferences.JavPreferences
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
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
    private const val NETWORK_COOLDOWN_MS = 5L * 60 * 1000
    private const val BLOCKED_COOLDOWN_MS = 15L * 60 * 1000
  }

  private val requestLock = Mutex()
  private var lastRequestAt = 0L
  @Volatile private var cooldownUntil = 0L
  @Volatile private var cooldownKind = JavSourceFailureKind.NETWORK
  @Volatile private var cooldownMessage = "JavBus is temporarily unavailable"

  suspend fun fetch(code: String): JavMetadata = withContext(Dispatchers.IO) {
    if (System.currentTimeMillis() < cooldownUntil) {
      throw JavSourceException(cooldownKind, cooldownMessage)
    }
    awaitRateLimit()
    val url = "$BASE_URL/${URLEncoder.encode(code, "UTF-8").replace("+", "%20")}"
    val builder = Request.Builder()
      .url(url)
      .get()
      .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
      .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
      .header("Referer", "$BASE_URL/")
      .header("User-Agent", userAgent())

    val userCookie = JavBusCookies.clean(preferences.javBusCookie.get())
    val cookie = JavBusCookies.merge(
      ageConfirmed = preferences.ageConfirmed.get(),
      raw = userCookie,
    )
    if (cookie != null) builder.header("Cookie", cookie)

    try {
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
        JavBusParser.parse(code, response.body.string(), url, userCookie.isBlank())
      }
    } catch (error: JavSourceException) {
      if (error.kind == JavSourceFailureKind.NETWORK || error.kind == JavSourceFailureKind.BLOCKED) {
        val cooldownMs = if (error.kind == JavSourceFailureKind.BLOCKED) {
          BLOCKED_COOLDOWN_MS
        } else {
          NETWORK_COOLDOWN_MS
        }
        cooldownKind = error.kind
        cooldownMessage = error.message ?: "JavBus is temporarily unavailable"
        cooldownUntil = System.currentTimeMillis() + cooldownMs
      }
      throw error
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      cooldownKind = JavSourceFailureKind.NETWORK
      cooldownMessage = error.message ?: "JavBus request failed"
      cooldownUntil = System.currentTimeMillis() + NETWORK_COOLDOWN_MS
      throw JavSourceException(cooldownKind, cooldownMessage)
    }
  }

  fun coverHeaders(): Map<String, String> = buildMap {
    put("Referer", "$BASE_URL/")
    put("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
    put("User-Agent", userAgent())
    JavBusCookies.merge(
      ageConfirmed = preferences.ageConfirmed.get(),
      raw = JavBusCookies.clean(preferences.javBusCookie.get()),
    )?.let { put("Cookie", it) }
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
          "JavBus returned an age verification page; confirm 18+ in the JAV library or add a JavBus cookie in JAV settings"
        } else {
          "JavBus returned an age verification page; the saved cookie may have expired"
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

internal object JavBusCookies {
  fun clean(raw: String): String {
    var value = raw.trim().replace("\r", "").replace("\n", "")
    if (value.startsWith("Cookie:", ignoreCase = true)) {
      value = value.substringAfter(':').trim()
    }
    return value
  }

  /**
   * JavBus gates release pages behind an age prompt. The browser-side prompt stores
   * `age=verified` and `dv=1`; send those after the in-app 18+ confirmation so a scrape does
   * not depend on a manually copied cookie. A user-supplied cookie still wins for any key.
   */
  fun merge(ageConfirmed: Boolean, raw: String): String? {
    val fields = linkedMapOf<String, String>()
    if (ageConfirmed) {
      fields["age"] = "verified"
      fields["dv"] = "1"
    }
    parse(raw).forEach { (name, value) -> fields[name] = value }
    if (fields.isEmpty()) return null
    return fields.entries.joinToString("; ") { "${it.key}=${it.value}" }
  }

  private fun parse(raw: String): Map<String, String> =
    raw.split(';').mapNotNull { part ->
      val index = part.indexOf('=')
      if (index <= 0) return@mapNotNull null
      val name = part.substring(0, index).trim()
      val value = part.substring(index + 1).trim()
      if (name.isBlank()) null else name to value
    }.toMap()
}
