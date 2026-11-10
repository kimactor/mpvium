package app.aryan447.mpvium.domain.jav

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

enum class JavBatchFilter {
  ALL,
  MISSING,
  FAILED,
  STALE,
}

object JavBatchScheduler {
  const val UNIQUE_SCRAPE = "jav-batch-scrape"
  const val UNIQUE_PREFETCH = "jav-batch-prefetch"
  const val KEY_MODE = "mode"
  const val KEY_FILTER = "filter"
  const val KEY_DONE = "done"
  const val KEY_TOTAL = "total"
  const val KEY_SUCCESS = "success"
  const val KEY_FAILED = "failed"
  const val KEY_NO_COVER = "no_cover"
  const val KEY_ALREADY_CACHED = "already_cached"
  const val KEY_REASON = "reason"
  const val KEY_ERROR = "error"
  const val MODE_SCRAPE = "scrape"
  const val MODE_PREFETCH = "prefetch"
  const val REASON_NO_METADATA = "no_metadata"
  const val REASON_ALL_CACHED = "all_cached"
  const val REASON_NO_COVER_URL = "no_cover_url"
  const val REASON_NO_ITEMS = "no_items"

  fun enqueueScrape(context: Context, filter: JavBatchFilter) {
    val request = OneTimeWorkRequestBuilder<JavBatchWorker>()
      .setInputData(
        workDataOf(
          KEY_MODE to MODE_SCRAPE,
          KEY_FILTER to filter.name,
        ),
      )
      .setConstraints(networkConstraints())
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      UNIQUE_SCRAPE,
      ExistingWorkPolicy.REPLACE,
      request,
    )
  }

  fun enqueuePrefetch(context: Context) {
    val request = OneTimeWorkRequestBuilder<JavBatchWorker>()
      .setInputData(workDataOf(KEY_MODE to MODE_PREFETCH))
      .setConstraints(networkConstraints())
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      UNIQUE_PREFETCH,
      ExistingWorkPolicy.REPLACE,
      request,
    )
  }

  private fun networkConstraints(): Constraints =
    Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()
}
