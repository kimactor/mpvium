package app.aryan447.mpvium.ui.jav

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState as collectState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.aryan447.mpvium.R
import app.aryan447.mpvium.domain.jav.JavLibraryItem
import app.aryan447.mpvium.preferences.JavPreferences
import app.aryan447.mpvium.preferences.MediaLayoutMode
import app.aryan447.mpvium.preferences.preference.collectAsState
import app.aryan447.mpvium.presentation.Screen
import app.aryan447.mpvium.ui.browser.LocalNavigationBarHeight
import app.aryan447.mpvium.ui.browser.components.MediaLayoutToggle
import app.aryan447.mpvium.ui.browser.dialogs.MarkAsDialog
import app.aryan447.mpvium.ui.browser.states.EmptyState
import app.aryan447.mpvium.ui.browser.states.LoadingState
import app.aryan447.mpvium.ui.preferences.SettingsTopBar
import app.aryan447.mpvium.ui.streaming.components.LibraryFilterBar
import app.aryan447.mpvium.ui.streaming.components.LibraryResultCount
import app.aryan447.mpvium.ui.streaming.components.SortOption
import app.aryan447.mpvium.ui.theme.glassSearchBarColors
import app.aryan447.mpvium.ui.utils.LocalBackStack
import app.aryan447.mpvium.utils.media.VideoWatchStatusOps
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object JavLibraryScreen : Screen {
  @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val preferences = koinInject<JavPreferences>()
    val libraryEnabled by preferences.libraryEnabled.collectAsState()
    val layoutMode by preferences.layoutMode.collectAsState()
    val navigationBarHeight = LocalNavigationBarHeight.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val gridColumns = when {
      isTablet -> if (isLandscape) 5 else 4
      isLandscape -> 3
      else -> 2
    }
    val vm: JavLibraryViewModel = viewModel(factory = JavLibraryViewModel.factory(context.applicationContext as android.app.Application))
    val state by vm.uiState.collectState()
    var showAgeGate by remember { mutableStateOf(!preferences.ageConfirmed.get()) }
    // Saved with the back stack entry so returning from a detail screen restores the exact row.
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var watchFilterOrdinal by rememberSaveable { mutableIntStateOf(0) }
    var sortOrdinal by rememberSaveable { mutableIntStateOf(JavLibrarySort.NAME.ordinal) }
    var selectedCodes by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var showMarkAsDialog by remember { mutableStateOf(false) }

    val watchFilter = JavWatchFilter.entries[watchFilterOrdinal.coerceIn(JavWatchFilter.entries.indices)]
    val sort = JavLibrarySort.entries[sortOrdinal.coerceIn(JavLibrarySort.entries.indices)]
    val filteredItems = remember(state.items, searchQuery, watchFilter, sort) {
      state.items.filterJavItems(searchQuery, watchFilter).sortedByJavSort(sort)
    }
    val selectionMode = selectedCodes.isNotEmpty()
    val allFilteredSelected = filteredItems.isNotEmpty() && filteredItems.all { selectedCodes.contains(it.code) }

    LaunchedEffect(libraryEnabled) {
      if (libraryEnabled) vm.refresh(forceScan = state.items.isEmpty())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
      var resumedOnce = false
      val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          if (resumedOnce) {
            vm.refresh()
          } else {
            resumedOnce = true
          }
        }
      }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = isSearching && !selectionMode) {
      isSearching = false
      searchQuery = ""
    }
    BackHandler(enabled = selectionMode) {
      selectedCodes = emptySet()
    }

    if (showAgeGate) {
      AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.jav_age_title)) },
        text = { Text(stringResource(R.string.jav_age_message)) },
        confirmButton = {
          TextButton(onClick = {
            preferences.ageConfirmed.set(true)
            showAgeGate = false
          }) {
            Text(stringResource(R.string.jav_age_confirm))
          }
        },
        dismissButton = {
          TextButton(onClick = {
            showAgeGate = false
            backstack.removeLastOrNull()
          }) {
            Text(stringResource(R.string.jav_age_decline))
          }
        },
      )
    }

    Scaffold(
      containerColor = Color.Transparent,
      topBar = {
        when {
          selectionMode -> SettingsTopBar(
            title = stringResource(R.string.selected_items, selectedCodes.size, state.items.size),
            onBack = { selectedCodes = emptySet() },
            actions = {
              IconButton(onClick = {
                selectedCodes = if (allFilteredSelected) {
                  emptySet()
                } else {
                  filteredItems.map { it.code }.toSet()
                }
              }) {
                Icon(
                  Icons.Filled.SelectAll,
                  contentDescription = stringResource(
                    if (allFilteredSelected) R.string.deselect_all else R.string.select_all,
                  ),
                )
              }
              IconButton(onClick = { showMarkAsDialog = true }) {
                Icon(
                  Icons.Filled.CheckCircle,
                  contentDescription = stringResource(R.string.jav_library_mark_as),
                )
              }
            },
          )
          isSearching -> SearchBar(
            inputField = {
              SearchBarDefaults.InputField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
                placeholder = { Text(stringResource(R.string.jav_library_search_placeholder)) },
                leadingIcon = {
                  Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                },
                trailingIcon = {
                  IconButton(onClick = {
                    isSearching = false
                    searchQuery = ""
                  }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                  }
                },
              )
            },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 6.dp),
            colors = glassSearchBarColors(),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
          ) {}
          else -> SettingsTopBar(
            title = stringResource(R.string.jav_library_title),
            actions = {
              IconButton(onClick = { isSearching = true }) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.jav_library_search))
              }
              MediaLayoutToggle(
                mode = layoutMode,
                onToggle = {
                  val next = if (layoutMode == MediaLayoutMode.GRID) MediaLayoutMode.LIST else MediaLayoutMode.GRID
                  preferences.layoutMode.set(next)
                },
              )
              IconButton(onClick = { backstack.add(JavBatchScreen) }) {
                Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.jav_batch_title))
              }
              IconButton(onClick = { backstack.add(JavSettingsScreen) }) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.jav_library_settings_title))
              }
            },
          )
        }
      },
    ) { padding ->
      when {
        !libraryEnabled -> EmptyState(
          icon = Icons.Filled.Settings,
          title = stringResource(R.string.jav_library_disabled_title),
          message = stringResource(R.string.jav_library_disabled_message),
          actionLabel = stringResource(R.string.jav_library_open_settings),
          onAction = { backstack.add(JavSettingsScreen) },
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        )
        state.isLoading -> LoadingState(
          title = stringResource(R.string.jav_library_loading),
          message = stringResource(R.string.jav_library_loading_message),
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        )
        state.items.isEmpty() -> EmptyState(
          icon = Icons.Filled.Settings,
          title = stringResource(R.string.jav_library_empty_title),
          message = state.error ?: stringResource(R.string.jav_library_empty_message),
          actionLabel = stringResource(R.string.jav_library_rescan),
          onAction = { vm.refresh(forceScan = true) },
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        )
        layoutMode == MediaLayoutMode.LIST -> LazyColumn(
          state = listState,
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = navigationBarHeight + 24.dp,
          ),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item(key = "filters") {
            JavFilterControls(
              watchFilter = watchFilter,
              onWatchFilterChange = { watchFilterOrdinal = it.ordinal },
              sort = sort,
              onSortChange = { sortOrdinal = it.ordinal },
              shownCount = filteredItems.size,
              totalCount = state.items.size,
            )
          }
          state.error?.let { error ->
            item(key = "error") {
              JavErrorText(error)
            }
          }
          if (filteredItems.isEmpty()) {
            item(key = "no_matches") {
              JavNoMatches(onShowAll = {
                searchQuery = ""
                watchFilterOrdinal = JavWatchFilter.ALL.ordinal
              })
            }
          }
          items(filteredItems, key = { it.file.path }) { item ->
            JavListRow(
              item = item,
              selectionMode = selectionMode,
              selected = selectedCodes.contains(item.code),
              onClick = {
                if (selectionMode) {
                  selectedCodes = selectedCodes.toggle(item.code)
                } else {
                  backstack.add(JavDetailScreen(item.code))
                }
              },
              onLongClick = {
                selectedCodes = selectedCodes.toggle(item.code)
              },
            )
          }
        }
        else -> LazyVerticalGrid(
          columns = GridCells.Fixed(gridColumns),
          state = gridState,
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = navigationBarHeight + 24.dp,
          ),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
            JavFilterControls(
              watchFilter = watchFilter,
              onWatchFilterChange = { watchFilterOrdinal = it.ordinal },
              sort = sort,
              onSortChange = { sortOrdinal = it.ordinal },
              shownCount = filteredItems.size,
              totalCount = state.items.size,
            )
          }
          state.error?.let { error ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "error") {
              JavErrorText(error)
            }
          }
          if (filteredItems.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "no_matches") {
              JavNoMatches(onShowAll = {
                searchQuery = ""
                watchFilterOrdinal = JavWatchFilter.ALL.ordinal
              })
            }
          }
          items(filteredItems, key = { it.file.path }) { item ->
            JavGridCard(
              item = item,
              selectionMode = selectionMode,
              selected = selectedCodes.contains(item.code),
              onClick = {
                if (selectionMode) {
                  selectedCodes = selectedCodes.toggle(item.code)
                } else {
                  backstack.add(JavDetailScreen(item.code))
                }
              },
              onLongClick = {
                selectedCodes = selectedCodes.toggle(item.code)
              },
            )
          }
        }
      }
    }

    MarkAsDialog(
      isOpen = showMarkAsDialog,
      selectedCount = selectedCodes.size,
      onDismiss = { showMarkAsDialog = false },
      onSelect = { status ->
        showMarkAsDialog = false
        val videos = state.items.filter { selectedCodes.contains(it.code) }.map { it.video }
        selectedCodes = emptySet()
        scope.launch {
          VideoWatchStatusOps.markVideos(videos, status)
          vm.refresh()
        }
      },
    )
  }
}

private fun Set<String>.toggle(code: String): Set<String> =
  if (contains(code)) this - code else this + code

@Composable
private fun JavFilterControls(
  watchFilter: JavWatchFilter,
  onWatchFilterChange: (JavWatchFilter) -> Unit,
  sort: JavLibrarySort,
  onSortChange: (JavLibrarySort) -> Unit,
  shownCount: Int,
  totalCount: Int,
) {
  val watchLabels = JavWatchFilter.entries.map { stringResource(it.labelRes) }
  val sortOptions = JavLibrarySort.entries.map { SortOption(it, stringResource(it.labelRes)) }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    LibraryFilterBar(
      options = JavWatchFilter.entries,
      selected = watchFilter,
      labelOf = { watchLabels[it.ordinal] },
      onSelect = onWatchFilterChange,
      sorts = sortOptions,
      selectedSort = sort,
      onSortSelect = onSortChange,
      modifier = Modifier.padding(horizontal = 4.dp),
    )
    LibraryResultCount(
      shown = shownCount,
      total = totalCount,
      noun = stringResource(
        if (totalCount == 1) R.string.jav_library_noun_video else R.string.jav_library_noun_videos,
      ),
    )
  }
}

@Composable
private fun JavNoMatches(onShowAll: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      text = stringResource(R.string.jav_library_no_matches),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = stringResource(R.string.jav_library_no_matches_message),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onShowAll) {
      Text(stringResource(R.string.jav_library_show_all))
    }
  }
}

@Composable
private fun JavErrorText(error: String) {
  Text(
    text = error,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.error,
    modifier = Modifier.padding(bottom = 4.dp),
  )
}

@Composable
private fun JavInfoColumn(
  item: JavLibraryItem,
  titleStyle: TextStyle,
  modifier: Modifier = Modifier,
) {
  val actresses = item.actresses.joinToString(" / ")
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = item.title,
      style = titleStyle,
      color = MaterialTheme.colorScheme.onSurface,
    )
    if (!item.title.equals(item.code, ignoreCase = true)) {
      Text(
        text = item.code,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
      )
    }
    if (actresses.isNotBlank()) {
      Text(
        text = stringResource(R.string.jav_library_actress_line, actresses),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun BoxScope.JavCoverBadges(
  item: JavLibraryItem,
  selectionMode: Boolean,
  selected: Boolean,
) {
  val facts = listOfNotNull(
    item.video.resolution.takeIf { it.isNotBlank() },
    item.video.size.takeIf { it > 0 }?.let { item.video.sizeFormatted },
  ).joinToString(" · ")
  if (facts.isNotBlank()) {
    JavBadge(
      text = facts,
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(6.dp),
    )
  }

  val watched = item.isWatched
  if (selectionMode) {
    Surface(
      shape = CircleShape,
      color = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f),
      border = if (selected) null else BorderStroke(1.5.dp, Color.White),
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(6.dp)
        .size(24.dp),
    ) {
      if (selected) {
        Icon(
          Icons.Filled.Check,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.padding(4.dp),
        )
      }
    }
  } else if (watched) {
    JavBadge(
      text = stringResource(R.string.jav_library_watched),
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(6.dp),
    )
  }

  val duration = item.video.durationFormatted.takeIf { it.isNotBlank() && it != "0s" }
    ?: item.metadata?.runtimeMinutes?.let { minutes ->
      if (minutes > 0) stringResource(R.string.jav_library_runtime_minutes, minutes) else null
    }
  if (duration != null) {
    JavBadge(
      text = duration,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(6.dp),
    )
  }

  if (item.hasChineseSubtitle) {
    JavBadge(
      text = "中",
      badgeDescription = stringResource(R.string.jav_library_subtitle_badge),
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(6.dp),
    )
  }

  val progress = item.progressPercentage
  if (progress > 0f && !item.isWatched) {
    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .height(3.dp)
        .background(Color.White.copy(alpha = 0.3f)),
    ) {
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .fillMaxWidth(progress)
          .background(MaterialTheme.colorScheme.primary),
      )
    }
  }
}

@Composable
private fun JavBadge(
  text: String,
  modifier: Modifier = Modifier,
  badgeDescription: String? = null,
) {
  Surface(
    color = Color.Black.copy(alpha = 0.65f),
    shape = RoundedCornerShape(4.dp),
    modifier = modifier,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
      ),
      color = Color.White,
      modifier = Modifier
        .then(
          badgeDescription?.let { description ->
            Modifier.semantics { contentDescription = description }
          } ?: Modifier,
        )
        .padding(horizontal = 5.dp, vertical = 2.dp),
    )
  }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun JavListRow(
  item: JavLibraryItem,
  selectionMode: Boolean,
  selected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val haptic = LocalHapticFeedback.current
  var coverRatio by remember(item.file.path, item.coverUrl) { mutableStateOf(3f / 2f) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .then(
        if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
        else Modifier,
      )
      .combinedClickable(
        onClick = {
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
          onClick()
        },
        onLongClick = {
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
          onLongClick()
        },
      )
      .padding(horizontal = 4.dp, vertical = 6.dp)
      .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(coverRatio),
    ) {
      JavCover(
        item = item,
        showBackdrop = false,
        contentScale = ContentScale.Crop,
        onImageLoaded = { bitmap ->
          if (bitmap.width > 0 && bitmap.height > 0) {
            coverRatio = (bitmap.width.toFloat() / bitmap.height).coerceIn(0.6f, 2.4f)
          }
        },
        modifier = Modifier.fillMaxSize(),
      )
      JavCoverBadges(item = item, selectionMode = selectionMode, selected = selected)
    }
    JavInfoColumn(
      item = item,
      titleStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
      modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JavGridCard(
  item: JavLibraryItem,
  selectionMode: Boolean,
  selected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val haptic = LocalHapticFeedback.current
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .then(
        if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
        else Modifier,
      )
      .combinedClickable(
        onClick = {
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
          onClick()
        },
        onLongClick = {
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
          onLongClick()
        },
      ),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(3f / 2f),
    ) {
      JavCover(
        item = item,
        modifier = Modifier.fillMaxSize(),
      )
      JavCoverBadges(item = item, selectionMode = selectionMode, selected = selected)
    }
    JavInfoColumn(
      item = item,
      titleStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
      modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
    )
  }
}
