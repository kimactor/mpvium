package app.aryan447.mpvium.ui.tmdb

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState as collectState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import app.aryan447.mpvium.R
import app.aryan447.mpvium.domain.tmdb.TmdbBatchScheduler
import app.aryan447.mpvium.preferences.TmdbPreferences
import app.aryan447.mpvium.preferences.preference.collectAsState
import app.aryan447.mpvium.presentation.Screen
import app.aryan447.mpvium.ui.preferences.PreferenceCard
import app.aryan447.mpvium.ui.preferences.PreferenceDivider
import app.aryan447.mpvium.ui.preferences.PreferenceSectionHeader
import app.aryan447.mpvium.ui.preferences.PreferenceRowIcon
import app.aryan447.mpvium.ui.preferences.SettingsTopBar
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.TextFieldPreference
import org.koin.compose.koinInject

@Serializable
object MetadataSettingsScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val preferences = koinInject<TmdbPreferences>()
    val workManager = remember { WorkManager.getInstance(context) }
    val infos by workManager.getWorkInfosForUniqueWorkFlow(TmdbBatchScheduler.UNIQUE_WORK)
      .collectState(initial = emptyList())
    val active = infos.firstOrNull { !it.state.isFinished }
    val done = active?.progress?.getInt("done", 0) ?: 0
    val total = active?.progress?.getInt("total", 0) ?: 0

    Scaffold(
      containerColor = androidx.compose.ui.graphics.Color.Transparent,
      topBar = { SettingsTopBar(title = stringResource(R.string.metadata_title)) },
    ) { padding ->
      ProvidePreferenceLocals {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentPadding = PaddingValues(bottom = 32.dp),
        ) {
          item { PreferenceSectionHeader(title = stringResource(R.string.metadata_enrich_title)) }
          item {
            PreferenceCard {
              val apiKey by preferences.apiKey.collectAsState()
              TextFieldPreference(
                value = apiKey,
                onValueChange = { preferences.apiKey.set(it.trim()) },
                title = { Text(stringResource(R.string.metadata_tmdb_key_title)) },
                summary = {
                  Text(
                    stringResource(R.string.metadata_tmdb_key_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                },
                textToValue = { it },
                icon = { PreferenceRowIcon(icon = Icons.Outlined.Tune) },
                textField = { value, onValueChange, _ ->
                  TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.metadata_tmdb_key_placeholder)) },
                    singleLine = true,
                  )
                },
              )
              PreferenceDivider()
              Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                  text = stringResource(R.string.metadata_enrich_summary),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                  onClick = { TmdbBatchScheduler.enqueue(context) },
                  enabled = active == null,
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                ) {
                  Text(stringResource(R.string.metadata_enrich_start))
                }
                if (active != null) {
                  LinearProgressIndicator(
                    progress = { if (total > 0) done.toFloat() / total else 0f },
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(top = 12.dp),
                  )
                  Text(
                    text = stringResource(R.string.metadata_enrich_progress, done, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                  )
                  OutlinedButton(
                    onClick = { workManager.cancelUniqueWork(TmdbBatchScheduler.UNIQUE_WORK) },
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(top = 8.dp),
                  ) {
                    Text(stringResource(R.string.jav_batch_cancel))
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
