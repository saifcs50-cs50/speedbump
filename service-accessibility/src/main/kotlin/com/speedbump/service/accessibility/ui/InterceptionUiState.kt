package com.speedbump.service.accessibility.ui

data class InterceptionUiState(
    val packageName: String,
    val appLabel: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val escalationIndex: Int = 0,
    val recentIntentions: List<String> = emptyList(),
) {
    /** The skip control does not exist until the wait is actually served. */
    val canProceed: Boolean get() = remainingSeconds <= 0

    val progress: Float
        get() = if (totalSeconds <= 0) 1f
        else ((totalSeconds - remainingSeconds).toFloat() / totalSeconds).coerceIn(0f, 1f)

    val isEscalated: Boolean get() = escalationIndex > 0
}
