package com.speedbump.core.data.settings

import androidx.datastore.core.CorruptionException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The settings file outlives the version of the app that wrote it, which is the
 * whole reason this is a typed document rather than loose keys. These cover the
 * two ways that goes wrong: a file from a different version, and a file that is
 * not readable at all.
 */
class SpeedbumpSettingsSerializerTest {

    private val serializer = SpeedbumpSettingsSerializer

    @Test
    fun `settings survive a round trip`() = runTest {
        val original = SpeedbumpSettings(
            onboardingCompleted = true,
            baselineReviewed = true,
            baselineSkipped = false,
        )

        assertThat(roundTrip(original)).isEqualTo(original)
    }

    @Test
    fun `defaults are written out, so an older build can still read every field`() = runTest {
        val output = ByteArrayOutputStream()
        serializer.writeTo(SpeedbumpSettings(), output)

        val written = output.toString(Charsets.UTF_8.name())
        assertThat(written).contains("onboardingCompleted")
        assertThat(written).contains("baselineReviewed")
        assertThat(written).contains("baselineSkipped")
    }

    // Forward compatibility: a user who installs a newer build, then rolls back,
    // must not lose their settings to a field the older build has never heard of.
    @Test
    fun `a field written by a newer version is ignored rather than fatal`() = runTest {
        val fromTheFuture = """
            {"onboardingCompleted":true,"baselineReviewed":true,
             "baselineSkipped":false,"strictModeUntilMs":123456}
        """.trimIndent()

        val settings = serializer.readFrom(ByteArrayInputStream(fromTheFuture.toByteArray()))

        assertThat(settings.onboardingCompleted).isTrue()
        assertThat(settings.baselineReviewed).isTrue()
    }

    @Test
    fun `a missing field falls back to its default`() = runTest {
        val partial = """{"onboardingCompleted":true}"""

        val settings = serializer.readFrom(ByteArrayInputStream(partial.toByteArray()))

        assertThat(settings.onboardingCompleted).isTrue()
        assertThat(settings.baselineReviewed).isFalse()
        assertThat(settings.baselineSkipped).isFalse()
    }

    // DataStore turns a CorruptionException into the default value. Anything
    // else propagates and takes the app down on launch - in a blocker, that is
    // an app the user cannot open to disable.
    @Test
    fun `an unreadable file raises corruption so DataStore can fall back`() = runTest {
        val garbage = "not json at all {{{".toByteArray()

        val failure = runCatching { serializer.readFrom(ByteArrayInputStream(garbage)) }

        assertThat(failure.exceptionOrNull()).isInstanceOf(CorruptionException::class.java)
    }

    @Test
    fun `an empty file raises corruption rather than an unchecked parse error`() = runTest {
        val failure = runCatching { serializer.readFrom(ByteArrayInputStream(ByteArray(0))) }

        assertThat(failure.exceptionOrNull()).isInstanceOf(CorruptionException::class.java)
    }

    private suspend fun roundTrip(settings: SpeedbumpSettings): SpeedbumpSettings {
        val output = ByteArrayOutputStream()
        serializer.writeTo(settings, output)
        return serializer.readFrom(ByteArrayInputStream(output.toByteArray()))
    }
}
