package app.aryan447.mpvium.ui.jav

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import app.aryan447.mpvium.database.entities.JavItemEntity
import app.aryan447.mpvium.domain.jav.JavLibraryItem
import app.aryan447.mpvium.domain.jav.JavLibraryRepository
import app.aryan447.mpvium.domain.jav.JavMetadataRepository
import app.aryan447.mpvium.domain.jav.JavSubtitleFile
import app.aryan447.mpvium.domain.jav.JavSubtitleRepository
import app.aryan447.mpvium.domain.jav.SubtitleCatCandidate
import app.aryan447.mpvium.domain.jav.SubtitleCatSource
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
import kotlin.math.roundToInt

@Serializable
data class JavDetailScreen(
  val code: String,
) : Screen {
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val repository = koinInject<JavLibraryRepository>()
    val metadataRepository = koinInject<JavMetadataRepository>()
    val subtitleRepository = koinInject<JavSubtitleRepository>()
    val subtitleCatSource = koinInject<SubtitleCatSource>()
    val scope = rememberCoroutineScope()
    var item by remember(code) { mutableStateOf<JavLibraryItem?>(null) }
    var isLoading by remember(code) { mutableStateOf(true) }
    var isScraping by remember(code) { mutableStateOf(false) }
    var error by remember(code) { mutableStateOf<String?>(null) }
    var subtitles by remember(code) { mutableStateOf<List<JavSubtitleFile>>(emptyList()) }
    var subtitleMessage by remember(code) { mutableStateOf<String?>(null) }
    var isWorkingSubtitles by remember(code) { mutableStateOf(false) }
    var searchingSubtitles by remember(code) { mutableStateOf(false) }
    var showSubtitleSearch by remember(code) { mutableStateOf(false) }
    var candidates by remember(code) { mutableStateOf<List<SubtitleCatCandidate>>(emptyList()) }

    suspend fun loadItem() {
      item = withContext(Dispatchers.IO) {
        runCatching { repository.getItem(code) }.getOrNull()
      }
      isLoading = false
    }

    suspend fun loadSubtitles() {
      subtitles = withContext(Dispatchers.IO) {
        runCatching { subtitleRepository.listForVideo(code, item?.video?.path) }.getOrDefault(emptyList())
      }
    }

    LaunchedEffect(code) {
      loadItem()
      loadSubtitles()
    }

    fun pickedFileName(uri: Uri): String =
      runCatching {
        context.contentResolver
          .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
          ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
      }.getOrNull() ?: uri.lastPathSegment ?: "subtitle.srt"

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      scope.launch {
        isWorkingSubtitles = true
        subtitleMessage = null
        val result = withContext(Dispatchers.IO) {
          runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
              ?: error("Could not read the file")
            subtitleRepository.importFile(code, pickedFileName(uri), bytes).getOrThrow()
          }
        }
        isWorkingSubtitles = false
        subtitleMessage = result.fold(
          onSuccess = { context.getString(R.string.jav_library_subtitle_imported, it.name) },
          onFailure = { context.getString(R.string.jav_library_subtitle_import_failed) },
        )
        loadSubtitles()
      }
    }

    fun searchSubtitles() {
      if (searchingSubtitles) return
      scope.launch {
        searchingSubtitles = true
        subtitleMessage = null
        val result = subtitleCatSource.searchChinese(code)
        searchingSubtitles = false
        candidates = result.getOrDefault(emptyList())
        showSubtitleSearch = true
        if (result.isFailure) {
          subtitleMessage = context.getString(R.string.jav_library_subtitle_search_failed)
        }
      }
    }

    fun downloadSubtitle(candidate: SubtitleCatCandidate, traditional: Boolean) {
      scope.launch {
        isWorkingSubtitles = true
        subtitleMessage = null
        val result = subtitleCatSource.download(candidate, traditional).mapCatching { bytes ->
          subtitleRepository
            .saveDownload(code, if (traditional) "zh-TW" else "zh-CN", bytes)
            .getOrThrow()
        }
        isWorkingSubtitles = false
        subtitleMessage = result.fold(
          onSuccess = { context.getString(R.string.jav_library_subtitle_saved, it.name) },
          onFailure = { context.getString(R.string.jav_library_subtitle_download_failed) },
        )
        if (result.isSuccess) showSubtitleSearch = false
        loadSubtitles()
      }
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
          val hasTags = current.actresses.isNotEmpty() ||
            current.genres.isNotEmpty() ||
            listOfNotNull(
              current.metadata?.studio,
              current.metadata?.label,
              current.metadata?.series,
              current.metadata?.director,
            ).any { it.isNotBlank() }
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
                    onClick = { MediaUtils.playFile(current.video, context, "jav_library", mediaCode = current.code) },
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

            current.metadata?.overview?.takeIf { it.isNotBlank() }?.let { overview ->
              item {
                PreferenceCard {
                  Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                      text = stringResource(R.string.jav_library_overview),
                      style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                      text = overview,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(top = 6.dp),
                    )
                  }
                }
              }
            }

            if (hasTags) {
              item {
                JavTagsCard(item = current)
              }
            }

            item {
              JavFileInfoCard(item = current)
            }

            item {
              PreferenceCard {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = stringResource(R.string.jav_library_subtitles),
                      style = MaterialTheme.typography.titleSmall,
                      modifier = Modifier.weight(1f),
                    )
                    if (isWorkingSubtitles) {
                      CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                  }
                  if (subtitles.isEmpty()) {
                    Text(
                      text = stringResource(R.string.jav_library_subtitle_none),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(top = 4.dp),
                    )
                  } else {
                    subtitles.forEach { subtitle ->
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                          text = subtitle.file.name,
                          style = MaterialTheme.typography.bodyMedium,
                          modifier = Modifier.weight(1f),
                        )
                        if (subtitle.chinese) {
                          Text(
                            text = stringResource(R.string.jav_library_subtitle_chinese),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 4.dp),
                          )
                        }
                        if (subtitle.managed) {
                          IconButton(onClick = {
                            scope.launch {
                              subtitleRepository.delete(subtitle.file)
                              loadSubtitles()
                            }
                          }) {
                            Icon(
                              Icons.Filled.Delete,
                              contentDescription = stringResource(R.string.jav_library_subtitle_delete),
                            )
                          }
                        }
                      }
                    }
                  }
                  Row(
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    FilledTonalButton(
                      onClick = { importLauncher.launch(arrayOf("*/*")) },
                      modifier = Modifier.weight(1f),
                    ) {
                      Icon(Icons.Filled.Download, contentDescription = null)
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(stringResource(R.string.jav_library_subtitle_import))
                    }
                    OutlinedButton(
                      onClick = { searchSubtitles() },
                      enabled = !searchingSubtitles,
                      modifier = Modifier.weight(1f),
                    ) {
                      Icon(Icons.Filled.Search, contentDescription = null)
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        stringResource(
                          if (searchingSubtitles) {
                            R.string.jav_library_subtitle_searching
                          } else {
                            R.string.jav_library_subtitle_search
                          },
                        ),
                      )
                    }
                  }
                  subtitleMessage?.let { message ->
                    Text(
                      text = message,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(bottom = 6.dp),
                    )
                  }
                }
              }
            }

            item {
              PreferenceCard {
                val metadata = current.metadata
                JavInfoRow(stringResource(R.string.jav_library_status), javMetadataStatusLabel(metadata?.status))
                JavInfoRow(stringResource(R.string.jav_library_source), metadata?.source)
                JavInfoRow(stringResource(R.string.jav_library_last_error), metadata?.lastError)
                JavInfoRow(stringResource(R.string.jav_library_release_date), metadata?.releaseDate)
                JavInfoRow(
                  stringResource(R.string.jav_library_runtime),
                  metadata?.runtimeMinutes?.let { stringResource(R.string.jav_library_runtime_minutes, it) },
                )
              }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
          }
        }
      }
    }

    if (showSubtitleSearch) {
      AlertDialog(
        onDismissRequest = { showSubtitleSearch = false },
        title = { Text(stringResource(R.string.jav_library_subtitle_search)) },
        text = {
          if (candidates.isEmpty()) {
            Text(stringResource(R.string.jav_library_subtitle_no_results))
          } else {
            LazyColumn(
              modifier = Modifier.heightIn(max = 320.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              items(candidates, key = { it.detailUrl }) { candidate ->
                Column(modifier = Modifier.fillMaxWidth()) {
                  Text(
                    text = candidate.title,
                    style = MaterialTheme.typography.bodyMedium,
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    candidate.simplifiedUrl?.let {
                      TextButton(onClick = { downloadSubtitle(candidate, traditional = false) }) {
                        Text(stringResource(R.string.jav_library_subtitle_chinese_simplified))
                      }
                    }
                    candidate.traditionalUrl?.let {
                      TextButton(onClick = { downloadSubtitle(candidate, traditional = true) }) {
                        Text(stringResource(R.string.jav_library_subtitle_chinese_traditional))
                      }
                    }
                  }
                }
              }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { showSubtitleSearch = false }) {
            Text(stringResource(R.string.jav_library_subtitle_close))
          }
        },
      )
    }
  }
}

@Composable
private fun JavFileInfoCard(item: JavLibraryItem) {
  val resume = if (!item.isWatched && item.resumePositionMs >= 10_000L) {
    stringResource(
      R.string.jav_library_resume_at,
      DateUtils.formatElapsedTime(item.resumePositionMs / 1000),
    )
  } else {
    null
  }
  val watchStatus = when {
    item.isWatched -> stringResource(R.string.jav_library_watched)
    item.progressPercentage > 0f -> stringResource(
      R.string.jav_library_progress,
      (item.progressPercentage * 100).roundToInt(),
    )
    else -> stringResource(R.string.jav_library_filter_unwatched)
  }.let { status -> listOfNotNull(status, resume).joinToString(" · ") }

  PreferenceCard {
    JavInfoRow(stringResource(R.string.jav_library_watch_status), watchStatus)
    JavInfoRow(stringResource(R.string.jav_library_file_name), item.video.displayName)
    JavInfoRow(
      stringResource(R.string.jav_library_file_size),
      item.video.sizeFormatted.takeIf { item.video.size > 0 },
    )
    JavInfoRow(
      stringResource(R.string.jav_library_resolution),
      item.video.resolution.takeIf { it.isNotBlank() },
    )
    JavInfoRow(
      stringResource(R.string.jav_library_duration),
      item.video.durationFormatted.takeIf { it.isNotBlank() && it != "0s" }
        ?: item.metadata?.runtimeMinutes?.let { stringResource(R.string.jav_library_runtime_minutes, it) },
    )
    JavInfoRow(
      stringResource(R.string.jav_library_file_path),
      item.video.path.takeIf { it.isNotBlank() },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JavTagsCard(item: JavLibraryItem) {
  val groups = mutableListOf<Pair<String, List<String>>>()
  if (item.actresses.isNotEmpty()) {
    groups += stringResource(R.string.jav_library_actresses) to item.actresses
  }
  item.metadata?.studio?.takeIf { it.isNotBlank() }?.let {
    groups += stringResource(R.string.jav_library_studio) to listOf(it)
  }
  item.metadata?.label?.takeIf { it.isNotBlank() }?.let {
    groups += stringResource(R.string.jav_library_label) to listOf(it)
  }
  item.metadata?.series?.takeIf { it.isNotBlank() }?.let {
    groups += stringResource(R.string.jav_library_series) to listOf(it)
  }
  item.metadata?.director?.takeIf { it.isNotBlank() }?.let {
    groups += stringResource(R.string.jav_library_director) to listOf(it)
  }
  if (item.genres.isNotEmpty()) {
    groups += stringResource(R.string.jav_library_genres) to item.genres
  }

  PreferenceCard {
    Column(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = stringResource(R.string.jav_library_tags),
        style = MaterialTheme.typography.titleSmall,
      )
      groups.forEach { (label, values) ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            values.forEach { value -> JavTagChip(value) }
          }
        }
      }
    }
  }
}

@Composable
private fun JavTagChip(text: String) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = RoundedCornerShape(8.dp),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
    )
  }
}

@Composable
private fun javMetadataStatusLabel(status: String?): String? = when (status) {
  null, "" -> null
  JavItemEntity.STATUS_PENDING -> stringResource(R.string.jav_library_metadata_pending)
  JavItemEntity.STATUS_OK -> stringResource(R.string.jav_library_metadata_ok)
  JavItemEntity.STATUS_PARTIAL -> stringResource(R.string.jav_library_metadata_partial)
  JavItemEntity.STATUS_FAILED -> stringResource(R.string.jav_library_metadata_failed)
  JavItemEntity.STATUS_NOT_FOUND -> stringResource(R.string.jav_library_metadata_not_found)
  else -> status
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
