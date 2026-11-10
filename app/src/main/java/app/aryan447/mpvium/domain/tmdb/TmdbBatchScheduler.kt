package app.aryan447.mpvium.domain.tmdb

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object TmdbBatchScheduler {
  const val UNIQUE_WORK = "tmdb-metadata-enrich"

  fun enqueue(context: Context) {
    val request = OneTimeWorkRequestBuilder<TmdbEnrichWorker>()
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build(),
      )
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      UNIQUE_WORK,
      ExistingWorkPolicy.REPLACE,
      request,
    )
  }
}
