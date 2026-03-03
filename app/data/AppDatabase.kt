// In app/src/main/java/com/cellshield.app/data/AppDatabase.kt
package com.cellshield.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// --- ENTITY ---
// This class defines the table for your history log.
@Entity(tableName = "detection_history")
data class DetectionEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis(),
    val severity: String,
    // --- NEW FIELDS ---
    val towerId: String? = null,
    val signalStrength: Int? = null, // e.g., in dBm
    val location: String? = null // e.g., "Lat/Lng: 5.4, 100.3"
)

// --- DATA CLASS FOR TRENDS ---
data class DailyTrend(val day: String, val count: Int)

// --- DAO (Data Access Object) ---
// This interface defines how you interact with the database.
@Dao
interface HistoryDao {
    @Insert
    suspend fun insertEvent(event: DetectionEvent)

    @Query("SELECT * FROM detection_history ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<DetectionEvent>>

    @Query("SELECT * FROM detection_history WHERE timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    suspend fun getEventsForExport(startDate: Long, endDate: Long): List<DetectionEvent>

    // --- NEW QUERIES ---
    @Query("""
        SELECT COUNT(*) as count, strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch') as day
        FROM detection_history
        WHERE severity = 'High' 
        GROUP BY day
        ORDER BY timestamp DESC
        LIMIT 7
    """)
    fun getDetectionTrends(): Flow<List<DailyTrend>>

    @Query("SELECT * FROM detection_history ORDER BY timestamp DESC LIMIT 1")
    fun getLatestEvent(): Flow<DetectionEvent?>
}

// --- DATABASE ---
// This is the main database class for the application.
@Database(entities = [DetectionEvent::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var Instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "cellshield_database")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}