package app.aryan447.mpvium.preferences

import app.aryan447.mpvium.preferences.preference.PreferenceStore

class TmdbPreferences(
  preferenceStore: PreferenceStore,
) {
  val apiKey = preferenceStore.getString("tmdb_api_key", "")
}
