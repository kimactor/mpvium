package app.aryan447.mpvium.domain.jav

/**
 * Restores a cooldown kind written by an older build. Unknown or missing values fall back to
 * a network cooldown, which is the shortest-lived and least surprising state.
 */
internal fun savedJavCooldownKind(value: String): JavSourceFailureKind =
  runCatching { JavSourceFailureKind.valueOf(value) }
    .getOrDefault(JavSourceFailureKind.NETWORK)
