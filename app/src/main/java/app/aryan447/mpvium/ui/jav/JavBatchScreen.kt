package app.aryan447.mpvium.ui.jav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.aryan447.mpvium.R
import app.aryan447.mpvium.domain.jav.JavBatchFilter
import app.aryan447.mpvium.domain.jav.JavBatchScheduler
import app.aryan447.mpvium.presentation.Screen
import app.aryan447.mpvium.presentation.components.ExpressiveChoiceRow
import app.aryan447.mpvium.ui.preferences.PreferenceCard
import app.aryan447.mpvium.ui.preferences.PreferenceSectionHeader
import app.aryan447.mpvium.ui.preferences.SettingsTopBar
import kotlinx.serialization.Serializable

@Serializable
object JavBatchScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context) }
    val scrapeInfos by workManager.getWorkInfosForUniqueWorkFlow(JavBatchScheduler.UNIQUE_SCRAPE)
      .collectAsState(initial = emptyList())
    val prefetchInfos by workManager.getWorkInfosForUniqueWorkFlow(JavBatchScheduler.UNIQUE_PREFETCH)
      .collectAsState(initial = emptyList())
    val active = (scrapeInfos + prefetchInfos).firstOrNull { !it.state.isFinished }
    val lastScrape = scrapeInfos.lastOrNull()
    val lastPrefetch = prefetchInfos.lastOrNull()
    var filterName by rememberSaveable { mutableStateOf(JavBatchFilter.MISSING.name) }
    val filter = runCatching { JavBatchFilter.valueOf(filterName) }.getOrDefault(JavBatchFilter.MISSING)
    val labels = mapOf(
      JavBatchFilter.ALL to stringResource(R.string.jav_batch_filter_all),
      JavBatchFilter.MISSING to stringResource(R.string.jav_batch_filter_missing),
      JavBatchFilter.FAILED to stringResource(R.string.jav_batch_filter_failed),
      JavBatchFilter.STALE to stringResource(R.string.jav_batch_filter_stale),
    )
    val progress = active?.progress
    val done = progress?.getInt("done", 0) ?: 0
    val total = progress?.getInt("total", 0) ?: 0
    val phase = progress?.getString("phase")

    Scaffold(
      containerColor = androidx.compose.ui.graphics.Color.Transparent,
      topBar = { SettingsTopBar(title = stringResource(R.string.jav_batch_title)) },
    ) { padding ->
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        contentPadding = PaddingValues(bottom = 32.dp),
      ) {
        item {
          PreferenceSectionHeader(title = stringResource(R.string.jav_batch_filter_title))
        }
        item {
          PreferenceCard {
            ExpressiveChoiceRow(
              options = JavBatchFilter.entries,
              selected = filter,
              labelOf = { labels[it].orEmpty() },
              onSelect = { filterName = it.name },
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
          }
        }

        item {
          Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = { JavBatchScheduler.enqueueScrape(context, filter) },
              enabled = active == null,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(stringResource(R.string.jav_batch_start_scrape))
            }
            FilledTonalButton(
              onClick = { JavBatchScheduler.enqueuePrefetch(context) },
              enabled = active == null,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(stringResource(R.string.jav_batch_start_prefetch))
            }
            if (active != null) {
              OutlinedButton(
                onClick = {
                  workManager.cancelUniqueWork(JavBatchScheduler.UNIQUE_SCRAPE)
                  workManager.cancelUniqueWork(JavBatchScheduler.UNIQUE_PREFETCH)
                },
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(stringResource(R.string.jav_batch_cancel))
              }
            }
          }
        }

        if (active == null) {
          lastScrape?.let { info ->
            item { BatchResultCard(title = stringResource(R.string.jav_batch_start_scrape), info = info, scrape = true) }
          }
          lastPrefetch?.let { info ->
            item { BatchResultCard(title = stringResource(R.string.jav_batch_start_prefetch), info = info, scrape = false) }
          }
        }

        if (active != null) {
          item {
            PreferenceCard {
              Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                LinearProgressIndicator(
                  progress = { if (total > 0) done.toFloat() / total else 0f },
                  modifier = Modifier.fillMaxWidth(),
                )
                Text(
                  text = if (total > 0) {
                    stringResource(R.string.jav_batch_progress, done, total)
                  } else {
                    stringResource(R.string.jav_batch_running)
                  },
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.padding(top = 8.dp),
                )
                if (!phase.isNullOrBlank()) {
                  Text(
                    text = stringResource(R.string.jav_batch_current, phase),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BatchResultCard(
  title: String,
  info: WorkInfo,
  scrape: Boolean,
) {
  val message = when (info.state) {
    WorkInfo.State.SUCCEEDED -> {
      if (scrape) {
        stringResource(
          R.string.jav_batch_scrape_result,
          info.outputData.getInt(JavBatchScheduler.KEY_SUCCESS, 0),
          info.outputData.getInt(JavBatchScheduler.KEY_FAILED, 0),
        )
      } else {
        val total = info.outputData.getInt(JavBatchScheduler.KEY_TOTAL, 0)
        if (total == 0) {
          when (info.outputData.getString(JavBatchScheduler.KEY_REASON)) {
            JavBatchScheduler.REASON_ALL_CACHED -> stringResource(R.string.jav_batch_all_cached)
            JavBatchScheduler.REASON_NO_METADATA -> stringResource(R.string.jav_batch_no_metadata)
            JavBatchScheduler.REASON_NO_COVER_URL -> stringResource(R.string.jav_batch_no_cover_url)
            JavBatchScheduler.REASON_NO_ITEMS -> stringResource(R.string.jav_batch_no_items)
            else -> stringResource(R.string.jav_batch_no_cover_url)
          }
        } else {
          stringResource(
            R.string.jav_batch_cover_result,
            info.outputData.getInt(JavBatchScheduler.KEY_SUCCESS, 0),
            info.outputData.getInt(JavBatchScheduler.KEY_FAILED, 0),
            info.outputData.getInt(JavBatchScheduler.KEY_NO_COVER, 0),
            info.outputData.getInt(JavBatchScheduler.KEY_ALREADY_CACHED, 0),
          )
        }
      }
    }
    WorkInfo.State.FAILED -> stringResource(
      R.string.jav_batch_failed,
      info.outputData.getString(JavBatchScheduler.KEY_ERROR) ?: "",
    )
    WorkInfo.State.CANCELLED -> stringResource(R.string.jav_batch_cancelled)
    else -> null
  }

  if (message != null) {
    PreferenceCard {
      Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp),
        )
      }
    }
  }
}
