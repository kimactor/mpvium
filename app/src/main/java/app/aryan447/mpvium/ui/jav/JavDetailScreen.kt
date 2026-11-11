package app.aryan447.mpvium.ui.jav

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aryan447.mpvium.R
import app.aryan447.mpvium.domain.jav.JavLibraryItem
import app.aryan447.mpvium.domain.jav.JavLibraryRepository
import app.aryan447.mpvium.domain.jav.JavMetadataRepository
import app.aryan447.mpvium.presentation.Screen
import app.aryan447.mpvium.ui.browser.states.EmptyState
import app.aryan447.mpvium.ui.browser.states.LoadingState
import app.aryan447.mpvium.ui.preferences.PreferenceCard
import app.aryan447.mpvium.ui.preferences.SettingsTopBar
import app.aryan447.mpvium.utils.media.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data class JavDetailScreen(
  val code: String,
) : Screen {
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val repository = koinInject<JavLibraryRepository>()
    val metadataRepository = koinInject<JavMetadataRepository>()
    val scope = rememberCoroutineScope()
    var item by remember(code) { mutableStateOf<JavLibraryItem?>(null) }
    var isLoading by remember(code) { mutableStateOf(true) }
    var isScraping by remember(code) { mutableStateOf(false) }
    var error by remember(code) { mutableStateOf<String?>(null) }

    suspend fun loadItem() {
      item = withContext(Dispatchers.IO) {
        runCatching { repository.getItem(code) }.getOrNull()
      }
      isLoading = false
    }

    LaunchedEffect(code) {
      loadItem()
    }

    fun scrape() {
      if (isScraping) return
      scope.launch {
        isScraping = true
        error = null
        val result = withContext(Dispatchers.IO) {
          runCatching { metadataRepository.scrape(code, force = true) }
        }
        error = result.exceptionOrNull()?.message ?: result.getOrNull()?.errorMessage
        loadItem()
        isScraping = false
      }
    }

    Scaffold(
      containerColor = Color.Transparent,
      topBar = {
        SettingsTopBar(
          title = item?.title ?: code,
          actions = {
            IconButton(onClick = { scope.launch { isLoading = true; loadItem() } }) {
              Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.jav_library_refresh))
            }
          },
        )
      },
    ) { padding ->
      when {
        isLoading -> LoadingState(
          title = stringResource(R.string.jav_library_loading),
          message = stringResource(R.string.jav_library_loading_message),
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        )
        item == null -> EmptyState(
          icon = Icons.Filled.Refresh,
          title = stringResource(R.string.jav_library_not_found_title),
          message = stringResource(R.string.jav_library_not_found_message),
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        )
        else -> {
          val current = item!!
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .padding(padding),
          ) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .aspectRatio(3f / 2f),
              ) {
                JavCover(
                  item = current,
                  shape = RoundedCornerShape(0.dp),
                  modifier = Modifier.fillMaxSize(),
                )
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .background(
                      Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background,
                      ),
                    ),
                )
              }
            }

            item {
              Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                  text = current.title,
                  style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                  text = current.code,
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(top = 4.dp),
                )
                if (current.subtitle.isNotBlank()) {
                  Text(
                    text = current.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                  )
                }

                Row(
                  modifier = Modifier.padding(top = 16.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Button(
                    onClick = { MediaUtils.playFile(current.video, context, "jav_library") },
                  ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.jav_library_play))
                  }
                  FilledTonalButton(onClick = ::scrape, enabled = !isScraping) {
                    if (isScraping) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                      )
                    } else {
                      Icon(Icons.Filled.Download, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      stringResource(
                        if (isScraping) R.string.jav_library_scraping else R.string.jav_library_scrape,
                      ),
                    )
                  }
                  current.metadata?.sourceUrl?.takeIf { it.isNotBlank() }?.let { sourceUrl ->
                    TextButton(onClick = {
                      runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl)))
                      }
                    }) {
                      Text(stringResource(R.string.jav_library_open_source))
                    }
                  }
                }

                error?.let { message ->
                  Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                  )
                }
              }
            }

            item {
              PreferenceCard {
                val metadata = current.metadata
                JavInfoRow(stringResource(R.string.jav_library_status), metadata?.status)
                JavInfoRow(stringResource(R.string.jav_library_source), metadata?.source)
                JavInfoRow(stringResource(R.string.jav_library_last_error), metadata?.lastError)
                JavInfoRow(stringResource(R.string.jav_library_release_date), metadata?.releaseDate)
                JavInfoRow(
                  stringResource(R.string.jav_library_runtime),
                  metadata?.runtimeMinutes?.let { stringResource(R.string.jav_library_runtime_minutes, it) },
                )
                JavInfoRow(stringResource(R.string.jav_library_studio), metadata?.studio)
                JavInfoRow(stringResource(R.string.jav_library_label), metadata?.label)
                JavInfoRow(stringResource(R.string.jav_library_series), metadata?.series)
                JavInfoRow(stringResource(R.string.jav_library_director), metadata?.director)
                JavInfoRow(
                  stringResource(R.string.jav_library_actresses),
                  current.actresses.joinToString(", ").takeIf { it.isNotBlank() },
                )
                JavInfoRow(
                  stringResource(R.string.jav_library_genres),
                  current.genres.joinToString(", ").takeIf { it.isNotBlank() },
                )
              }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
          }
        }
      }
    }
  }
}

@Composable
private fun JavInfoRow(
  label: String,
  value: String?,
) {
  if (value.isNullOrBlank()) return
  Row(modifier = Modifier
    .fillMaxWidth()
    .padding(horizontal = 16.dp, vertical = 8.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.width(112.dp),
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.weight(1f),
    )
  }
}
