package app.aryan447.mpvium.preferences

import app.aryan447.mpvium.preferences.preference.PreferenceStore
import app.aryan447.mpvium.preferences.preference.getEnum

enum class JavProxyType {
  NONE,
  HTTP,
  SOCKS,
}

class JavPreferences(
  preferenceStore: PreferenceStore,
) {
  val libraryEnabled = preferenceStore.getBoolean("jav_library_enabled", false)
  val hideFromNormalLibrary = preferenceStore.getBoolean("jav_hide_from_normal_library", true)
  val javBusCookie = preferenceStore.getString("jav_bus_cookie", "")
  val proxyType = preferenceStore.getEnum("jav_proxy_type", JavProxyType.NONE)
  val proxyHost = preferenceStore.getString("jav_proxy_host", "")
  val proxyPort = preferenceStore.getInt("jav_proxy_port", 1080)
  val proxyUsername = preferenceStore.getString("jav_proxy_username", "")
  val proxyPassword = preferenceStore.getString("jav_proxy_password", "")
  val customUserAgent = preferenceStore.getString("jav_custom_user_agent", "")
}
