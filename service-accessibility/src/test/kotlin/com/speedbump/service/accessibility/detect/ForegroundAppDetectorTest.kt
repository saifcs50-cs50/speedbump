package com.speedbump.service.accessibility.detect

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ForegroundAppDetectorTest {

    private val own = "com.speedbump.app"
    private val armed = setOf("com.instagram.android")

    private fun detector() = ForegroundAppDetector(ownPackage = own)

    private fun ForegroundAppDetector.evaluate(
        pkg: String?,
        cls: String? = "com.instagram.android.MainActivity",
        atMs: Long,
        granted: Set<String> = emptySet(),
        activityClasses: Boolean = true,
    ) = evaluate(
        event = WindowEvent(pkg, cls, atMs),
        armedPackages = armed,
        isSessionGranted = { it in granted },
        isActivityClass = { _, _ -> activityClasses },
    )

    @Test
    fun `intercepts an armed app opening`() {
        val result = detector().evaluate(pkg = "com.instagram.android", atMs = 1_000)

        assertThat(result).isEqualTo(Detection.Intercept("com.instagram.android"))
    }

    @Test
    fun `ignores apps that are not armed`() {
        val result = detector().evaluate(pkg = "com.android.chrome", atMs = 1_000)

        assertThat(result).isEqualTo(Detection.Ignore(IgnoreReason.NOT_ARMED))
    }

    @Test
    fun `ignores its own overlay so it cannot loop`() {
        val result = detector().evaluate(pkg = own, atMs = 1_000)

        assertThat(result).isEqualTo(Detection.Ignore(IgnoreReason.OWN_PACKAGE))
    }

    @Test
    fun `ignores dialogs and popups that are not activities`() {
        val result = detector()
            .evaluate(pkg = "com.instagram.android", atMs = 1_000, activityClasses = false)

        assertThat(result).isEqualTo(Detection.Ignore(IgnoreReason.NOT_AN_ACTIVITY))
    }

    @Test
    fun `debounces the burst of window events a single launch emits`() {
        val detector = detector()

        val first = detector.evaluate(pkg = "com.instagram.android", atMs = 1_000)
        val second = detector.evaluate(pkg = "com.instagram.android", atMs = 1_300)

        assertThat(first).isInstanceOf(Detection.Intercept::class.java)
        assertThat(second).isEqualTo(Detection.Ignore(IgnoreReason.DEBOUNCED))
    }

    @Test
    fun `intercepts again once the debounce window has passed`() {
        val detector = detector()

        detector.evaluate(pkg = "com.instagram.android", atMs = 1_000)
        val later = detector.evaluate(pkg = "com.instagram.android", atMs = 2_500)

        assertThat(later).isEqualTo(Detection.Intercept("com.instagram.android"))
    }

    @Test
    fun `does not re-intercept inside a granted session`() {
        val result = detector().evaluate(
            pkg = "com.instagram.android",
            atMs = 1_000,
            granted = setOf("com.instagram.android"),
        )

        assertThat(result).isEqualTo(Detection.Ignore(IgnoreReason.SESSION_GRANTED))
    }

    /**
     * Regression: on a test phone the app being ejected emitted a window event
     * ~28ms after the interception resolved, which opened a second interception
     * and double-counted the abandonment in the headline metric.
     */
    @Test
    fun `ignores the teardown event of an app that was just ejected`() {
        val detector = detector()
        detector.evaluate(pkg = "com.instagram.android", atMs = 1_000)

        detector.onInterceptionResolved("com.instagram.android", elapsedMs = 5_000)
        val tailEvent = detector.evaluate(pkg = "com.instagram.android", atMs = 5_028)

        assertThat(tailEvent).isEqualTo(Detection.Ignore(IgnoreReason.RESOLUTION_COOLDOWN))
    }

    @Test
    fun `intercepts a genuine relaunch after the cooldown expires`() {
        val detector = detector()
        detector.onInterceptionResolved("com.instagram.android", elapsedMs = 5_000)

        val relaunch = detector.evaluate(pkg = "com.instagram.android", atMs = 6_500)

        assertThat(relaunch).isEqualTo(Detection.Intercept("com.instagram.android"))
    }

    @Test
    fun `cooldown applies only to the app that was ejected`() {
        val detector = ForegroundAppDetector(ownPackage = own)
        detector.onInterceptionResolved("com.snapchat.android", elapsedMs = 5_000)

        val other = detector.evaluate(pkg = "com.instagram.android", atMs = 5_028)

        assertThat(other).isEqualTo(Detection.Intercept("com.instagram.android"))
    }

    @Test
    fun `ignores events with no package name`() {
        val result = detector().evaluate(pkg = null, atMs = 1_000)

        assertThat(result).isEqualTo(Detection.Ignore(IgnoreReason.NO_PACKAGE))
    }
}
