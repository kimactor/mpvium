package app.aryan447.mpvium.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "jav_files",
  indices = [
    Index(value = ["code"]),
    Index(value = ["matchType"]),
    Index(value = ["lastSeenAt"]),
  ],
)
data class JavFileEntity(
  @PrimaryKey val path: String,
  val code: String,
  val rawCode: String? = null,
  val kind: String,
  val confidence: Float,
  val matchType: String = MATCH_AUTO,
  val displayName: String,
  val size: Long,
  val mtimeMs: Long,
  val lastSeenAt: Long = System.currentTimeMillis(),
) {
  companion object {
    const val MATCH_AUTO = "AUTO"
    const val MATCH_MANUAL = "MANUAL"
  }
}
