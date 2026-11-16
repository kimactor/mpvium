package app.aryan447.mpvium.domain.jav

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class JavSubtitleFile(
  val file: File,
  val chinese: Boolean,
  val managed: Boolean,
)

/**
 * Local JAV subtitles live in two places: next to the video (named after the release code) and in
 * app-private storage for files imported or downloaded from the app.
 */
class JavSubtitleRepository(
  private val context: Context,
) {
  fun appSubtitleDir(code: String): File =
    File(File(context.filesDir, "jav_subtitles"), code.uppercase(Locale.ROOT))

  suspend fun listForVideo(
    code: String,
    videoFilePath: String?,
    videoUri: String? = null,
  ): List<JavSubtitleFile> =
    withContext(Dispatchers.IO) {
      val appDir = appSubtitleDir(code)
      val candidates = buildList {
        videoDirectory(videoFilePath, videoUri)?.let { dir ->
          dir.listFiles()
            .orEmpty()
            .filter { it.isFile && JavSubtitleMatcher.matchesCode(it.name, code) }
            .forEach { add(JavSubtitleFile(it, JavSubtitleMatcher.isChineseName(it.name), managed = false)) }
        }
        appDir.listFiles()
          .orEmpty()
          .filter { it.isFile && JavSubtitleMatcher.isSubtitleFile(it.name) }
          .forEach { add(JavSubtitleFile(it, JavSubtitleMatcher.isChineseName(it.name), managed = true)) }
      }
      candidates
        .distinctBy { it.file.absolutePath }
        .sortedWith(compareByDescending<JavSubtitleFile> { it.chinese }.thenBy { it.file.name.lowercase(Locale.ROOT) })
    }

  suspend fun hasChinese(code: String, videoFilePath: String?): Boolean =
    listForVideo(code, videoFilePath).any { it.chinese }

  suspend fun importFile(
    code: String,
    displayName: String,
    bytes: ByteArray,
  ): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        require(JavSubtitleMatcher.isSubtitleFile(displayName)) { "Unsupported subtitle file" }
        require(JavSubtitleEncoding.looksLikeSubtitle(bytes)) { "File is not a subtitle" }
        val target = File(appSubtitleDir(code), sanitizeName(displayName))
        target.parentFile?.mkdirs()
        target.writeBytes(JavSubtitleEncoding.toUtf8(bytes))
        target
      }
    }

  suspend fun saveDownload(
    code: String,
    language: String,
    bytes: ByteArray,
  ): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        require(JavSubtitleEncoding.looksLikeSubtitle(bytes)) { "Downloaded file is not a subtitle" }
        val target = File(appSubtitleDir(code), "${code.uppercase(Locale.ROOT)}-$language.srt")
        target.parentFile?.mkdirs()
        target.writeBytes(JavSubtitleEncoding.toUtf8(bytes))
        target
      }
    }

  suspend fun delete(file: File): Boolean = withContext(Dispatchers.IO) { file.delete() }

  private fun sanitizeName(name: String): String {
    val base = name.substringAfterLast('/').substringAfterLast('\\')
    val extension = base.substringAfterLast('.', "srt").lowercase(Locale.ROOT)
    val stem = base.substringBeforeLast('.', base).replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
    return if (stem.isBlank()) "subtitle.$extension" else "$stem.$extension"
  }

  private fun videoDirectory(videoFilePath: String?, videoUri: String?): File? {
    videoFilePath?.takeIf { it.startsWith("/") }?.let { return File(it).parentFile }
    listOfNotNull(videoFilePath, videoUri)
      .filter { it.startsWith("content://") }
      .forEach { value ->
        queryColumn(value, MediaStore.MediaColumns.DATA)
          ?.takeIf { it.startsWith("/") }
          ?.let { return File(it).parentFile }
        queryColumn(value, MediaStore.MediaColumns.RELATIVE_PATH)
          ?.let { relative ->
            val dir = File(Environment.getExternalStorageDirectory(), relative)
            if (dir.isDirectory) return dir
          }
      }
    return null
  }

  private fun queryColumn(
    uriString: String,
    column: String,
  ): String? =
    runCatching {
      context.contentResolver
        .query(Uri.parse(uriString), arrayOf(column), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
}
