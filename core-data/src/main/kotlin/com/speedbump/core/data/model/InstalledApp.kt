package com.speedbump.core.data.model

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
)
