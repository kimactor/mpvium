package app.aryan447.mpvium.domain.jav

enum class JavCodeKind {
  STANDARD,
  FC2,
  AMATEUR,
  DATED,
  UNCENSORED,
}

data class JavCodeMatch(
  val code: String,
  val rawCode: String,
  val kind: JavCodeKind,
  val confidence: Float,
)

object JavCodeParser {
  const val MIN_AUTO_CONFIDENCE = 0.85f

  private val amateurPrefixes = setOf("SIRO", "STP", "STN", "LUXU", "MIUM")

  private val blockedPrefixes = setOf(
    "S", "E", "EP", "SE", "H", "X", "AV", "AVC", "HEVC", "HDR", "DV",
    "DDP", "DD", "DTS", "AAC", "AC", "MP", "MPEG", "VC", "VP", "BT",
    "UHD", "WEB", "DL", "REMUX", "BLURAY", "BDRIP", "BRRIP", "HDRIP",
    "CAM", "TS", "TC", "HD", "FHD", "SD", "NF", "AMZN", "DSNP", "ATVP",
    "HMAX", "STAN", "TV", "MOVIE", "PART", "CD", "DISC", "SEASON",
    "EPISODE", "RES", "FPS", "BIT", "CH", "AUDIO", "SUB", "ENG", "JPN",
    "CHS", "CHT", "GB", "MB", "KB", "TB", "MP4", "MKV", "AVI", "WMV",
    "ISO", "RAR", "ZIP", "XXX", "PORN", "FULL", "HDTV", "PDTV", "DVDRIP",
    "BR", "DVD", "VHS", "AM", "PM",
  )

  private val fc2 = Regex(
    """(?i)(?<![A-Z0-9])(?:FC2[-_ ]?PPV[-_ ]?|FC2[-_ ]?|FC[-_ ]?)(\d{5,8})(?![0-9])""",
  )
  private val amateur = Regex(
    """(?<![0-9])(\d{3})[-_ ]?([A-Z]{3,6})[-_ ](\d{2,4})(?![0-9])""",
    RegexOption.IGNORE_CASE,
  )
  private val dated = Regex(
    """(?<!\d)(\d{6})[-_](\d{3})(?!\d)""",
  )
  private val heyzo = Regex(
    """(?i)(?<![A-Z0-9])HEYZO[-_ ]?(\d{3,4})(?![0-9])""",
  )

  /**
   * Labels whose own name contains digits. They cannot go through the generic
   * letter-only patterns, and shorter labels must not eat the prefix of a longer one
   * (`S2MCR-08` before `S2M`).
   */
  private val digitLabels = listOf("CW3D2DBD", "S2MBD", "S2MCR", "S2M", "T28", "T38")
  private val digitLabel = Regex(
    "(?i)(?<![A-Z0-9])(" +
      digitLabels.sortedByDescending { it.length }.joinToString("|") +
      ")[-_ .]?(\\d{2,5})(?![0-9])",
  )
  private val serialWidths = mapOf(
    "CW3D2DBD" to 2,
    "S2MBD" to 3,
    "S2MCR" to 2,
    "S2M" to 3,
    "T28" to 3,
    "T38" to 3,
  )

  private val kirari = Regex(
    """(?i)(?<![A-Z0-9])(MKB?D-S)(\d{2,4})(?![0-9])""",
  )

  // IBW appends a z to this release family (IBW-483z); most other trailing letters
  // are part markers and stay outside the code.
  private val ibwSuffix = Regex(
    """(?i)(?<![A-Z0-9])(IBW)[-_ ]?(\d{2,5})(Z)(?![A-Z0-9])""",
  )

  private val tokyoHot = Regex(
    """(?i)(?<![A-Z0-9])N[-_ ]?(\d{3,4})(?![0-9])""",
  )
  private val standardWithSeparator = Regex(
    """(?<![A-Z0-9])([A-Z]{2,6})[-_](\d{2,5})(?![0-9])""",
    RegexOption.IGNORE_CASE,
  )
  private val standardCompact = Regex(
    """(?<![A-Z0-9])([A-Z]{2,6})(\d{3,5})(?!\d)""",
    RegexOption.IGNORE_CASE,
  )

  fun parse(displayName: String): JavCodeMatch? = parseToken(displayName)

  fun parseFile(displayName: String, parentName: String? = null): JavCodeMatch? =
    parseToken(displayName) ?: parentName?.let(::parseToken)

  fun sameReleaseCode(first: String, second: String): Boolean {
    val parsedFirst = parse(first)?.code
    val parsedSecond = parse(second)?.code
    if (parsedFirst != null && parsedSecond != null) return parsedFirst == parsedSecond
    return compareKey(first) == compareKey(second)
  }

  private fun compareKey(value: String): String =
    value.uppercase().filter { it.isLetterOrDigit() }

  private fun parseToken(value: String): JavCodeMatch? {
    val input = value.trim().uppercase()
    if (input.isBlank()) return null

    fc2.find(input)?.let { match ->
      val digits = match.groupValues[1]
      return JavCodeMatch(
        code = "FC2-PPV-$digits",
        rawCode = match.value.trim(),
        kind = JavCodeKind.FC2,
        confidence = 0.98f,
      )
    }

    amateur.find(input)?.let { match ->
      val prefix = match.groupValues[2].uppercase()
      val digits = match.groupValues[3]
      return JavCodeMatch(
        code = "$prefix-$digits",
        rawCode = match.value.trim(),
        kind = JavCodeKind.AMATEUR,
        confidence = 0.9f,
      )
    }

    dated.find(input)?.let { match ->
      val code = "${match.groupValues[1]}-${match.groupValues[2]}"
      return JavCodeMatch(
        code = code,
        rawCode = match.value.trim(),
        kind = JavCodeKind.DATED,
        confidence = 0.85f,
      )
    }

    heyzo.find(input)?.let { match ->
      val digits = match.groupValues[1]
      return JavCodeMatch(
        code = "HEYZO-$digits",
        rawCode = match.value.trim(),
        kind = JavCodeKind.UNCENSORED,
        confidence = 0.95f,
      )
    }

    digitLabel.find(input)?.let { match ->
      val label = match.groupValues[1].uppercase()
      val digits = match.groupValues[2]
      val width = serialWidths[label] ?: digits.length
      return JavCodeMatch(
        code = "$label-${digits.padStart(width, '0')}",
        rawCode = match.value.trim(),
        kind = if (label == "T28" || label == "T38") {
          JavCodeKind.STANDARD
        } else {
          JavCodeKind.UNCENSORED
        },
        confidence = 0.95f,
      )
    }

    kirari.find(input)?.let { match ->
      return JavCodeMatch(
        code = "${match.groupValues[1].uppercase()}${match.groupValues[2]}",
        rawCode = match.value.trim(),
        kind = JavCodeKind.UNCENSORED,
        confidence = 0.95f,
      )
    }

    ibwSuffix.find(input)?.let { match ->
      return JavCodeMatch(
        code = "IBW-${match.groupValues[2]}Z",
        rawCode = match.value.trim(),
        kind = JavCodeKind.STANDARD,
        confidence = 0.95f,
      )
    }

    standardWithSeparator.findAll(input)
      .firstOrNull { it.groupValues[1].uppercase() !in blockedPrefixes }
      ?.let { match ->
        val prefix = match.groupValues[1].uppercase()
        val digits = match.groupValues[2]
        return JavCodeMatch(
          code = "$prefix-$digits",
          rawCode = match.value.trim(),
          kind = if (prefix in amateurPrefixes) JavCodeKind.AMATEUR else JavCodeKind.STANDARD,
          confidence = if (prefix in amateurPrefixes) 0.9f else 0.95f,
        )
      }

    standardCompact.findAll(input)
      .firstOrNull { it.groupValues[1].uppercase() !in blockedPrefixes }
      ?.let { match ->
        val prefix = match.groupValues[1].uppercase()
        val digits = match.groupValues[2]
        return JavCodeMatch(
          code = "$prefix-$digits",
          rawCode = match.value.trim(),
          kind = if (prefix in amateurPrefixes) JavCodeKind.AMATEUR else JavCodeKind.STANDARD,
          confidence = 0.85f,
        )
      }

    tokyoHot.find(input)?.let { match ->
      val digits = match.groupValues[1]
      return JavCodeMatch(
        code = "N$digits",
        rawCode = match.value.trim(),
        kind = JavCodeKind.UNCENSORED,
        confidence = 0.7f,
      )
    }

    return null
  }
}
