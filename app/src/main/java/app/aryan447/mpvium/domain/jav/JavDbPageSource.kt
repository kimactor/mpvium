package app.aryan447.mpvium.domain.jav

import app.aryan447.mpvium.preferences.JavPreferences
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

internal const val JAVDB_BASE_URL = "https://javdb.com"

internal fun javDbSearchQuery(code: String): String =
  if (code.startsWith("FC2-PPV-")) code.removePrefix("FC2-PPV-") else code

/**
 * Reads JavDB's public pages when its anonymous API asks for a login.
 *
 * JavDB stopped answering anonymous search and detail requests in November 2026. The pages
 * still render without an account, so a scrape can spend one search page and one detail page
 * instead. The site limits search pages harder than detail pages (12 s versus 3 s), and a 403
 * is an egress-IP rate limit rather than a login wall.
 */
class JavDbPageSource(
  private val preferences: JavPreferences,
  private val httpClientFactory: JavHttpClientFactory,
) {
  companion object {
    private const val SEARCH_INTERVAL_MS = 12_000L
    private const val DETAIL_INTERVAL_MS = 3_000L
    private const val AUTH_COOLDOWN_MS = 60L * 60 * 1000
    private const val BLOCKED_COOLDOWN_MS = 15L * 60 * 1000
    private const val NETWORK_COOLDOWN_MS = 60L * 1000
    private const val DEFAULT_USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
  }

  private val requestLock = Mutex()
  private var lastSearchAt = 0L
  private var lastDetailAt = 0L

  @Volatile private var cooldownUntil = 0L
  @Volatile private var cooldownKind = JavSourceFailureKind.NETWORK
  @Volatile private var cooldownMessage = "JavDB web pages are temporarily unavailable"

  suspend fun fetch(code: String): JavMetadata = withContext(Dispatchers.IO) {
    if (System.currentTimeMillis() < cooldownUntil) {
      throw JavSourceException(cooldownKind, cooldownMessage)
    }

    try {
      awaitSlot(search = true)
      val searchHtml = get("/search", mapOf("q" to javDbSearchQuery(code), "f" to "all"))
      if (JavDbPageParser.isLoginPage(searchHtml)) throw loginRequired()

      val rows = JavDbPageParser.searchRows(searchHtml)
        .filter { JavCodeParser.sameReleaseCode(code, it.code) }
      if (rows.isEmpty()) {
        throw JavSourceException(JavSourceFailureKind.NOT_FOUND, "JavDB does not have $code")
      }
      val row = chooseIdentity(code, rows)

      awaitSlot(search = false)
      val detailHtml = get("/v/${row.id}", mapOf("locale" to "zh"))
      if (JavDbPageParser.isLoginPage(detailHtml)) throw loginRequired()

      val detail = JavDbPageParser.detail(detailHtml)
      if (!JavCodeParser.sameReleaseCode(code, detail.code)) {
        throw JavSourceException(
          JavSourceFailureKind.PARSE,
          "JavDB web page returned the wrong code: ${detail.code}",
        )
      }
      JavDbPageParser.toMetadata(detail, row.id)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: JavSourceException) {
      rememberCooldown(error)
      throw error
    } catch (error: Exception) {
      val failure = JavSourceException(
        JavSourceFailureKind.NETWORK,
        error.message ?: "JavDB web page request failed",
      )
      rememberCooldown(failure)
      throw failure
    }
  }

  fun coverHeaders(): Map<String, String> = buildMap {
    put("Referer", "$JAVDB_BASE_URL/")
    put("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
    put("User-Agent", userAgent())
  }

  private fun chooseIdentity(code: String, rows: List<JavDbPageRow>): JavDbPageRow {
    val exact = rows.filter { it.code.equals(code, ignoreCase = true) }
    val sameIdentity = exact.ifEmpty { rows }
    val regular = sameIdentity.filterNot { row ->
      row.title.contains("FANZA限定") || row.title.contains("FANZA 限定")
    }
    val candidates = regular.ifEmpty { sameIdentity }
    if (candidates.size == 1) return candidates.first()
    throw JavSourceException(
      JavSourceFailureKind.PARSE,
      "JavDB has multiple identities for $code; choose one manually",
    )
  }

  private fun get(path: String, query: Map<String, String> = emptyMap()): String {
    val url = (JAVDB_BASE_URL + path).toHttpUrl().newBuilder().apply {
      query.forEach { (name, value) -> addQueryParameter(name, value) }
    }.build()
    val request = Request.Builder()
      .url(url)
      .get()
      .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
      .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
      .header("Referer", "$JAVDB_BASE_URL/")
      .header("User-Agent", userAgent())
      .build()

    httpClientFactory.build().newCall(request).execute().use { response ->
      when {
        response.code == 404 || response.code == 410 -> throw JavSourceException(
          JavSourceFailureKind.NOT_FOUND,
          "JavDB does not have this page",
        )
        response.code == 403 || response.code == 429 -> throw JavSourceException(
          JavSourceFailureKind.BLOCKED,
          "JavDB web pages are rate-limiting this connection; switch the proxy exit and retry",
        )
        !response.isSuccessful -> throw JavSourceException(
          JavSourceFailureKind.NETWORK,
          "JavDB web page returned HTTP ${response.code}",
        )
      }
      val body = response.body.string()
      if (body.isBlank()) {
        throw JavSourceException(JavSourceFailureKind.PARSE, "JavDB web page was empty")
      }
      return body
    }
  }

  private suspend fun awaitSlot(search: Boolean) = requestLock.withLock {
    val now = System.currentTimeMillis()
    val last = if (search) lastSearchAt else lastDetailAt
    val interval = if (search) SEARCH_INTERVAL_MS else DETAIL_INTERVAL_MS
    if (last > 0) {
      val waitMs = interval - (now - last)
      if (waitMs > 0) delay(waitMs)
    }
    val stamp = System.currentTimeMillis()
    if (search) lastSearchAt = stamp else lastDetailAt = stamp
  }

  private fun rememberCooldown(error: JavSourceException) {
    val waitMs = when (error.kind) {
      JavSourceFailureKind.AUTH_REQUIRED -> AUTH_COOLDOWN_MS
      JavSourceFailureKind.BLOCKED -> BLOCKED_COOLDOWN_MS
      JavSourceFailureKind.NETWORK -> NETWORK_COOLDOWN_MS
      else -> 0L
    }
    if (waitMs <= 0) return
    cooldownUntil = System.currentTimeMillis() + waitMs
    cooldownKind = error.kind
    cooldownMessage = error.message ?: "JavDB web pages are temporarily unavailable"
  }

  private fun loginRequired() = JavSourceException(
    JavSourceFailureKind.AUTH_REQUIRED,
    "JavDB web pages now require a sign-in; retry later or use another source",
  )

  private fun userAgent(): String =
    preferences.customUserAgent.get().trim().ifBlank { DEFAULT_USER_AGENT }
}

internal data class JavDbPageRow(
  val id: String,
  val code: String,
  val title: String,
)

internal data class JavDbPageDetail(
  val code: String,
  val displayTitle: String,
  val originalTitle: String,
  val releaseDate: String?,
  val runtimeMinutes: Int?,
  val studio: String?,
  val label: String?,
  val series: String?,
  val director: String?,
  val genres: List<String>,
  val actresses: List<String>,
  val coverUrl: String?,
)

/**
 * Parser for the two JavDB pages the fallback reads. Kept free of Android and HTTP types so
 * the field projection can be tested from JVM fixtures.
 */
internal object JavDbPageParser {
  private const val FIELD_CODE = "番號"
  private const val FIELD_CODE_SIMPLIFIED = "番号"
  private const val FIELD_DATE = "日期"
  private const val FIELD_RUNTIME = "時長"
  private const val FIELD_RUNTIME_SIMPLIFIED = "时长"
  private const val FIELD_ACTORS = "演員"
  private const val FIELD_ACTORS_SIMPLIFIED = "演员"
  private const val FIELD_STUDIO = "片商"
  private const val FIELD_LABEL = "發行"
  private const val FIELD_LABEL_SIMPLIFIED = "发行"
  private const val FIELD_SERIES = "系列"
  private const val FIELD_DIRECTOR = "導演"
  private const val FIELD_DIRECTOR_SIMPLIFIED = "导演"
  private const val FIELD_GENRES = "類別"
  private const val FIELD_GENRES_SIMPLIFIED = "类别"

  private val PANEL = Regex(
    """<div class="panel-block[^"]*">\s*<strong>([^<:：]+)[:：]</strong>\s*(?:&nbsp;)?\s*<span class="value">(.*?)</span>""",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
  )
  private val SEARCH_ROW = Regex(
    """<a\b([^>]*\bclass="[^"]*\bbox\b[^"]*"[^>]*)>(.*?)<strong[^>]*>(.*?)</strong>""",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
  )
  private val HREF = Regex("""\bhref="(/v/[A-Za-z0-9]+)"""", RegexOption.IGNORE_CASE)
  private val TITLE_ATTRIBUTE = Regex("""\btitle="([^"]*)"""", RegexOption.IGNORE_CASE)
  private val CURRENT_TITLE = Regex(
    """<strong[^>]*\bclass="[^"]*\bcurrent-title\b[^"]*"[^>]*>(.*?)</strong>""",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
  )
  private val ORIGIN_TITLE = Regex(
    """<[^>]*\bclass="[^"]*\borigin-title\b[^"]*"[^>]*>(.*?)</(?:span|div|strong|h2)>""",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
  )
  private val PAGE_TITLE = Regex(
    """<title[^>]*>(.*?)</title>""",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
  )
  private val COVER_TAG = Regex(
    """<img\b[^>]*\bclass="[^"]*\bvideo-cover\b[^"]*"[^>]*>""",
    RegexOption.IGNORE_CASE,
  )
  private val IMAGE_SOURCE = Regex("""\bsrc="([^"]+)"""", RegexOption.IGNORE_CASE)
  private val IMAGE_DATA_SOURCE = Regex("""\bdata-src="([^"]+)"""", RegexOption.IGNORE_CASE)
  private val ACTOR_ANCHOR = Regex(
    """<a\b([^>]*)>(.*?)</a>""",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
  )
  private val ACTOR_HREF = Regex("""\bhref="/actors/[A-Za-z0-9]+"""", RegexOption.IGNORE_CASE)
  private val ACTOR_FEMALE = Regex("""\bactor-female\b""", RegexOption.IGNORE_CASE)
  private val GENRE_ANCHOR = Regex(
    """<a\b[^>]*>(.*?)</a>""",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
  )
  private val DIGITS = Regex("""\d+""")
  private val TAG = Regex("""<[^>]*>""")
  private val WHITESPACE = Regex("""\s+""")
  private val ENTITY = Regex("""&(#x[0-9a-fA-F]+|#\d+|nbsp|amp|quot|apos|lt|gt);""")
  private val LOGIN_TITLE = Regex(
    """<title>\s*(?:登入|登录|login|sign in)\s*\|""",
    RegexOption.IGNORE_CASE,
  )
  private val GENRE_SEPARATOR = Regex("""[,、]+""")
  private val JAPANESE = Regex("[\\u3040-\\u30ff\\u4e00-\\u9fff]")

  fun searchRows(html: String): List<JavDbPageRow> =
    SEARCH_ROW.findAll(html)
      .mapNotNull { match ->
        val attributes = match.groupValues[1]
        val id = HREF.find(attributes)?.groupValues?.get(1)?.substringAfterLast('/')
        val code = clean(match.groupValues[3])
        if (id.isNullOrBlank() || code.isBlank()) return@mapNotNull null
        val title = TITLE_ATTRIBUTE.find(attributes)?.groupValues?.get(1)?.let(::clean).orEmpty()
        JavDbPageRow(id = id, code = code, title = title)
      }
      .distinctBy { it.id }
      .toList()

  fun detail(html: String): JavDbPageDetail {
    val panels = PANEL.findAll(html).associate { match ->
      clean(match.groupValues[1]) to match.groupValues[2]
    }
    val code = panel(panels, FIELD_CODE, FIELD_CODE_SIMPLIFIED)
      .replace(WHITESPACE, "")
      .replace("\u00a0", "")
      .replace("\u3000", "")
    if (code.isBlank()) {
      throw JavSourceException(
        JavSourceFailureKind.PARSE,
        "JavDB web page did not contain a movie code",
      )
    }

    val displayTitle = CURRENT_TITLE.find(html)?.groupValues?.get(1)?.let(::clean).orEmpty()
    val originalTitle = ORIGIN_TITLE.find(html)?.groupValues?.get(1)?.let(::clean).orEmpty()
    val pageTitle = PAGE_TITLE.find(html)?.groupValues?.get(1)
      ?.let(::clean)
      ?.substringBefore(" | JavDB")
      ?.substringBefore(" - JavDB")
      .orEmpty()

    val actorsHtml = panel(panels, FIELD_ACTORS, FIELD_ACTORS_SIMPLIFIED)
    val actresses = ACTOR_ANCHOR.findAll(actorsHtml)
      .filter { anchor ->
        ACTOR_HREF.containsMatchIn(anchor.groupValues[1]) &&
          ACTOR_FEMALE.containsMatchIn(anchor.groupValues[1])
      }
      .map { clean(it.groupValues[2]) }
      .filter { it.isNotBlank() }
      .distinct()
      .toList()

    val genresHtml = panel(panels, FIELD_GENRES, FIELD_GENRES_SIMPLIFIED)
    val genres = GENRE_ANCHOR.findAll(genresHtml)
      .map { clean(it.groupValues[1]) }
      .filter { it.isNotBlank() }
      .distinct()
      .toList()
      .ifEmpty {
        clean(genresHtml).split(GENRE_SEPARATOR)
          .map { it.trim() }
          .filter { it.isNotBlank() }
          .distinct()
      }

    val runtime = panel(panels, FIELD_RUNTIME, FIELD_RUNTIME_SIMPLIFIED)
      .let { DIGITS.find(it)?.value?.toIntOrNull() }

    return JavDbPageDetail(
      code = code,
      displayTitle = displayTitle.ifBlank { pageTitle },
      originalTitle = originalTitle,
      releaseDate = panel(panels, FIELD_DATE).takeIf { it.isNotBlank() },
      runtimeMinutes = runtime?.takeIf { it > 0 },
      studio = preferJapanese(panel(panels, FIELD_STUDIO)).takeIf { it.isNotBlank() },
      label = panel(panels, FIELD_LABEL, FIELD_LABEL_SIMPLIFIED).takeIf { it.isNotBlank() },
      series = panel(panels, FIELD_SERIES).takeIf { it.isNotBlank() },
      director = panel(panels, FIELD_DIRECTOR, FIELD_DIRECTOR_SIMPLIFIED)
        .takeIf { it.isNotBlank() },
      genres = genres,
      actresses = actresses,
      coverUrl = coverUrl(html),
    )
  }

  fun isLoginPage(html: String): Boolean = LOGIN_TITLE.containsMatchIn(html)

  fun toMetadata(detail: JavDbPageDetail, movieId: String): JavMetadata {
    val preferred = detail.originalTitle.ifBlank { detail.displayTitle }.ifBlank { detail.code }
    val stripped = stripCodePrefix(preferred, detail.code)
    return JavMetadata(
      code = detail.code,
      title = stripped.ifBlank { preferred },
      originalTitle = detail.originalTitle.takeIf { it.isNotBlank() },
      releaseDate = detail.releaseDate,
      runtimeMinutes = detail.runtimeMinutes,
      studio = detail.studio,
      label = detail.label,
      series = detail.series,
      director = detail.director,
      genres = detail.genres,
      actresses = detail.actresses,
      coverUrl = detail.coverUrl,
      source = "JavDB",
      sourceUrl = "$JAVDB_BASE_URL/v/$movieId",
      sourceId = movieId,
    )
  }

  private fun coverUrl(html: String): String? {
    val tag = COVER_TAG.find(html)?.value ?: return null
    val value = IMAGE_SOURCE.find(tag)?.groupValues?.get(1)
      ?: IMAGE_DATA_SOURCE.find(tag)?.groupValues?.get(1)
      ?: return null
    val resolved = runCatching {
      URI("$JAVDB_BASE_URL/").resolve(value.trim()).toString()
    }.getOrNull() ?: return null
    val host = runCatching { URI(resolved).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return null
    val allowed = host == "javdb.com" ||
      host.endsWith(".javdb.com") ||
      host.endsWith(".jdbstatic.com") ||
      host.endsWith(".spfcas.com")
    return resolved.takeIf { it.startsWith("https://") && allowed }
  }

  /**
   * JavDB writes the studio as `PRESTIGE,プレステージ`. Keep the Japanese form, which matches
   * the catalogue and the rest of the metadata; fall back to the first value when none is.
   */
  private fun preferJapanese(value: String): String {
    val parts = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    return parts.firstOrNull { JAPANESE.containsMatchIn(it) } ?: parts.firstOrNull().orEmpty()
  }

  private fun stripCodePrefix(value: String, code: String): String {
    if (!value.startsWith(code, ignoreCase = true)) return value
    return value.substring(code.length).trim().trimStart('-', ':', '：', ' ')
  }

  private fun panel(panels: Map<String, String>, vararg names: String): String =
    names.firstNotNullOfOrNull { panels[it] }.orEmpty()

  private fun clean(value: String): String =
    decodeEntities(value.replace(TAG, " ")).replace(WHITESPACE, " ").trim()

  private fun decodeEntities(value: String): String =
    ENTITY.replace(value) { match ->
      val entity = match.groupValues[1]
      when {
        entity == "nbsp" -> " "
        entity == "amp" -> "&"
        entity == "quot" -> "\""
        entity == "apos" -> "'"
        entity == "lt" -> "<"
        entity == "gt" -> ">"
        entity.startsWith("#x") ->
          entity.drop(2).toIntOrNull(16)?.let(::character) ?: match.value
        entity.startsWith("#") ->
          entity.drop(1).toIntOrNull()?.let(::character) ?: match.value
        else -> match.value
      }
    }

  private fun character(value: Int): String =
    if (value in 0..0x10FFFF) String(Character.toChars(value)) else ""
}
