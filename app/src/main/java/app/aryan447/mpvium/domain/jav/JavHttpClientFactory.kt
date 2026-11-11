package app.aryan447.mpvium.domain.jav

import app.aryan447.mpvium.preferences.JavPreferences
import app.aryan447.mpvium.preferences.JavProxyType
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.Credentials
import okhttp3.OkHttpClient

class JavHttpClientFactory(
  private val preferences: JavPreferences,
) {
  private var cachedKey: String? = null
  private var cachedClient: OkHttpClient? = null

  /**
   * Reuse one client until a JAV network preference changes. Batch scraping calls this for
   * every item; rebuilding an OkHttp client per request would leak dispatcher threads.
   */
  @Synchronized
  fun build(): OkHttpClient {
    val proxyType = preferences.proxyType.get()
    val proxyHost = preferences.proxyHost.get().trim()
    val proxyPort = preferences.proxyPort.get()
    val proxyUsername = preferences.proxyUsername.get().trim()
    val proxyPassword = preferences.proxyPassword.get()
    val userAgent = preferences.customUserAgent.get().trim()

    val key = listOf(
      proxyType.name,
      proxyHost,
      proxyPort.toString(),
      proxyUsername,
      proxyPassword,
      userAgent,
    ).joinToString("\u0000")
    cachedClient?.takeIf { cachedKey == key }?.let { return it }

    val builder = OkHttpClient.Builder()
      .connectTimeout(25, TimeUnit.SECONDS)
      .readTimeout(35, TimeUnit.SECONDS)
      .writeTimeout(25, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)

    if (proxyType != JavProxyType.NONE && proxyHost.isNotBlank() && proxyPort in 1..65535) {
      val type = if (proxyType == JavProxyType.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
      builder.proxy(Proxy(type, InetSocketAddress(proxyHost, proxyPort)))
      if (proxyType == JavProxyType.HTTP && proxyUsername.isNotBlank()) {
        builder.proxyAuthenticator { _, response ->
          if (response.priorResponse != null) {
            null
          } else {
            response.request.newBuilder()
              .header("Proxy-Authorization", Credentials.basic(proxyUsername, proxyPassword))
              .build()
          }
        }
      }
    }

    if (userAgent.isNotBlank()) {
      builder.addInterceptor { chain ->
        chain.proceed(
          chain.request().newBuilder()
            .header("User-Agent", userAgent)
            .build(),
        )
      }
    }

    return builder.build().also {
      cachedKey = key
      cachedClient = it
    }
  }
}
