package com.speedbump.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.speedbump.core.data.db.entity.InterceptionEventEntity
import com.speedbump.core.data.model.InterceptionOutcome
import kotlinx.coroutines.flow.Flow

@Dao
interface InterceptionEventDao {

    @Insert
    suspend fun insert(event: InterceptionEventEntity): Long

    @Query(
        """
        UPDATE interception_events
        SET outcome = :outcome, resolved_at_ms = :resolvedAtMs, intention = :intention
        WHERE id = :id
        """
    )
    suspend fun resolve(
        id: Long,
        outcome: InterceptionOutcome,
        resolvedAtMs: Long,
        intention: String?,
    )

    @Query("SELECT * FROM interception_events WHERE id = :id")
    suspend fun findById(id: Long): InterceptionEventEntity?

    /** Every interception in a window, newest first. */
    @Query(
        """
        SELECT * FROM interception_events
        WHERE started_at_ms >= :fromMs AND started_at_ms < :toMs
        ORDER BY started_at_ms DESC
        """
    )
    fun observeBetween(fromMs: Long, toMs: Long): Flow<List<InterceptionEventEntity>>

    /** The hero metric. */
    @Query(
        """
        SELECT COUNT(*) FROM interception_events
        WHERE outcome = 'ABANDONED' AND started_at_ms >= :fromMs AND started_at_ms < :toMs
        """
    )
    fun observeAbandonedCount(fromMs: Long, toMs: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM interception_events
        WHERE package_name = :packageName
          AND started_at_ms >= :fromMs AND started_at_ms < :toMs
        """
    )
    suspend fun countForPackageBetween(packageName: String, fromMs: Long, toMs: Long): Int

    /** Recent intentions for a package, for the "last 7 times you said" callback. */
    @Query(
        """
        SELECT intention FROM interception_events
        WHERE package_name = :packageName AND intention IS NOT NULL AND intention != ''
        ORDER BY started_at_ms DESC
        LIMIT :limit
        """
    )
    suspend fun recentIntentions(packageName: String, limit: Int): List<String>

    @Query("DELETE FROM interception_events WHERE started_at_ms < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int
}
