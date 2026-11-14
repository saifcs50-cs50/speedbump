package com.speedbump.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.speedbump.core.data.model.ChallengeType
import com.speedbump.core.data.policy.EscalationPolicy

/**
 * One rule per app. The package name is the primary key because a user can
 * only hold one opinion about an app at a time - "Instagram, but twice" is a
 * budget, not a second rule.
 */
@Entity(tableName = "app_rules")
data class AppRuleEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,

    @ColumnInfo(name = "base_delay_seconds")
    val baseDelaySeconds: Int = EscalationPolicy.DEFAULT_BASE_SECONDS,

    @ColumnInfo(name = "challenge")
    val challenge: ChallengeType = ChallengeType.NONE,

    /** Show the "what are you here for?" prompt. */
    @ColumnInfo(name = "ask_intention")
    val askIntention: Boolean = false,

    /** Minutes per day allowed in this app. Null means unlimited. */
    @ColumnInfo(name = "daily_budget_minutes")
    val dailyBudgetMinutes: Int? = null,

    /** Allowed opens per day. Null means unlimited. */
    @ColumnInfo(name = "daily_launch_quota")
    val dailyLaunchQuota: Int? = null,

    /** How long "continue" buys before the next speedbump, in minutes. */
    @ColumnInfo(name = "session_grant_minutes")
    val sessionGrantMinutes: Int = 5,

    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long = 0,
)
