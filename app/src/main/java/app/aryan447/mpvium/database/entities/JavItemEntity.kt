package app.aryan447.mpvium.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "jav_items",
  indices = [
    Index(value = ["status"]),
    Index(value = ["updatedAt"]),
  ],
)
data class JavItemEntity(
  @PrimaryKey val code: String,
  val title: String = "",
  val originalTitle: String? = null,
  val overview: String? = null,
  val releaseDate: String? = null,
  val runtimeMinutes: Int? = null,
  val studio: String? = null,
  val label: String? = null,
  val series: String? = null,
  val director: String? = null,
  val genresJson: String? = null,
  val actressesJson: String? = null,
  val coverUrl: String? = null,
  val localCoverPath: String? = null,
  val source: String? = null,
  val sourceUrl: String? = null,
  val sourceId: String? = null,
  val status: String = STATUS_PENDING,
  val lastError: String? = null,
  val nextRetryAt: Long? = null,
  val fetchedAt: Long? = null,
  val updatedAt: Long = System.currentTimeMillis(),
  val manuallyEdited: Boolean = false,
  val manualTitle: String? = null,
  val manualCoverPath: String? = null,
) {
  companion object {
    const val STATUS_PENDING = "PENDING"
    const val STATUS_OK = "OK"
    const val STATUS_PARTIAL = "PARTIAL"
    const val STATUS_FAILED = "FAILED"
    const val STATUS_NOT_FOUND = "NOT_FOUND"
  }
}
