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
  const val MODE_SCRAPE = "scrape"
  const val MODE_PREFETCH = "prefetch"

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
