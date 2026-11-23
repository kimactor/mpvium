package app.aryan447.mpvium.domain.jav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class JavDbPageSourceTest {
  private val searchHtml = """
    <html><body>
      <div class="movie-list">
        <a href="/v/Nope" class="other"><strong>NOPE-001</strong></a>
        <a href="/v/AbC123" class="box" title="SSIS-057 テスト">
          <div class="cover"><img src="//c0.jdbstatic.com/covers/ss/SSIS-057.jpg"></div>
          <strong>SSIS-057</strong>
        </a>
        <a href="/v/Def456" class="box" title="SSIS-057-C 限定版">
          <strong>SSIS-057-C</strong>
        </a>
      </div>
    </body></html>
  """.trimIndent()

  private val detailHtml = """
    <html><head><title>SSIS-057 テスト | JavDB</title></head><body>
      <div class="panel-block first-block"><strong>番號:</strong><span class="value">SSIS-057</span></div>
      <div class="panel-block"><strong>日期:</strong><span class="value">2021-05-07</span></div>
      <div class="panel-block"><strong>時長:</strong><span class="value">120 分鐘</span></div>
      <div class="panel-block"><strong>演員:</strong><span class="value">
        <a href="/actors/a1" class="item actor-female">女優A</a>
        <a href="/actors/a2" class="item actor-male">男優B</a>
        <a href="/actors/a3" class="item actor-female">女優C</a>
      </span></div>
      <div class="panel-block"><strong>片商:</strong><span class="value">PRESTIGE,プレステージ</span></div>
      <div class="panel-block"><strong>發行:</strong><span class="value">S1 NO.1 STYLE</span></div>
      <div class="panel-block"><strong>系列:</strong><span class="value">テストシリーズ</span></div>
      <div class="panel-block"><strong>導演:</strong><span class="value">監督名</span></div>
      <div class="panel-block"><strong>類別:</strong><span class="value">
        <a href="/tags/1">巨乳</a><a href="/tags/2">中文字幕</a>
      </span></div>
      <strong class="current-title">SSIS-057 テスト</strong>
      <span class="origin-title">SSIS-057 オリジナル</span>
      <img class="video-cover lazy" src="https://c0.jdbstatic.com/covers/ss/SSIS-057.jpg">
    </body></html>
  """.trimIndent()

  @Test
  fun parsesSearchRowsWithBoxLinksOnly() {
    val rows = JavDbPageParser.searchRows(searchHtml)

    assertEquals(2, rows.size)
    assertEquals("AbC123", rows[0].id)
    assertEquals("SSIS-057", rows[0].code)
    assertEquals("SSIS-057 テスト", rows[0].title)
    assertEquals("Def456", rows[1].id)
    assertEquals("SSIS-057-C", rows[1].code)
  }

  @Test
  fun parsesDetailFieldsAndFemaleActresses() {
    val detail = JavDbPageParser.detail(detailHtml)

    assertEquals("SSIS-057", detail.code)
    assertEquals("SSIS-057 テスト", detail.displayTitle)
    assertEquals("SSIS-057 オリジナル", detail.originalTitle)
    assertEquals("2021-05-07", detail.releaseDate)
    assertEquals(120, detail.runtimeMinutes ?: -1)
    assertEquals("プレステージ", detail.studio)
    assertEquals("S1 NO.1 STYLE", detail.label)
    assertEquals("テストシリーズ", detail.series)
    assertEquals("監督名", detail.director)
    assertEquals(listOf("女優A", "女優C"), detail.actresses)
    assertEquals(listOf("巨乳", "中文字幕"), detail.genres)
    assertEquals("https://c0.jdbstatic.com/covers/ss/SSIS-057.jpg", detail.coverUrl)
  }

  @Test
  fun mapsToMetadataAndStripsCodePrefix() {
    val metadata = JavDbPageParser.toMetadata(
      JavDbPageParser.detail(detailHtml),
      "AbC123",
    )

    assertEquals("SSIS-057", metadata.code)
    assertEquals("オリジナル", metadata.title)
    assertEquals("SSIS-057 オリジナル", metadata.originalTitle)
    assertEquals("JavDB", metadata.source)
    assertEquals("https://javdb.com/v/AbC123", metadata.sourceUrl)
    assertEquals("AbC123", metadata.sourceId)
  }

  @Test
  fun detectsLoginPagesAndRejectsMissingCode() {
    assertTrue(JavDbPageParser.isLoginPage("<html><head><title>登入 | JavDB</title></head></html>"))
    assertFalse(JavDbPageParser.isLoginPage(detailHtml))
    assertThrows(JavSourceException::class.java) {
      JavDbPageParser.detail("<html><body>not a movie</body></html>")
    }
  }

  @Test
  fun dropsEmptyPlaceholderFields() {
    val detail = detailHtml.replace(
      "<span class=\"value\">監督名</span>",
      "<span class=\"value\">----</span>",
    )

    assertNull(JavDbPageParser.detail(detail).director)
  }

  @Test
  fun rejectsCoversOutsideJavdbHosts() {
    val detail = detailHtml.replace(
      "https://c0.jdbstatic.com/covers/ss/SSIS-057.jpg",
      "https://evil.example/covers/ss/SSIS-057.jpg",
    )

    assertNull(JavDbPageParser.detail(detail).coverUrl)
  }
}
