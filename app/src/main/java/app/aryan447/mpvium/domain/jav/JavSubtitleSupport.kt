package app.aryan447.mpvium.domain.jav

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * Filename heuristics shared by the JAV subtitle scanner, importer, and online source.
 *
 * JAV subtitle files are usually named after the release code (`SSIS-057.chs.srt`) instead of the
 * video filename, so plain prefix matching misses them.
 */
object JavSubtitleMatcher {
  val extensions = setOf("srt", "ass", "ssa", "vtt", "sub")

  private val chineseTokens =
    setOf(
      "zh",
      "chs",
      "cht",
      "chi",
      "sc",
      "tc",
      "gb",
      "big5",
      "zhcn",
      "zhtw",
      "zhhans",
      "zhhant",
      "gbk",
      "cn",
      "ch",
      "chinese",
    )

  private val chineseNameWords = listOf("中文", "中字", "简体", "繁体", "繁體", "简中", "繁中")
  private val kana = Regex("[\\u3040-\\u30ff\\uff66-\\uff9d]")

  fun isSubtitleFile(fileName: String): Boolean =
    fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) in extensions

  fun matchesCode(fileName: String, code: String): Boolean {
    if (!isSubtitleFile(fileName)) return false
    val stem = fileName.substringBeforeLast('.')
    if (JavCodeParser.parse(stem)?.code?.equals(code, ignoreCase = true) == true) return true
    return codePattern(code)?.containsMatchIn(stem) == true
  }

  fun isChineseName(fileName: String): Boolean {
    val stem = fileName.substringBeforeLast('.').lowercase(Locale.ROOT)
    if (chineseNameWords.any { stem.contains(it) }) return true
    if (kana.containsMatchIn(stem)) return false
    val tokens = stem.split(NON_ALPHANUMERIC)
    return tokens.any { it in chineseTokens }
  }

  /**
   * Matches the code with separator tolerance, but requires a boundary before and after it so
   * `ABP-123` cannot claim `ABP-1234.chs.srt` and `IBW-483` cannot claim `IBW-483z.srt`.
   */
  private fun codePattern(code: String): Regex? {
    val parts = code.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    val body = parts.joinToString("[-_ .]?") { Regex.escape(it) }
    return Regex("(?<![A-Za-z0-9])$body(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)
  }

  private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
}

/**
 * Text subtitles from Chinese sources are frequently GBK/GB18030 even when they claim `.srt`.
 * MPV would render those as mojibake, so everything imported or downloaded is rewritten as UTF-8.
 */
object JavSubtitleEncoding {
  fun toUtf8(bytes: ByteArray): ByteArray {
    if (bytes.isEmpty()) return bytes
    val text =
      decodeStrict(bytes, Charsets.UTF_8)
        ?: decodeStrict(bytes, Charset.forName("GB18030"))
        ?: return bytes
    return text.removePrefix("\uFEFF").toByteArray(Charsets.UTF_8)
  }

  fun looksLikeSubtitle(bytes: ByteArray): Boolean {
    if (bytes.size < 16) return false
    val probe = String(bytes, 0, minOf(bytes.size, 512), Charset.forName("ISO-8859-1"))
    return probe.contains("-->") ||
      probe.contains("[Script Info]", ignoreCase = true) ||
      probe.startsWith("WEBVTT", ignoreCase = true) ||
      probe.contains("{") && probe.contains("}")
  }

  private fun decodeStrict(
    bytes: ByteArray,
    charset: Charset,
  ): String? =
    runCatching {
      charset
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
    }.getOrNull()
}
