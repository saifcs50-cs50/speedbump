package com.speedbump.core.data.db

import androidx.room.TypeConverter
import com.speedbump.core.data.model.ChallengeType
import com.speedbump.core.data.model.InterceptionOutcome

class Converters {
    @TypeConverter
    fun outcomeToString(value: InterceptionOutcome?): String? = value?.name

    /**
     * Unknown values decode to null rather than throwing. A row written by a
     * newer build that the user then downgraded should not make the whole
     * stats screen crash.
     */
    @TypeConverter
    fun stringToOutcome(value: String?): InterceptionOutcome? =
        value?.let { runCatching { InterceptionOutcome.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun challengeToString(value: ChallengeType): String = value.name

    @TypeConverter
    fun stringToChallenge(value: String): ChallengeType =
        runCatching { ChallengeType.valueOf(value) }.getOrDefault(ChallengeType.NONE)
}
