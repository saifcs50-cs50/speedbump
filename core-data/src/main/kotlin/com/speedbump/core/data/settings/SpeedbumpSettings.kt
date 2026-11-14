package com.speedbump.core.data.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Everything that is a preference rather than a record.
 *
 * Records live in Room; this holds the handful of flags that describe where the
 * user is in the product. It is a typed document rather than loose key-value
 * pairs so that a misspelt key is a compile error.
 *
 * This is DataStore with a kotlinx-serialization serializer rather than Proto
 * DataStore, which gives the same typed document and schema evolution without
 * adding protoc codegen to a tightly pinned toolchain. The serialization plugin
 * is already applied here.
 */
@Serializable
data class SpeedbumpSettings(
    /** False until the user has been through onboarding, however they exit it. */
    val onboardingCompleted: Boolean = false,

    /** Set when the user has seen and acted on the proposals, or skipped them. */
    val baselineReviewed: Boolean = false,

    /** The user declined to wait and went straight to picking apps by hand. */
    val baselineSkipped: Boolean = false,
)

/**
 * A settings file that fails to parse is replaced with defaults rather than
 * crashing the app on launch. Losing a few flags is recoverable; a boot loop in
 * a blocker the user cannot open to disable is not.
 */
object SpeedbumpSettingsSerializer : Serializer<SpeedbumpSettings> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue = SpeedbumpSettings()

    override suspend fun readFrom(input: InputStream): SpeedbumpSettings =
        try {
            json.decodeFromString(
                SpeedbumpSettings.serializer(),
                input.readBytes().decodeToString(),
            )
        } catch (error: SerializationException) {
            throw CorruptionException("Unreadable Speedbump settings", error)
        }

    override suspend fun writeTo(t: SpeedbumpSettings, output: OutputStream) {
        output.write(
            json.encodeToString(SpeedbumpSettings.serializer(), t).encodeToByteArray()
        )
    }

    const val FILE_NAME = "speedbump-settings.json"
}
