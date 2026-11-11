package app.aryan447.mpvium.domain.jav

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DmmSourceTest {
  private fun detail(json: String) = Json.parseToJsonElement(json).jsonObject

  @Test
  fun guessesCidFromStandardCode() {
    assertEquals("ssis00519", DmmCodes.guessedCid("SSIS-519"))
    assertEquals("fns00061", DmmCodes.guessedCid("FNS-061"))
    assertNull(DmmCodes.guessedCid("FC2-PPV-1234567"))
  }

  @Test
  fun keepsPrefixedCidsFromSearchResults() {
    assertEquals(
      listOf("h_1711suke00073"),
      DmmCodes.searchCandidates(
        "SUKE-073",
        listOf("h_1711suke00073", "kckc00073", "h_1377meko00732"),
      ),
    )
    assertEquals(
      listOf("fns00061", "1fns00061"),
      DmmCodes.searchCandidates("FNS-061", listOf("fns00061", "1fns00061")),
    )
  }

  @Test
  fun prefersTheUnnumberedEdition() {
    assertEquals(
      listOf("ssis00519", "48ssis00519ai"),
      DmmCodes.searchCandidates("SSIS-519", listOf("48ssis00519ai", "ssis00519")),
    )
  }

  @Test
  fun convertsReleaseDateToJapanTime() {
    assertEquals("2021-05-07", DmmCodes.releaseDate("2021-05-06T15:00:00Z"))
    assertEquals("2021-05-07", DmmCodes.releaseDate("2021-05-07"))
    assertNull(DmmCodes.releaseDate(""))
  }

  @Test
  fun verifiesDetailIdentityByMakerCodeAndCid() {
    assertTrue(DmmCodes.matches("SSIS-519", detail("""{"id":"ssis00519","makerContentId":"SSIS-519"}""")))
    assertTrue(DmmCodes.matches("SSIS-519", detail("""{"id":"ssis00519","makerContentId":""}""")))
    assertFalse(DmmCodes.matches("SSIS-519", detail("""{"id":"ssis00520","makerContentId":"SSIS-520"}""")))
  }
}
