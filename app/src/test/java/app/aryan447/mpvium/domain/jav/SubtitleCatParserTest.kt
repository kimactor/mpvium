package app.aryan447.mpvium.domain.jav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SubtitleCatParserTest {
  @Test
  fun parsesAndDedupesSearchResults() {
    val html =
      """
      <a href="subs/272/%5BSubtitleTools.com%5D%2010.SSIS-057.html">one</a>
      <a href="subs/415/SSIS-057.html">two</a>
      <a href="/subs/415/SSIS-057.html">duplicate</a>
      """.trimIndent()

    assertEquals(
      listOf(
        "subs/272/%5BSubtitleTools.com%5D%2010.SSIS-057.html",
        "subs/415/SSIS-057.html",
      ),
      SubtitleCatParser.parseSearchResults(html),
    )
  }

  @Test
  fun parsesChineseDownloadLinks() {
    val html =
      """
      <a id="download_zh-CN" onclick="log_download(1);" href="/subs/424/SSIS-057-zh-CN.srt">zh</a>
      <a id="download_zh-TW" onclick="log_download(2);" href="/subs/424/SSIS-057-zh-TW.srt">zh</a>
      """.trimIndent()

    val links = SubtitleCatParser.parseChineseDownloads(html)

    assertEquals("/subs/424/SSIS-057-zh-CN.srt", links.simplified)
    assertEquals("/subs/424/SSIS-057-zh-TW.srt", links.traditional)
  }

  @Test
  fun reportsMissingChineseLinks() {
    val links = SubtitleCatParser.parseChineseDownloads("<html><body>no subtitles</body></html>")

    assertNull(links.simplified)
    assertNull(links.traditional)
  }

  @Test
  fun decodesResultTitles() {
    assertEquals(
      "[SubtitleTools.com] 10.SSIS-057",
      SubtitleCatParser.resultTitle("subs/272/%5BSubtitleTools.com%5D%2010.SSIS-057.html"),
    )
  }
}
