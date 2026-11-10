package app.aryan447.mpvium.ui.tmdb

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.aryan447.mpvium.R
import app.aryan447.mpvium.domain.media.model.Video
import app.aryan447.mpvium.domain.streaming.LibraryIndexRepository
import app.aryan447.mpvium.domain.streaming.MovieLibraryClassifier
import app.aryan447.mpvium.domain.streaming.StreamingMetadataRepository
import app.aryan447.mpvium.domain.streaming.model.LocalMovie
import app.aryan447.mpvium.domain.tmdb.TmdbClient
import app.aryan447.mpvium.domain.tmdb.TmdbCollection
import app.aryan447.mpvium.presentation.Screen
import app.aryan447.mpvium.ui.browser.states.EmptyState
import app.aryan447.mpvium.ui.browser.states.LoadingState
import app.aryan447.mpvium.ui.preferences.SettingsTopBar
import app.aryan447.mpvium.ui.streaming.components.StreamingImage
import app.aryan447.mpvium.ui.streaming.movies.MovieDetailScreen
import app.aryan447.mpvium.ui.utils.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

private data class CollectionGroup(
  val id: Int,
  val name: String,
  val posterUrl: String?,
  val count: Int,
)

@Serializable
object CollectionsScreen : Screen {
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val libraryRepository = koinInject<LibraryIndexRepository>()
    val metadataRepository = koinInject<StreamingMetadataRepository>()
    var groups by remember { mutableStateOf<List<CollectionGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
      isLoading = true
      groups = withContext(Dispatchers.IO) {
        val detected = libraryRepository.getLibrary()
        val enriched = detected.movies
          .filter { MovieLibraryClassifier.isMovieLibraryItem(it) }
          .map { metadataRepository.applyCachedMovie(it) }
        enriched
          .filter { it.collectionId != null }
          .groupBy { it.collectionId!! }
          .map { (id, movies) ->
            CollectionGroup(
              id = id,
              name = movies.firstOrNull()?.collectionName ?: "Collection",
              posterUrl = movies.firstOrNull()?.collectionPosterUrl,
              count = movies.size,
            )
          }
          .sortedBy { it.name.lowercase() }
      }
      isLoading = false
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
      var resumedOnce = false
      val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          if (resumedOnce) reload++ else resumedOnce = true
        }
      }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
      containerColor = androidx.compose.ui.graphics.Color.Transparent,
      topBar = { SettingsTopBar(title = stringResource(R.string.collections_title)) },
    ) { padding ->
      when {
        isLoading -> LoadingState(
          title = stringResource(R.string.collections_title),
          message = stringResource(R.string.collections_subtitle),
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        )
        groups.isEmpty() -> EmptyState(
          icon = androidx.compose.material.icons.Icons.Filled.Collections,
          title = stringResource(R.string.collections_empty_title),
          message = stringResource(R.string.collections_empty_message),
          actionLabel = stringResource(R.string.metadata_title),
          onAction = { backstack.add(MetadataSettingsScreen) },
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        )
        else -> LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(groups, key = { it.id }) { group ->
            CollectionCard(
              group = group,
              onClick = { backstack.add(CollectionDetailScreen(group.id, group.name)) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CollectionCard(
  group: CollectionGroup,
  onClick: () -> Unit,
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        modifier = Modifier
          .width(64.dp)
          .height(96.dp)
          .clip(RoundedCornerShape(10.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
      ) {
        StreamingImage(
          url = group.posterUrl,
          isSeries = false,
          modifier = Modifier.fillMaxSize(),
        )
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = group.name,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = stringResource(R.string.collections_owned_count, group.count),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp),
        )
      }
    }
  }
}

@Serializable
data class CollectionDetailScreen(
  val collectionId: Int,
  val collectionName: String,
) : Screen {
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val libraryRepository = koinInject<LibraryIndexRepository>()
    val metadataRepository = koinInject<StreamingMetadataRepository>()
    val tmdbClient = koinInject<TmdbClient>()
    var owned by remember { mutableStateOf<List<LocalMovie>>(emptyList()) }
    var collection by remember { mutableStateOf<TmdbCollection?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(collectionId) {
      val result = withContext(Dispatchers.IO) {
        val detected = libraryRepository.getLibrary()
        val movies = detected.movies
          .filter { MovieLibraryClassifier.isMovieLibraryItem(it) }
          .map { metadataRepository.applyCachedMovie(it) }
          .filter { it.collectionId == collectionId }
        movies to tmdbClient.collection(collectionId)
      }
      owned = result.first
      collection = result.second
      isLoading = false
    }

    val title = collection?.name ?: collectionName
    Scaffold(
      containerColor = androidx.compose.ui.graphics.Color.Transparent,
      topBar = { SettingsTopBar(title = title) },
    ) { padding ->
      if (isLoading) {
        LoadingState(
          title = stringResource(R.string.collections_title),
          message = stringResource(R.string.collections_subtitle),
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        )
      } else {
        val parts = collection?.parts.orEmpty()
        val ownedByTmdbId = owned.mapNotNull { movie -> movie.tmdbId?.let { it to movie } }.toMap()
        val rows: List<Pair<app.aryan447.mpvium.domain.tmdb.TmdbCollectionPart, LocalMovie?>> = if (parts.isNotEmpty()) {
          parts.map { part -> part to ownedByTmdbId[part.id] }
        } else {
          owned.map { movie ->
            app.aryan447.mpvium.domain.tmdb.TmdbCollectionPart(
              id = movie.tmdbId ?: 0,
              title = movie.title,
              releaseDate = movie.year,
              posterPath = movie.posterUrl,
              overview = movie.overview,
            ) to movie
          }
        }

        if (rows.isEmpty()) {
          EmptyState(
            icon = androidx.compose.material.icons.Icons.Filled.Collections,
            title = stringResource(R.string.collections_not_found),
            message = stringResource(R.string.collections_empty_message),
            modifier = Modifier
              .fillMaxSize()
              .padding(padding),
          )
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(rows.size) { index ->
              val (part, movie) = rows[index]
              CollectionPartRow(
                title = movie?.title ?: part.title,
                subtitle = if (movie != null) {
                  stringResource(R.string.collections_in_library)
                } else {
                  stringResource(R.string.collections_missing)
                },
                posterUrl = movie?.posterUrl ?: tmdbClient.imageUrl(part.posterPath),
                video = movie?.video,
                onClick = {
                  movie?.let { backstack.add(MovieDetailScreen(it.video.id, it.title)) }
                },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CollectionPartRow(
  title: String,
  subtitle: String,
  posterUrl: String?,
  video: Video?,
  onClick: () -> Unit,
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .clickable(enabled = video != null, onClick = onClick),
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Row(
      modifier = Modifier.padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        modifier = Modifier
          .size(52.dp, 78.dp)
          .clip(RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
      ) {
        StreamingImage(
          url = posterUrl,
          isSeries = false,
          modifier = Modifier.fillMaxSize(),
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
