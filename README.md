# Speedbump

Speedbump is an Android app that puts friction, a short delay, in front of distracting apps without blocking them.

When you open an app you chose to watch, a calm full-screen countdown covers it. "Not now" is the big button and is
always there; "Open anyway" only appears once the wait is over. The number the app keeps is abandoned launches: how
many times you opened something, hit the pause and walked away.

It is an early-stage personal project and isn't on any app store.

## What works today

| Area | State |
|---|---|
| Accessibility service that notices a watched app coming to the front, plus the full-screen delay overlay | Built and run on real Android 16 devices |
| Escalating delay: reopening the same app within 30 minutes lengthens the wait (10 s, 20 s, 45 s, 90 s at the default base) | Built, unit-tested |
| Per-app rules (delay length, which apps are watched), Room database with migrations | Built; the migration was run on a device, the rules screens have had little device testing |
| Stats screen (screen time, pickups, hourly chart, per-app trend) from Android's usage data | Built, aggregation unit-tested; little device testing |
| Onboarding that explains each permission and suggests apps from your own usage | Built; little device testing |
| Unlock challenges (retype a sentence, arithmetic, hold a button, NFC tag, QR code) | Generation and checking logic is written and unit-tested; **not wired into the overlay yet** |
| Daily time budgets, daily open limits, "what are you here for?" prompt | Stored in the rule, **not enforced yet** |
| `:feature-focus` and `:integration-shizuku` modules | Empty placeholders |

The Rule editor already lets you pick a challenge, a budget and an open limit, but the overlay ignores all three
for now.

## How interception works

1. `SpeedbumpService` listens for `TYPE_WINDOW_STATE_CHANGED` events.
2. `ForegroundAppDetector` only delays an Activity from another app (a toast, dialog, keyboard or Speedbump's own
   window doesn't count), and only outside the 800 ms launch-burst debounce, a granted session and the
   post-interception cooldown.
3. The delay screen is a `TYPE_APPLICATION_OVERLAY` window. Back counts as "Not now", so backing out is an
   abandoned launch.
4. If you wait it out and continue, you get a session grant (5 minutes by default), so moving around inside the app
   doesn't trigger another delay. Grants live in memory only, so a reboot or force-stop gives you a fresh
   speedbump.

The 1.2 s post-interception cooldown is there because the app you were sent away from can emit one last window
event while it is torn down. Without it, that event opened a second interception, counted the abandonment twice and
pushed the escalation ladder up a rung. `ForegroundAppDetectorTest` locks this in.

## Privacy

Speedbump declares no `INTERNET` permission, so it can't make network calls. A Gradle task,
`verify<Variant>NoNetworkPermissions`, runs on every `assemble` and fails the build if `INTERNET`,
`ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` or `QUERY_ALL_PACKAGES` end up in the merged manifest. A transitive
AndroidX dependency can merge a permission in without any line of this repo changing. WorkManager once did that with
`ACCESS_NETWORK_STATE`, so the manifest removes it explicitly. The audit for the last build is written to
`app/build/reports/speedbump/<variant>-permissions.txt`.

Backups are off (`allowBackup="false"` and data-extraction rules that exclude everything), so Android backup never
uploads the on-device database.

## Permissions, and why

| Permission / access | Why |
|---|---|
| Accessibility service (`BIND_ACCESSIBILITY_SERVICE`) | The only way to learn that another app just came to the foreground. The service listens for window-change events. |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | The delay screen is an overlay window rather than an Activity. Since Android 10 a background app cannot reliably start an Activity, and an overlay leaves the app underneath exactly where it was when you choose to continue. |
| Usage access (`PACKAGE_USAGE_STATS`) | Optional. Feeds the stats screen and the "which apps are worth watching" suggestions. Blocking works without it. |
| `RECEIVE_BOOT_COMPLETED` | Lets WorkManager reschedule the periodic usage roll-up after a reboot. |
| `POST_NOTIFICATIONS`, `VIBRATE` | Declared for planned features. The current code does not use either. |
| `<queries>` for launcher apps | Lists the apps you can pick from without the restricted `QUERY_ALL_PACKAGES` permission. |

## What the accessibility service can see

At runtime Speedbump sets `AccessibilityServiceInfo.packageNames` to only the apps you chose to watch, so the system
filters events before they reach the app. With nothing watched it uses a package name that can't exist, because
some device makers treat an empty list as "all packages". While the overlay is up, the filter widens for a few
seconds so Speedbump can see you leave (an abandoned launch), then narrows again.

The code only reads the package name and class name of window-change events, never screen contents, messages or
what you type. The service's configuration still declares `canRetrieveWindowContent` and `flagReportViewIds` (see
[`speedbump_accessibility_service.xml`](service-accessibility/src/main/res/xml/speedbump_accessibility_service.xml)),
so it requests that capability even though nothing uses it. I still need to tighten that.

Settings the app can't change itself (accessibility, overlay, usage access) each hand you off to the matching
Android Settings screen. Phone, messaging, maps, camera and system settings are on a built-in safety list, resolved
from the device's current defaults. They can never be armed, so the app can't get in the way of an emergency call.

## Build

You need:

- JDK 21 or 25 (both work, including Android Studio's bundled JDK; a JDK 26 on `PATH` doesn't work with AGP)
- Android SDK platform 37 and build-tools 37.0.0
- `ANDROID_HOME` set, or a git-ignored `local.properties` file containing `sdk.dir=...`

```bash
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`, with the application id `com.speedbump.app.debug`. To install it on
a phone with USB debugging on:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then open Speedbump, go through onboarding (it walks you through the three special-access grants) and pick the apps
to watch.

The JVM unit tests need no device:

```bash
./gradlew test
```

They cover the escalation ladder, the challenge generator and verifier, the baseline planner, the settings
serializer, the usage aggregation and stats assembly, and the foreground-app detector (debounce, cooldown, session
grants). There are no instrumented or UI tests yet, so overlay behaviour on real devices is tested by hand.

## Toolchain pins

These depend on each other, so change them together.

- AGP is 9.3.2 because Hilt 2.60+ requires AGP 9.0 or newer.
- Gradle is 9.7.1 (via the wrapper). AGP 8.x relies on a Gradle internal API removed in Gradle 9.6, so AGP 8 caps
  out at Gradle 9.5.1.
- The convention plugins deliberately leave out `org.jetbrains.kotlin.android`. AGP 9 has built-in Kotlin support
  and errors if that plugin is applied too.
- `compileSdk = 37`, `targetSdk = 36`, `minSdk = 26`. The Compose BOM in use requires compiling against 37, which
  only makes newer APIs visible. `targetSdk` is what controls runtime behaviour.

## Project layout

```
:app                     Application, MainActivity, home screen, permission checks
:core-data               Room database, repositories, escalation policy, usage roll-ups
:core-ui                 Dark-first Material 3 theme and shared components
:service-accessibility   Accessibility service, detection, overlay, delay screen
:feature-onboarding      First-run flow and baseline suggestions
:feature-rules           App picker and per-app rule editor
:feature-stats           Stats dashboard
:feature-focus           Placeholder (empty)
:integration-shizuku     Placeholder (empty)
build-logic              Gradle convention plugins and the permission audit task
```

## Tech

Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, WorkManager, kotlinx.serialization, coroutines and
Flow. Tests use JUnit 4, Truth and Turbine.

## Licence

MIT. See [LICENSE](LICENSE).
