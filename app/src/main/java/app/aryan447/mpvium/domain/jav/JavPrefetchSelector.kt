package app.aryan447.mpvium.domain.jav

import app.aryan447.mpvium.database.entities.JavItemEntity

internal data class JavPrefetchSelection(
  val targets: List<JavItemEntity>,
  val alreadyCached: Int,
  val skipped: Int,
)

/**
 * Splits cover-prefetch candidates into due and waiting items. `nextRetryAt` is written by the
 * metadata repository after a failed scrape; without this filter a prefetch run would hit the
 * same blocked source again for every item immediately after a restart.
 */
internal object JavPrefetchSelector {
  fun select(
    items: List<JavItemEntity>,
    now: Long,
    hasCover: (JavItemEntity) -> Boolean,
  ): JavPrefetchSelection {
    val alreadyCached = items.count(hasCover)
    val pending = items.filterNot(hasCover)
    val targets = pending
      .filter { (it.nextRetryAt ?: 0L) <= now }
      .sortedBy { it.code }
    return JavPrefetchSelection(
      targets = targets,
      alreadyCached = alreadyCached,
      skipped = pending.size - targets.size,
    )
  }
}
