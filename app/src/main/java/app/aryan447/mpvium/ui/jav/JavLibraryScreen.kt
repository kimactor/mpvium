package app.aryan447.mpvium.ui.jav

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState as collectState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.aryan447.mpvium.R
import app.aryan447.mpvium.domain.jav.JavLibraryItem
import app.aryan447.mpvium.preferences.JavPreferences
import app.aryan447.mpvium.preferences.preference.collectAsState
import app.aryan447.mpvium.presentation.Screen
import app.aryan447.mpvium.ui.browser.LocalNavigationBarHeight
import app.aryan447.mpvium.ui.browser.states.EmptyState
import app.aryan447.mpvium.ui.browser.states.LoadingState
import app.aryan447.mpvium.ui.preferences.SettingsTopBar
import app.aryan447.mpvium.ui.streaming.components.StreamingImage
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
    val navigationBarHeight = LocalNavigationBarHeight.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val columns = if (isTablet) 4 else 3
    val vm: JavLibraryViewModel = viewModel(factory = JavLibraryViewModel.factory(context.applicationContext as android.app.Application))
    val state by vm.uiState.collectState()

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

    Scaffold(
      containerColor = Color.Transparent,
      topBar = {
        SettingsTopBar(
          title = stringResource(R.string.jav_library_title),
          actions = {
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
        else -> LazyVerticalGrid(
          columns = GridCells.Fixed(columns),
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
              Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 4.dp),
              )
            }
          }
          items(state.items, key = { it.file.path }) { item ->
            JavPosterCard(
              item = item,
              onClick = { backstack.add(JavDetailScreen(item.code)) },
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JavPosterCard(
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
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(2f / 3f)
        .clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
      StreamingImage(
        url = item.coverUrl,
        fallbackVideo = item.video,
        isSeries = false,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              0.62f to Color.Transparent,
              1f to Color.Black.copy(alpha = 0.78f),
            ),
          ),
      )
      Text(
        text = item.code,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(8.dp),
      )
    }
    Text(
      text = item.title,
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
    )
    if (item.subtitle.isNotBlank()) {
      Text(
        text = item.subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 2.dp, end = 2.dp),
      )
    }
  }
}
