package io.github.gambitly.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The user's result for one content item; [kind] is one of the constants in [ProgressKind]. */
@Entity(tableName = "progress", primaryKeys = ["kind", "itemId"])
data class Progress(
    val kind: String,
    val itemId: String,
    /** Times completed successfully. */
    val completions: Int,
    /** Times attempted, including failures. */
    val attempts: Int,
    /** Best score as a percentage, where the mode has one (classic games); otherwise 0. */
    val bestPercent: Int,
    val updatedAt: Long,
)

object ProgressKind {
    const val OPENING = "opening"
    const val PUZZLE = "puzzle"
    const val CLASSIC = "classic"
    const val ENDGAME = "endgame"
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE kind = :kind")
    fun observeKind(kind: String): Flow<List<Progress>>

    @Query("SELECT * FROM progress WHERE kind = :kind AND itemId = :itemId")
    suspend fun find(kind: String, itemId: String): Progress?

    @Upsert
    suspend fun save(progress: Progress)
}

/** Records one attempt at an item, keeping the best score. */
suspend fun ProgressDao.record(kind: String, itemId: String, success: Boolean, percent: Int = 0) {
    val old = find(kind, itemId)
    save(
        Progress(
            kind = kind,
            itemId = itemId,
            completions = (old?.completions ?: 0) + if (success) 1 else 0,
            attempts = (old?.attempts ?: 0) + 1,
            bestPercent = maxOf(old?.bestPercent ?: 0, percent),
            updatedAt = System.currentTimeMillis(),
        ),
    )
}

@Database(entities = [Progress::class], version = 1, exportSchema = false)
abstract class UserDatabase : RoomDatabase() {
    abstract fun progress(): ProgressDao

    companion object {
        fun open(context: Context): UserDatabase =
            Room.databaseBuilder(context, UserDatabase::class.java, "user.db").build()
    }
}
