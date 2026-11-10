package app.aryan447.mpvium.domain.tmdb

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aryan447.mpvium.domain.streaming.LibraryIndexRepository
import app.aryan447.mpvium.domain.streaming.MovieLibraryClassifier
import app.aryan447.mpvium.domain.streaming.StreamingArtworkStore
import app.aryan447.mpvium.domain.streaming.StreamingMetadataRepository
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TmdbEnrichWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
  private val libraryIndexRepository: LibraryIndexRepository by inject()
  private val metadataRepository: StreamingMetadataRepository by inject()
  private val artworkStore: StreamingArtworkStore by inject()

  override suspend fun doWork(): Result {
    return try {
      val library = libraryIndexRepository.getLibrary(force = true)
      val movies = library.movies.filter { MovieLibraryClassifier.isMovieLibraryItem(it) }
      if (movies.isEmpty()) return Result.success(workDataOf("done" to 0, "total" to 0))

      setProgress(workDataOf("done" to 0, "total" to movies.size))
      movies.forEachIndexed { index, movie ->
        if (isStopped) return Result.failure()
        runCatching {
          val enriched = metadataRepository.enrichMovie(movie, forceRefresh = true)
          enriched.posterUrl?.let { artworkStore.loadImage(it) }
          enriched.backdropUrl?.let { artworkStore.loadImage(it) }
        }
        val done = index + 1
        if (done == movies.size || done % 2 == 0) {
          setProgress(workDataOf("done" to done, "total" to movies.size))
        }
      }
      Result.success(workDataOf("done" to movies.size, "total" to movies.size))
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      Result.failure(workDataOf("error" to (error.message ?: "TMDb enrichment failed")))
    }
  }
}
