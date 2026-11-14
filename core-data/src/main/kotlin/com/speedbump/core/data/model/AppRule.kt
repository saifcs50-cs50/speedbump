package com.speedbump.core.data.model

import com.speedbump.core.data.db.entity.AppRuleEntity
import com.speedbump.core.data.policy.EscalationPolicy

/** Domain view of a rule, kept free of Room annotations. */
data class AppRule(
    val packageName: String,
    val enabled: Boolean = true,
    val baseDelaySeconds: Int = EscalationPolicy.DEFAULT_BASE_SECONDS,
    val challenge: ChallengeType = ChallengeType.NONE,
    val askIntention: Boolean = false,
    val dailyBudgetMinutes: Int? = null,
    val dailyLaunchQuota: Int? = null,
    val sessionGrantMinutes: Int = DEFAULT_SESSION_GRANT_MINUTES,
) {
    val sessionGrantMs: Long get() = sessionGrantMinutes * 60_000L

    val hasBudget: Boolean get() = dailyBudgetMinutes != null
    val hasQuota: Boolean get() = dailyLaunchQuota != null

    companion object {
        const val DEFAULT_SESSION_GRANT_MINUTES = 5
    }
}

internal fun AppRuleEntity.toDomain() = AppRule(
    packageName = packageName,
    enabled = enabled,
    baseDelaySeconds = baseDelaySeconds,
    challenge = challenge,
    askIntention = askIntention,
    dailyBudgetMinutes = dailyBudgetMinutes,
    dailyLaunchQuota = dailyLaunchQuota,
    sessionGrantMinutes = sessionGrantMinutes,
)

internal fun AppRule.toEntity(createdAtMs: Long) = AppRuleEntity(
    packageName = packageName,
    enabled = enabled,
    baseDelaySeconds = baseDelaySeconds,
    challenge = challenge,
    askIntention = askIntention,
    dailyBudgetMinutes = dailyBudgetMinutes,
    dailyLaunchQuota = dailyLaunchQuota,
    sessionGrantMinutes = sessionGrantMinutes,
    createdAtMs = createdAtMs,
)
