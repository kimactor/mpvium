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
  fun build(): OkHttpClient {
    val builder = OkHttpClient.Builder()
      .connectTimeout(25, TimeUnit.SECONDS)
      .readTimeout(35, TimeUnit.SECONDS)
      .writeTimeout(25, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)

    val proxyType = preferences.proxyType.get()
    val proxyHost = preferences.proxyHost.get().trim()
    val proxyPort = preferences.proxyPort.get()
    if (proxyType != JavProxyType.NONE && proxyHost.isNotBlank() && proxyPort in 1..65535) {
      val type = if (proxyType == JavProxyType.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
      builder.proxy(Proxy(type, InetSocketAddress(proxyHost, proxyPort)))
      if (proxyType == JavProxyType.HTTP) {
        val username = preferences.proxyUsername.get().trim()
        val password = preferences.proxyPassword.get()
        if (username.isNotBlank()) {
          builder.proxyAuthenticator { _, response ->
            if (response.priorResponse != null) {
              null
            } else {
              response.request.newBuilder()
                .header("Proxy-Authorization", Credentials.basic(username, password))
                .build()
            }
          }
        }
      }
    }

    val userAgent = preferences.customUserAgent.get().trim()
    if (userAgent.isNotBlank()) {
      builder.addInterceptor { chain ->
        chain.proceed(
          chain.request().newBuilder()
            .header("User-Agent", userAgent)
            .build(),
        )
      }
    }

    return builder.build()
  }
}
