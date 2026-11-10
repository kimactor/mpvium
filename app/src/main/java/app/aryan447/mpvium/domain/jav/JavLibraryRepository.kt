package app.aryan447.mpvium.domain.jav

import app.aryan447.mpvium.database.dao.JavDao
import app.aryan447.mpvium.database.dao.LibraryIndexDao
import app.aryan447.mpvium.database.entities.JavFileEntity
import app.aryan447.mpvium.database.entities.JavItemEntity
import app.aryan447.mpvium.domain.media.model.Video
import app.aryan447.mpvium.domain.streaming.toVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class JavLibraryRepository(
  private val javDao: JavDao,
  private val libraryIndexDao: LibraryIndexDao,
  private val json: Json,
) {
  suspend fun loadItems(): List<JavLibraryItem> = withContext(Dispatchers.IO) {
    val metadata = javDao.getAllItems().associateBy { it.code }
    val index = libraryIndexDao.getAll().associateBy { it.path }
    javDao.getAllFiles()
      .mapNotNull { file ->
        val entity = index[file.path] ?: return@mapNotNull null
        file.toItem(entity.toVideo(), metadata[file.code])
      }
      .sortedWith(compareBy({ it.title.lowercase() }, { it.code }))
  }

  suspend fun getItem(code: String): JavLibraryItem? = withContext(Dispatchers.IO) {
    val metadata = javDao.getItem(code)
    val file = javDao.getFilesForCode(code).firstOrNull() ?: return@withContext null
    val entity = libraryIndexDao.getByPath(file.path) ?: return@withContext null
    file.toItem(entity.toVideo(), metadata)
  }

  private fun JavFileEntity.toItem(
    video: Video,
    metadata: JavItemEntity?,
  ): JavLibraryItem = JavLibraryItem(
    file = this,
    metadata = metadata,
    video = video,
    actresses = metadata?.let { it.decodeList(it.actressesJson) }.orEmpty(),
    genres = metadata?.let { it.decodeList(it.genresJson) }.orEmpty(),
  )

  private fun JavItemEntity.decodeList(value: String?): List<String> {
    if (value.isNullOrBlank()) return emptyList()
    return runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
  }
}
