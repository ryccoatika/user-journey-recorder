# Journey Recorder

A native Android SQA tool that records user journeys in **other** apps — taps,
text input, screen changes — via an `AccessibilityService`. Journeys are
reviewed in-app (with per-value redaction) and exported as Markdown that a
downstream AI agent turns into test automation (Maestro / Appium / Espresso).

Internal sideload tool. Everything stays on-device until you explicitly export.

## Highlights

- **Capture engine** — an `AccessibilityService` records clicks, long-presses,
  text input, scrolls, screen changes and system-dialog interactions in the
  target app you pick. A fallback locator chain (view id → content description
  → text → descendants → hint → class+bounds) keeps steps usable even on
  Compose / Flutter / React Native UIs that expose no view ids.
- **Privacy-first** — passwords and sensitive fields (PIN, OTP, CVV, card
  numbers via Luhn) are masked in the capture pipeline **before** anything
  touches disk; raw sensitive text is never persisted. Recorded journeys never
  leave the device until you export them.
- **Review & redact** — every typed value is shown in the step list with a
  redact toggle before export.
- **Markdown export** — numbered, screen-grouped steps with a header (app
  package/version, device, Android version). Share sheet on all APIs; Save to
  Downloads on API 29+.
- **Floating control bubble** — draggable overlay to pause / stop / discard a
  recording, plus a live notification so you can stop even if the bubble is
  dismissed.

## Tech stack

- Kotlin 2.2.10, Jetpack Compose + Material 3 (indigo/red theme, no dynamic
  color by design), Room + KSP, DataStore.
- Manual DI via a `Graph` singleton — no Hilt, on purpose.
- Firebase Analytics + Crashlytics (anonymous diagnostics only — see below).
- AGP 9.x, compileSdk 37, minSdk 24, targetSdk 36. JDK 17+.

## What telemetry is (and isn't) collected

Recorded journey content — taps, typed text, on-screen text, and the identity
of the app being recorded — is **never** sent anywhere. Firebase collects only
anonymous lifecycle/feature events (screen views, recording started / stopped /
discarded, export, onboarding completed, redaction used) and crash reports, all
routed through `AppAnalytics` and guarded so a telemetry failure can never
crash the app or the capture service.

## Build & test

```bash
./gradlew compileDebugKotlin   # fast compile check
./gradlew test                 # JVM unit tests
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # signed release APK (needs release key, below)
```

Firebase is mandatory. `app/google-services.json` is gitignored; produce it by
decrypting the committed secret (see below) before the first build.

### Running on a device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.ryccoatika.journeyrecorder/.ui.MainActivity
```

Sideload trap: Android 13+ greys out the accessibility toggle for installs from
browsers / file managers ("Restricted setting"). `adb install` is exempt.

## Secrets & signing

Plaintext secrets are gitignored; only AES256-encrypted `release/*.gpg` blobs
are committed. The shared debug keystore (`release/app-debug.jks`, public
well-known credentials) is committed so every machine/CI produces the same
debug signature.

```bash
# Decrypt secrets into their plaintext originals (produces app/google-services.json
# and release/app-release.jks):
ENCRYPT_KEY=<passphrase> ./release/decrypt-secrets.sh

# After changing a secret, re-encrypt and commit the resulting release/*.gpg:
ENCRYPT_KEY=<passphrase> ./release/encrypt-secrets.sh
```

Release signing reads `release/app-release.jks` (alias `journeyrecorder`) and
expects the passwords in Gradle properties (e.g. `~/.gradle/gradle.properties`):

```properties
JOURNEYRECORDER_RELEASE_KEYSTORE_PWD=…
JOURNEYRECORDER_RELEASE_KEY_PWD=…
```

The release build is signed only when `release/app-release.jks` is present;
otherwise the release APK is unsigned (the project still builds).

## Project structure

```
data/          Room entities, DAO, JourneyRepository (sole writer of recorder state)
recorder/      capture engine: AccessibilityService, EventInterpreter,
               ElementIdentity, ScreenTracker, StepPipeline, SensitiveTextGuard,
               bubble/BubbleController
export/        MarkdownGenerator (pure, golden-tested), ExportManager, JourneyNotifier
analytics/     AppAnalytics — Firebase wrapper
ui/            Compose screens: home, setup, detail, settings, guide, onboarding
di/Graph.kt    manual DI singletons
```

## Known v1 limitations

Documented in-app; not bugs:

- Swipe / pinch / drag gestures are not captured — only taps, typing, scrolls.
- Apps that block accessibility services (some banking apps) can't be recorded.
- Compose / Flutter / RN targets expose no view ids — steps fall back to
  text/content-description/bounds locators (flagged in the export).
- Split-screen is unsupported.
