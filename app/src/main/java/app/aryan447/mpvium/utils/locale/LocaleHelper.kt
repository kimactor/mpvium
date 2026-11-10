package app.aryan447.mpvium.utils.locale

import android.content.Context
import android.content.res.Configuration
import androidx.preference.PreferenceManager
import app.aryan447.mpvium.preferences.AppLanguage
import java.util.Locale

object LocaleHelper {
  private const val KEY = "app_language"

  fun wrap(base: Context): Context {
    val stored = PreferenceManager.getDefaultSharedPreferences(base)
      .getString(KEY, AppLanguage.SYSTEM.name)
    val language = runCatching { AppLanguage.valueOf(stored.orEmpty()) }
      .getOrDefault(AppLanguage.SYSTEM)
    val tag = language.tag ?: return base
    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale)
    val configuration = Configuration(base.resources.configuration).apply {
      setLocale(locale)
      setLayoutDirection(locale)
    }
    return base.createConfigurationContext(configuration)
  }
}
