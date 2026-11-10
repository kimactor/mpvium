package app.aryan447.mpvium.ui.jav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import app.aryan447.mpvium.R
import app.aryan447.mpvium.preferences.JavPreferences
import app.aryan447.mpvium.preferences.JavProxyType
import app.aryan447.mpvium.preferences.preference.collectAsState
import app.aryan447.mpvium.presentation.Screen
import app.aryan447.mpvium.ui.preferences.ExpressiveListPreference
import app.aryan447.mpvium.ui.preferences.HapticSwitchPreference
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
object JavSettingsScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val preferences = koinInject<JavPreferences>()

    Scaffold(
      containerColor = androidx.compose.ui.graphics.Color.Transparent,
      topBar = {
        SettingsTopBar(title = stringResource(R.string.jav_library_settings_title))
      },
    ) { padding ->
      ProvidePreferenceLocals {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentPadding = PaddingValues(bottom = 32.dp),
        ) {
          item { PreferenceSectionHeader(title = stringResource(R.string.jav_library_section_library)) }
          item {
            PreferenceCard {
              val enabled by preferences.libraryEnabled.collectAsState()
              HapticSwitchPreference(
                value = enabled,
                onValueChange = { preferences.libraryEnabled.set(it) },
                icon = { PreferenceRowIcon(icon = Icons.Outlined.Settings) },
                title = { Text(stringResource(R.string.jav_library_enable_title)) },
                summary = {
                  Text(
                    stringResource(R.string.jav_library_enable_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                },
              )
              PreferenceDivider()
              val hide by preferences.hideFromNormalLibrary.collectAsState()
              HapticSwitchPreference(
                value = hide,
                onValueChange = { preferences.hideFromNormalLibrary.set(it) },
                icon = { PreferenceRowIcon(icon = Icons.Outlined.Info) },
                title = { Text(stringResource(R.string.jav_library_hide_title)) },
                summary = {
                  Text(
                    stringResource(R.string.jav_library_hide_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                },
              )
            }
          }

          item { PreferenceSectionHeader(title = stringResource(R.string.jav_library_section_source)) }
          item {
            PreferenceCard {
              val cookie by preferences.javBusCookie.collectAsState()
              TextFieldPreference(
                value = cookie,
                onValueChange = { preferences.javBusCookie.set(it) },
                title = { Text(stringResource(R.string.jav_library_cookie_title)) },
                summary = {
                  Text(
                    if (cookie.isBlank()) {
                      stringResource(R.string.jav_library_cookie_summary)
                    } else {
                      stringResource(R.string.jav_library_cookie_set, cookie.take(12))
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                },
                textToValue = { it },
                icon = { PreferenceRowIcon(icon = Icons.Outlined.VpnKey) },
                textField = { value, onValueChange, _ ->
                  TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.jav_library_cookie_placeholder)) },
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                  )
                },
              )
            }
          }

          item { PreferenceSectionHeader(title = stringResource(R.string.jav_library_section_network)) }
          item {
            PreferenceCard {
              val proxyType by preferences.proxyType.collectAsState()
              ExpressiveListPreference(
                value = proxyType,
                onValueChange = { preferences.proxyType.set(it) },
                values = JavProxyType.entries,
                valueToText = { AnnotatedString(it.name) },
                icon = { PreferenceRowIcon(icon = Icons.Outlined.Language) },
                title = { Text(stringResource(R.string.jav_library_proxy_type_title)) },
                summary = { Text(proxyType.name, color = MaterialTheme.colorScheme.onSurfaceVariant) },
              )
              PreferenceDivider()
              val host by preferences.proxyHost.collectAsState()
              TextFieldPreference(
                value = host,
                onValueChange = { preferences.proxyHost.set(it.trim()) },
                title = { Text(stringResource(R.string.jav_library_proxy_host_title)) },
                textToValue = { it },
                icon = { PreferenceRowIcon(icon = Icons.Outlined.Language) },
                textField = { value, onValueChange, _ ->
                  TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.jav_library_proxy_host_placeholder)) },
                    singleLine = true,
                  )
                },
              )
              PreferenceDivider()
              val port by preferences.proxyPort.collectAsState()
              TextFieldPreference(
                value = port.toString(),
                onValueChange = {
                  preferences.proxyPort.set(it.filter(Char::isDigit).toIntOrNull()?.coerceIn(1, 65535) ?: 1080)
                },
                title = { Text(stringResource(R.string.jav_library_proxy_port_title)) },
                textToValue = { it },
                icon = { PreferenceRowIcon(icon = Icons.Outlined.Language) },
                textField = { value, onValueChange, _ ->
                  TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.jav_library_proxy_port_placeholder)) },
                    singleLine = true,
                  )
                },
              )
              PreferenceDivider()
              val username by preferences.proxyUsername.collectAsState()
              TextFieldPreference(
                value = username,
                onValueChange = { preferences.proxyUsername.set(it) },
                title = { Text(stringResource(R.string.jav_library_proxy_username_title)) },
                textToValue = { it },
                icon = { PreferenceRowIcon(icon = Icons.Outlined.Person) },
                textField = { value, onValueChange, _ ->
                  TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                  )
                },
              )
              PreferenceDivider()
              val password by preferences.proxyPassword.collectAsState()
              TextFieldPreference(
                value = password,
                onValueChange = { preferences.proxyPassword.set(it) },
                title = { Text(stringResource(R.string.jav_library_proxy_password_title)) },
                textToValue = { it },
                icon = { PreferenceRowIcon(icon = Icons.Outlined.VpnKey) },
                textField = { value, onValueChange, _ ->
                  TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                  )
                },
              )
              PreferenceDivider()
              val userAgent by preferences.customUserAgent.collectAsState()
              TextFieldPreference(
                value = userAgent,
                onValueChange = { preferences.customUserAgent.set(it) },
                title = { Text(stringResource(R.string.jav_library_user_agent_title)) },
                summary = {
                  Text(
                    stringResource(R.string.jav_library_user_agent_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                },
                textToValue = { it },
                icon = { PreferenceRowIcon(icon = Icons.Outlined.Language) },
                textField = { value, onValueChange, _ ->
                  TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.jav_library_user_agent_placeholder)) },
                    singleLine = true,
                  )
                },
              )
            }
          }

          item { PreferenceSectionHeader(title = stringResource(R.string.jav_library_section_about)) }
          item {
            PreferenceCard {
              Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                  text = stringResource(R.string.jav_library_about_text),
                  style = MaterialTheme.typography.bodyMedium,
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
