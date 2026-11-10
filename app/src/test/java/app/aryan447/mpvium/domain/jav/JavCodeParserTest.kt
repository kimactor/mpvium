package app.aryan447.mpvium.domain.jav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class JavCodeParserTest {

  @Test
  fun parsesStandardCensoredCode() {
    val match = JavCodeParser.parse("ABP-123.1080p.mkv")

    assertEquals("ABP-123", match?.code)
    assertEquals(JavCodeKind.STANDARD, match?.kind)
    assertEquals(0.95f, match?.confidence)
  }

  @Test
  fun normalizesCompactStandardCode() {
    val match = JavCodeParser.parse("IPX001.mp4")

    assertEquals("IPX-001", match?.code)
    assertEquals(JavCodeKind.STANDARD, match?.kind)
  }

  @Test
  fun normalizesFc2Codes() {
    assertEquals("FC2-PPV-1234567", JavCodeParser.parse("FC2-PPV-1234567.mp4")?.code)
    assertEquals("FC2-PPV-1234567", JavCodeParser.parse("FC2-1234567.mp4")?.code)
    assertEquals("FC2-PPV-43768", JavCodeParser.parse("FC-43768.mp4")?.code)
    assertEquals(JavCodeKind.FC2, JavCodeParser.parse("FC2PPV1234567.mp4")?.kind)
  }

  @Test
  fun stripsThreeDigitAmateurPrefixForLookup() {
    val match = JavCodeParser.parse("259LUXU-688.mp4")

    assertEquals("LUXU-688", match?.code)
    assertEquals("259LUXU-688", match?.rawCode)
    assertEquals(JavCodeKind.AMATEUR, match?.kind)
  }

  @Test
  fun recognizesAmateurPrefixesWithoutNumbers() {
    assertEquals("MIUM-1239", JavCodeParser.parse("300MIUM-1239.mp4")?.code)
    assertEquals(JavCodeKind.AMATEUR, JavCodeParser.parse("SIRO-1234.mp4")?.kind)
    assertEquals(JavCodeKind.AMATEUR, JavCodeParser.parse("STP-1234.mp4")?.kind)
  }

  @Test
  fun parsesDatedAndUncensoredCodes() {
    val dated = JavCodeParser.parse("123123-001.mp4")
    assertEquals("123123-001", dated?.code)
    assertEquals(JavCodeKind.DATED, dated?.kind)

    assertEquals("HEYZO-1234", JavCodeParser.parse("HEYZO-1234.mp4")?.code)
    assertEquals(JavCodeKind.UNCENSORED, JavCodeParser.parse("HEYZO-1234.mp4")?.kind)

    val tokyoHot = JavCodeParser.parse("n0780.mp4")
    assertEquals("N0780", tokyoHot?.code)
    assertEquals(JavCodeKind.UNCENSORED, tokyoHot?.kind)
    assertTrue(tokyoHot!!.confidence < JavCodeParser.MIN_AUTO_CONFIDENCE)
  }

  @Test
  fun comparesCanonicalReleaseIdentities() {
    assertTrue(JavCodeParser.sameReleaseCode("259LUXU-688", "LUXU-688"))
    assertTrue(JavCodeParser.sameReleaseCode("abp-123", "ABP-123"))
    assertFalse(JavCodeParser.sameReleaseCode("ABP-123", "ABP-124"))
  }

  @Test
  fun fallsBackToParentFolder() {
    val match = JavCodeParser.parseFile("movie.mkv", "ABP-123")

    assertEquals("ABP-123", match?.code)
  }

  @Test
  fun ignoresSeasonEpisodeAndTechnicalTokens() {
    assertNull(JavCodeParser.parse("Breaking.Bad.S01E01.1080p.mkv"))
    assertNull(JavCodeParser.parse("The.100.S01E01.1080p.mkv"))
    assertNull(JavCodeParser.parse("Movie.2024.1080p.WEB-DL.DDP5.1.x264.mkv"))
    assertNull(JavCodeParser.parse("H264.x265.HEVC.10bit.mkv"))
    assertNull(JavCodeParser.parse("UHD.BluRay.REMUX.DTS-HD.MA.5.1.mkv"))
  }

  @Test
  fun doesNotTruncateLongerCodes() {
    assertEquals("ABP-1234", JavCodeParser.parse("ABP-1234.mp4")?.code)
    assertEquals("ABP-12345", JavCodeParser.parse("ABP-12345.mp4")?.code)
    assertNull(JavCodeParser.parse("ABP-123456.mp4"))
  }

  @Test
  fun keepsPartSuffixOutOfCode() {
    assertEquals("ABP-123", JavCodeParser.parse("ABP-123-C.mp4")?.code)
    assertEquals("ABP-123", JavCodeParser.parse("ABP-123-4K.mp4")?.code)
    assertEquals("ABP-123", JavCodeParser.parse("ABP-123-N0780.mp4")?.code)
    assertEquals("ABP-123", JavCodeParser.parse("x264.ABP-123.mkv")?.code)
    assertEquals("ABP-123", JavCodeParser.parse("HD-ABP-123.mkv")?.code)
  }
}
