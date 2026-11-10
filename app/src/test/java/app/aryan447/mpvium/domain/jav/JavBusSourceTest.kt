package app.aryan447.mpvium.domain.jav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class JavBusSourceTest {

  @Test
  fun parsesWorkPageFields() {
    val metadata = JavBusParser.parse(
      code = "ABP-123",
      html = """
        <html>
          <h3>ABP-123 Sample Title</h3>
          <a class="bigImage" href="/pics/cover/abc_b.jpg"><img src="/pics/cover/abc.jpg"></a>
          <p><span class="header">識別碼:</span> ABP-123</p>
          <p><span class="header">發行日期:</span> 2024-01-02</p>
          <p><span class="header">長度:</span> 120 分鐘</p>
          <p><span class="header">製作商:</span> Studio A</p>
          <p><span class="header">發行商:</span> Label B</p>
          <p><span class="header">系列:</span> Series C</p>
          <p><span class="header">導演:</span> Director D</p>
          <div class="star-name"><a href="/star/1">Actress One</a></div>
          <div class="star-name"><a href="/star/2">Actress Two</a></div>
        </html>
      """.trimIndent(),
      sourceUrl = "https://www.javbus.com/ABP-123",
      cookieMissing = false,
    )

    assertEquals("ABP-123", metadata.code)
    assertEquals("Sample Title", metadata.title)
    assertEquals("2024-01-02", metadata.releaseDate)
    assertEquals(120, metadata.runtimeMinutes)
    assertEquals("Studio A", metadata.studio)
    assertEquals("Label B", metadata.label)
    assertEquals("Series C", metadata.series)
    assertEquals("Director D", metadata.director)
    assertEquals(listOf("Actress One", "Actress Two"), metadata.actresses)
    assertEquals("https://www.javbus.com/pics/cover/abc_b.jpg", metadata.coverUrl)
  }

  @Test
  fun rejectsAgeGatePage() {
    val error = assertThrows(JavSourceException::class.java) {
      JavBusParser.parse(
        code = "ABP-123",
        html = "<html><h1>Age verification</h1></html>",
        sourceUrl = "https://www.javbus.com/ABP-123",
        cookieMissing = true,
      )
    }

    assertEquals(JavSourceFailureKind.AUTH_REQUIRED, error.kind)
    assertTrue(error.message!!.contains("cookie"))
  }

  @Test
  fun rejectsMismatchedCode() {
    val error = assertThrows(JavSourceException::class.java) {
      JavBusParser.parse(
        code = "ABP-123",
        html = """<p><span class="header">識別碼:</span> ABP-999</p>""",
        sourceUrl = "https://www.javbus.com/ABP-123",
        cookieMissing = false,
      )
    }

    assertEquals(JavSourceFailureKind.NOT_FOUND, error.kind)
  }
}
