package com.speedbump.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.speedbump.core.data.model.InterceptionOutcome

@Entity(
    tableName = "interception_events",
    indices = [
        Index("package_name"),
        Index("started_at_ms"),
    ],
)
data class InterceptionEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    /** Wall-clock epoch millis the overlay became visible. */
    @ColumnInfo(name = "started_at_ms")
    val startedAtMs: Long,

    /** Null while an interception is still on screen. */
    @ColumnInfo(name = "resolved_at_ms")
    val resolvedAtMs: Long? = null,

    @ColumnInfo(name = "outcome")
    val outcome: InterceptionOutcome? = null,

    /** The delay the user was actually asked to sit through, in seconds. */
    @ColumnInfo(name = "delay_seconds")
    val delaySeconds: Int,

    /**
     * 0 for the first launch inside the escalation window, 1 for the second,
     * and so on. Drives the 10s -> 20s -> 45s -> 90s ramp and is worth keeping
     * because "how many times did I bounce off this in a row" is interesting.
     */
    @ColumnInfo(name = "escalation_index")
    val escalationIndex: Int = 0,

    /** Free text from the "what are you here for?" prompt. */
    @ColumnInfo(name = "intention")
    val intention: String? = null,
)
