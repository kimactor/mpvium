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
  val layoutMode = preferenceStore.getEnum("jav_layout_mode", MediaLayoutMode.LIST)
  val javBusCookie = preferenceStore.getString("jav_bus_cookie", "")
  val javDbEnabled = preferenceStore.getBoolean("jav_db_enabled", false)
  val ageConfirmed = preferenceStore.getBoolean("jav_age_confirmed", false)
  val proxyType = preferenceStore.getEnum("jav_proxy_type", JavProxyType.NONE)
  val proxyHost = preferenceStore.getString("jav_proxy_host", "")
  val proxyPort = preferenceStore.getInt("jav_proxy_port", 1080)
  val proxyUsername = preferenceStore.getString("jav_proxy_username", "")
  val proxyPassword = preferenceStore.getString("jav_proxy_password", "")
  val customUserAgent = preferenceStore.getString("jav_custom_user_agent", "")

  val javBusCooldownUntil = preferenceStore.getLong("jav_bus_cooldown_until", 0L)
  val javBusCooldownKind = preferenceStore.getString("jav_bus_cooldown_kind", "")
  val javBusCooldownMessage = preferenceStore.getString("jav_bus_cooldown_message", "")

  val javDbApiAuthCooldownUntil = preferenceStore.getLong("jav_db_api_auth_cooldown_until", 0L)
  val javDbApiHostCooldowns = preferenceStore.getString("jav_db_api_host_cooldowns", "")

  val javDbPageCooldownUntil = preferenceStore.getLong("jav_db_page_cooldown_until", 0L)
  val javDbPageCooldownKind = preferenceStore.getString("jav_db_page_cooldown_kind", "")
  val javDbPageCooldownMessage = preferenceStore.getString("jav_db_page_cooldown_message", "")
}
