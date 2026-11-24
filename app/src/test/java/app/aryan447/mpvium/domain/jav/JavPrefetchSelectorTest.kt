package app.aryan447.mpvium.domain.jav

import app.aryan447.mpvium.database.entities.JavItemEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class JavPrefetchSelectorTest {
  private val now = 1_000_000L

  @Test
  fun selectsDueItemsAndCountsWaitingOnes() {
    val items = listOf(
      JavItemEntity(code = "A-001", localCoverPath = "/cover.jpg"),
      JavItemEntity(code = "B-002", nextRetryAt = now - 1),
      JavItemEntity(code = "C-003", nextRetryAt = now + 1_000),
      JavItemEntity(code = "D-004"),
    )

    val selection = JavPrefetchSelector.select(items, now) { it.localCoverPath != null }

    assertEquals(listOf("B-002", "D-004"), selection.targets.map { it.code })
    assertEquals(1, selection.alreadyCached)
    assertEquals(1, selection.skipped)
  }

  @Test
  fun treatsMissingRetryTimeAsDue() {
    val selection = JavPrefetchSelector.select(
      items = listOf(JavItemEntity(code = "SSIS-057")),
      now = now,
      hasCover = { false },
    )

    assertEquals(listOf("SSIS-057"), selection.targets.map { it.code })
    assertEquals(0, selection.alreadyCached)
    assertEquals(0, selection.skipped)
  }
}
