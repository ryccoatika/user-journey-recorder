# Contributing

Thanks for working on Journey Recorder. This is an internal Android SQA tool —
it records user journeys in other apps via an `AccessibilityService` and exports
them as Markdown. Before you start, skim two files:

- **`AGENTS.md`** — day-to-day conventions (build/test gates, where things live,
  the golden rules, gotchas). It's the single source of truth for conventions.
- **`docs/ARCHITECTURE.md`** — the structural contract: layers, dependency
  rules, and the capture-pipeline invariants.

## Getting set up

- JDK 17+. Android SDK with compileSdk 37, build-tools 37.
- Firebase is mandatory. `app/google-services.json` is gitignored; produce it by
  decrypting the committed secret first:

  ```bash
  ENCRYPT_KEY=<passphrase> ./release/decrypt-secrets.sh
  ```

- Open in Android Studio (or build from the CLI). The shared debug keystore
  (`release/app-debug.jks`) is committed, so debug builds sign identically on
  every machine.

## Build, test, verify

Run these before every commit — they are the gate:

```bash
./gradlew compileDebugKotlin   # fast compile check — after every edit
./gradlew test                 # JVM unit tests — before every commit
./gradlew assembleDebug        # debug APK
```

Device verification is on demand, not the default gate. Compile + unit tests
decide whether a change is good. When you do need a device, prefer the project's
preferred test device and `adb install` (Android 13+ greys out the accessibility
toggle for browser/file-manager installs — `adb install` is exempt).

### What's unit-tested (keep it that way)

The pure layers have JVM tests and must stay pure (no `android.*` imports):

- `MarkdownGenerator` — golden full-output strings (TimeZone pinned to UTC).
- `SensitiveTextGuard` — masking rules (password flags, name regex, Luhn).
- `StepPipeline` — ordering / coalescing with a `FakeDao` +
  `kotlinx-coroutines-test`.

If you change any of them, extend the tests in the same change. Pipeline timers
live in `backgroundScope` — use `advanceTimeBy(...) + runCurrent()`, not
`advanceUntilIdle()`.

## The privacy red line

This is non-negotiable and reviewers treat regressions as critical:

- **Raw sensitive text is never persisted.** Masking happens in the capture
  pipeline *before* anything hits disk. Masked rows store `typedText = null,
  masked = true` — never a stand-in string.
- **Review-before-export stays structural.** Typed values are redactable in the
  detail screen before export.
- **Recorded journey content never leaves the device via telemetry.** Analytics
  carries only anonymous counts/lifecycle events — never typed text, screen
  text, or the identity of the recorded app.

Any change touching `SensitiveTextGuard`, `StepPipeline` masking, `AppAnalytics`,
or export must keep all three true and extend the JVM tests in the same change.
See the golden rules (G1–G7) in `docs/ARCHITECTURE.md`.

## Coding conventions

- Kotlin + Jetpack Compose + Material 3. Match the surrounding code's style,
  naming, and comment density.
- **Theme**: indigo primary, error slot doubles as the recording accent (red),
  amber tertiary for recovered/warning. Colors live in `ui/theme/Color.kt` only
  — no inline `Color(0x…)` in screens. Dynamic color stays off by design.
- **Strings**: user-facing text goes in `res/values/strings.xml` (prefixed
  keys); composables use `stringResource`, non-Compose uses `context.getString`.
  Glyph/emoji symbols and Markdown tokens stay inline.
- **DI**: add singletons to `di/Graph.kt`. ViewModels are plain classes reading
  `Graph` via default ctor params (except `DetailViewModel`, which needs a
  `SavedStateHandle`).
- **Dependencies** live in `gradle/libs.versions.toml` — add there, never inline.
- Don't fight the accessibility-service gotchas in `AGENTS.md` (service config
  flags, bubble window type, coalescing window, `serviceInfo.packageNames`).

## Commits & pull requests

- **Conventional Commits**: `feat:`, `fix:`, `perf:`, `docs:`, `refactor:`,
  `style:`, `chore:`. Imperative subject ≤ ~50 chars; body explains *why*. Terse.
- Keep each PR focused. Run `compileDebugKotlin` + `test` green before pushing.
- If you changed a pure layer, show the test diff. If you changed privacy-
  sensitive code, call it out explicitly in the PR description.
- Secrets never land in git: only the encrypted `release/*.gpg` blobs are
  committed; plaintext keystores and `google-services.json` are gitignored. If
  you rotate a secret, re-run `./release/encrypt-secrets.sh` and commit the
  updated `.gpg`.

## Known v1 limitations (don't "fix" silently)

Documented in-app, intentional:

- Swipe / pinch / drag gestures aren't captured — only taps, typing, scrolls.
- Apps that block accessibility services (some banking apps) can't be recorded.
- Compose / Flutter / RN targets expose no view ids — steps fall back to
  text/content-description/bounds locators (flagged in the export).
- Split-screen is unsupported.
