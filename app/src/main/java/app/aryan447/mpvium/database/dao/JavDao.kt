package app.aryan447.mpvium.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.aryan447.mpvium.database.entities.JavFileEntity
import app.aryan447.mpvium.database.entities.JavItemEntity

@Dao
interface JavDao {
  @Query("SELECT * FROM jav_items")
  suspend fun getAllItems(): List<JavItemEntity>

  @Query("SELECT * FROM jav_items WHERE code = :code")
  suspend fun getItem(code: String): JavItemEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertItem(item: JavItemEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertMissingItems(items: List<JavItemEntity>)

  @Query("SELECT * FROM jav_files")
  suspend fun getAllFiles(): List<JavFileEntity>

  @Query("SELECT * FROM jav_files WHERE code = :code")
  suspend fun getFilesForCode(code: String): List<JavFileEntity>

  @Query("SELECT path FROM jav_files")
  suspend fun getAllFilePaths(): List<String>

  @Query("SELECT path FROM jav_files WHERE matchType = 'MANUAL'")
  suspend fun getManualFilePaths(): List<String>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertFiles(files: List<JavFileEntity>)

  @Query("UPDATE jav_files SET lastSeenAt = :seenAt WHERE path IN (:paths)")
  suspend fun markSeen(paths: List<String>, seenAt: Long)

  @Query("DELETE FROM jav_files WHERE path = :path")
  suspend fun deleteFile(path: String)

  @Query("DELETE FROM jav_files WHERE lastSeenAt < :cutoff")
  suspend fun deleteFilesNotSeenSince(cutoff: Long)

  @Transaction
  suspend fun indexFiles(
    files: List<JavFileEntity>,
    manualPaths: List<String>,
    seenAt: Long,
  ) {
    if (files.isNotEmpty()) {
      files.chunked(500).forEach { chunk ->
        insertMissingItems(chunk.map { JavItemEntity(code = it.code) })
        upsertFiles(chunk)
      }
    }
    if (manualPaths.isNotEmpty()) {
      manualPaths.chunked(500).forEach { chunk ->
        markSeen(chunk, seenAt)
      }
    }
  }
}
