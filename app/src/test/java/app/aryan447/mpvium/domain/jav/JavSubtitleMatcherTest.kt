package app.aryan447.mpvium.domain.jav

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class JavSubtitleMatcherTest {
  @Test
  fun matchesReleaseCodesInSubtitleNames() {
    assertTrue(JavSubtitleMatcher.matchesCode("SSIS-057.chs.srt", "SSIS-057"))
    assertTrue(JavSubtitleMatcher.matchesCode("ssis057.ass", "SSIS-057"))
    assertTrue(JavSubtitleMatcher.matchesCode("[Group] SSIS-057 中文字幕.srt", "SSIS-057"))
    assertFalse(JavSubtitleMatcher.matchesCode("SSIS-058.srt", "SSIS-057"))
    assertFalse(JavSubtitleMatcher.matchesCode("SSIS-057.mp4", "SSIS-057"))
  }

  @Test
  fun detectsChineseSubtitleNames() {
    assertTrue(JavSubtitleMatcher.isChineseName("SSIS-057.chs.srt"))
    assertTrue(JavSubtitleMatcher.isChineseName("SSIS-057.zh-CN.srt"))
    assertTrue(JavSubtitleMatcher.isChineseName("SSIS-057 中文字幕.srt"))
    assertFalse(JavSubtitleMatcher.isChineseName("SSIS-057.eng.srt"))
  }

  @Test
  fun rewritesGbkSubtitlesAsUtf8() {
    val source = "1\n00:00:01,000 --> 00:00:02,000\n中文字幕\n"
    val gbk = source.toByteArray(Charset.forName("GBK"))

    val converted = JavSubtitleEncoding.toUtf8(gbk)

    assertEquals(source, String(converted, Charsets.UTF_8))
    assertTrue(JavSubtitleEncoding.looksLikeSubtitle(converted))
  }

  @Test
  fun keepsUtf8SubtitlesUnchanged() {
    val source = "1\n00:00:01,000 --> 00:00:02,000\n中文字幕\n"

    val converted = JavSubtitleEncoding.toUtf8(source.toByteArray(Charsets.UTF_8))

    assertEquals(source, String(converted, Charsets.UTF_8))
  }

  @Test
  fun rejectsNonSubtitlePayloads() {
    assertFalse(JavSubtitleEncoding.looksLikeSubtitle("not a subtitle".toByteArray()))
  }
}
