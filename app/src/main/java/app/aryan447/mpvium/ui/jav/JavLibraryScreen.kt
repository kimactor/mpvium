package app.aryan447.mpvium.ui.jav

import android.content.res.Configuration
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState as collectState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
import app.aryan447.mpvium.ui.browser.states.EmptyState
import app.aryan447.mpvium.ui.browser.states.LoadingState
import app.aryan447.mpvium.ui.preferences.SettingsTopBar
import app.aryan447.mpvium.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object JavLibraryScreen : Screen {
  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
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

    LaunchedEffect(libraryEnabled) {
      if (libraryEnabled) vm.refresh(forceScan = true)
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
        SettingsTopBar(
          title = stringResource(R.string.jav_library_title),
          actions = {
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
          state.error?.let { error ->
            item(key = "error") {
              JavErrorText(error)
            }
          }
          items(state.items, key = { it.file.path }) { item ->
            JavListRow(
              item = item,
              onClick = { backstack.add(JavDetailScreen(item.code)) },
            )
          }
        }
        else -> LazyVerticalGrid(
          columns = GridCells.Fixed(gridColumns),
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
          state.error?.let { error ->
            item(span = { GridItemSpan(maxLineSpan) }) {
              JavErrorText(error)
            }
          }
          items(state.items, key = { it.file.path }) { item ->
            JavGridCard(
              item = item,
              onClick = { backstack.add(JavDetailScreen(item.code)) },
            )
          }
        }
      }
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun JavListRow(
  item: JavLibraryItem,
  onClick: () -> Unit,
) {
  val haptic = LocalHapticFeedback.current
  var coverRatio by remember(item.file.path, item.coverUrl) { mutableStateOf(3f / 2f) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .combinedClickable(
        onClick = {
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
          onClick()
        },
      )
      .padding(horizontal = 4.dp, vertical = 6.dp)
      .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()),
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
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(coverRatio),
    )
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
  onClick: () -> Unit,
) {
  val haptic = LocalHapticFeedback.current
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .combinedClickable(
        onClick = {
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
          onClick()
        },
      ),
  ) {
    JavCover(
      item = item,
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(3f / 2f),
    )
    JavInfoColumn(
      item = item,
      titleStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
      modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
    )
  }
}
