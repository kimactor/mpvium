package app.aryan447.mpvium.domain.jav

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aryan447.mpvium.MainActivity
import app.aryan447.mpvium.R
import app.aryan447.mpvium.database.dao.JavDao
import app.aryan447.mpvium.database.entities.JavItemEntity
import java.io.File
import app.aryan447.mpvium.domain.streaming.LibraryIndexRepository
import app.aryan447.mpvium.domain.streaming.StreamingArtworkStore
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class JavBatchWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
  companion object {
    private const val CHANNEL_ID = "jav_batch"
    private const val NOTIFICATION_ID = 7103
    private const val STALE_AFTER_MS = 7L * 24 * 60 * 60 * 1000
    private const val KEY_DONE = JavBatchScheduler.KEY_DONE
    private const val KEY_TOTAL = JavBatchScheduler.KEY_TOTAL
  }

  private val javDao: JavDao by inject()
  private val libraryIndexRepository: LibraryIndexRepository by inject()
  private val metadataRepository: JavMetadataRepository by inject()
  private val artworkStore: StreamingArtworkStore by inject()
  private val javBusSource: JavBusSource by inject()
  private val javDbSource: JavDbPublicSource by inject()

  override suspend fun doWork(): Result {
    val mode = inputData.getString(JavBatchScheduler.KEY_MODE) ?: JavBatchScheduler.MODE_SCRAPE
    return try {
      when (mode) {
        JavBatchScheduler.MODE_PREFETCH -> prefetchCovers()
        else -> scrapeMetadata()
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      Result.failure(workDataOf(JavBatchScheduler.KEY_ERROR to (error.message ?: "JAV batch failed")))
    }
  }

  private suspend fun scrapeMetadata(): Result {
    libraryIndexRepository.getLibrary(force = true)
    val filter = runCatching {
      JavBatchFilter.valueOf(inputData.getString(JavBatchScheduler.KEY_FILTER) ?: JavBatchFilter.ALL.name)
    }.getOrDefault(JavBatchFilter.ALL)
    val now = System.currentTimeMillis()
    val targets = javDao.getAllItems()
      .filter { item ->
        when (filter) {
          JavBatchFilter.ALL -> true
          JavBatchFilter.MISSING -> item.status != JavItemEntity.STATUS_OK || item.title.isBlank()
          JavBatchFilter.FAILED -> item.status == JavItemEntity.STATUS_FAILED
          JavBatchFilter.STALE -> item.status == JavItemEntity.STATUS_OK &&
            (item.fetchedAt ?: 0L) < now - STALE_AFTER_MS
        }
      }
      .sortedBy { it.code }

    if (targets.isEmpty()) {
      return Result.success(workDataOf(KEY_DONE to 0, KEY_TOTAL to 0, JavBatchScheduler.KEY_SUCCESS to 0, JavBatchScheduler.KEY_FAILED to 0))
    }

    var success = 0
    var failed = 0
    publishProgress(0, targets.size, "scrape")
    targets.forEachIndexed { index, item ->
      if (isStopped) return Result.failure()
      val result = runCatching { metadataRepository.scrape(item.code, force = true) }.getOrNull()
      if (result != null && result.errorKind == null) success++ else failed++
      val done = index + 1
      if (done == targets.size || done % 3 == 0) {
        publishProgress(done, targets.size, item.code)
      }
    }
    return Result.success(
      workDataOf(
        KEY_DONE to targets.size,
        KEY_TOTAL to targets.size,
        JavBatchScheduler.KEY_SUCCESS to success,
        JavBatchScheduler.KEY_FAILED to failed,
      ),
    )
  }

  private suspend fun prefetchCovers(): Result {
    libraryIndexRepository.getLibrary(force = true)
    val allItems = javDao.getAllItems()
    if (allItems.isEmpty()) {
      val hasFiles = javDao.getAllFiles().isNotEmpty()
      return Result.success(
        workDataOf(
          KEY_DONE to 0,
          KEY_TOTAL to 0,
          JavBatchScheduler.KEY_ALREADY_CACHED to 0,
          JavBatchScheduler.KEY_REASON to if (hasFiles) {
            JavBatchScheduler.REASON_NO_METADATA
          } else {
            JavBatchScheduler.REASON_NO_ITEMS
          },
        ),
      )
    }

    val alreadyCached = allItems.count { it.hasUsableCover() }
    val targets = allItems.filterNot { it.hasUsableCover() }.sortedBy { it.code }
    if (targets.isEmpty()) {
      return Result.success(
        workDataOf(
          KEY_DONE to 0,
          KEY_TOTAL to 0,
          JavBatchScheduler.KEY_ALREADY_CACHED to alreadyCached,
          JavBatchScheduler.KEY_REASON to JavBatchScheduler.REASON_ALL_CACHED,
        ),
      )
    }

    var success = 0
    var failed = 0
    var noCover = 0
    publishProgress(0, targets.size, "covers")
    targets.forEachIndexed { index, original ->
      if (isStopped) return Result.failure()
      var item = original
      if (!item.hasUsableCover() && item.coverUrl.isNullOrBlank()) {
        val scraped = runCatching { metadataRepository.scrape(item.code, force = true) }.getOrNull()
        if (scraped != null) item = scraped.item
      }

      if (item.hasUsableCover()) {
        success++
      } else {
        val coverUrl = item.coverUrl
        if (coverUrl.isNullOrBlank()) {
          noCover++
        } else {
          val headers = if (item.source == "JavDB" || item.source == "JavDB+JavBus") {
            javDbSource.coverHeaders()
          } else {
            javBusSource.coverHeaders()
          }
          val localPath = runCatching {
            artworkStore.downloadToLocal(coverUrl, headers, item.code)
          }.getOrNull()
          if (localPath != null) {
            javDao.upsertItem(
              item.copy(
                localCoverPath = localPath,
                status = if (item.title.isNotBlank()) JavItemEntity.STATUS_OK else item.status,
                updatedAt = System.currentTimeMillis(),
              ),
            )
            success++
          } else {
            failed++
          }
        }
      }
      val done = index + 1
      if (done == targets.size || done % 3 == 0) {
        publishProgress(done, targets.size, item.code)
      }
    }
    val reason = if (success == 0 && failed == 0 && noCover > 0) {
      JavBatchScheduler.REASON_NO_COVER_URL
    } else {
      ""
    }
    return Result.success(
      workDataOf(
        KEY_DONE to targets.size,
        KEY_TOTAL to targets.size,
        JavBatchScheduler.KEY_SUCCESS to success,
        JavBatchScheduler.KEY_FAILED to failed,
        JavBatchScheduler.KEY_NO_COVER to noCover,
        JavBatchScheduler.KEY_ALREADY_CACHED to alreadyCached,
        JavBatchScheduler.KEY_REASON to reason,
      ),
    )
  }

  private fun JavItemEntity.hasUsableCover(): Boolean =
    localCoverPath?.takeIf { it.isNotBlank() && File(it).isFile } != null ||
      manualCoverPath?.takeIf { it.isNotBlank() && File(it).isFile } != null

  private suspend fun publishProgress(done: Int, total: Int, phase: String) {
    val data = workDataOf(KEY_DONE to done, KEY_TOTAL to total, "phase" to phase)
    setProgress(data)
    try {
      setForeground(foregroundInfo(done, total, phase))
    } catch (_: Exception) {
    }
  }

  private fun foregroundInfo(done: Int, total: Int, phase: String): ForegroundInfo {
    createChannel()
    val intent = Intent(applicationContext, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
      applicationContext,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle(applicationContext.getString(R.string.jav_batch_notification_title))
      .setContentText(applicationContext.getString(R.string.jav_batch_notification_text, done, total))
      .setSubText(phase)
      .setOnlyAlertOnce(true)
      .setOngoing(true)
      .setProgress(if (total > 0) total else 0, done, total <= 0)
      .setContentIntent(pendingIntent)
      .build()
    return ForegroundInfo(NOTIFICATION_ID, notification)
  }

  private fun createChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
    manager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ID,
        applicationContext.getString(R.string.jav_batch_notification_channel),
        NotificationManager.IMPORTANCE_LOW,
      ),
    )
  }
}
