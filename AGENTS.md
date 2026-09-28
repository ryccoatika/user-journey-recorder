# AGENTS.md

Guide for AI agents (Claude Code, Codex, Cursor, Gemini, …) working in this
repo. Read this first. `CLAUDE.md` imports this file.

## What this is

A native **Android** SQA tool (Kotlin, Jetpack Compose, Room, manual DI — no
Hilt) that records user journeys in OTHER apps via an **AccessibilityService**:
taps, text input, screen changes. Journeys are reviewed in-app (with per-value
redaction) and exported as markdown that a downstream AI agent turns into test
automation (Maestro/Appium/Espresso). Internal sideload tool — Play Store
policy does not apply. Everything stays on-device until the user exports.

## Golden rules

1. **Sensitive text never touches disk.** Masking happens in the capture
   pipeline BEFORE persistence (`SensitiveTextGuard`: password flags, name
   regex, Luhn check; fail closed on unknown fields). Masked rows store
   `typedText = null, masked = true` — never a stand-in string. A tapped
   editable field's node text IS its content — `StepPipeline.sanitizeTarget`
   strips it from CLICK/SELECT rows. Don't weaken any of this.
2. **`sequence` drives step ordering, never timestamps.** Coalesced steps
   (typing, scrolls) are emitted late but must sort by when they *started*.
   All DAO queries `ORDER BY sequence`; markdown numbering = sequence order.
3. **`AccessibilityNodeInfo` never leaves `EventInterpreter`/`ElementIdentity`.**
   Nodes are live IPC proxies that go stale and can THROW on access. Extract an
   immutable `RawCapture` synchronously inside `onAccessibilityEvent`; wrap all
   node traversal in try/catch (a throw degrades to event-payload extraction
   with `Confidence.LOW`, never a crash — a service crash kills the recording).
4. **`StepPipeline` is a single-consumer channel.** One consumer coroutine owns
   ALL mutable pipeline state (sequence counter, coalescers, dedup, guard) —
   no locks, deterministic order, JVM-testable. Every finalized step is
   written through to Room immediately (process death loses at most the
   open coalescing window). `flushAndEnd()` is a barrier — repositories must
   await it before closing a journey.
5. **`JourneyRepository` is the sole writer of `RecorderStateHolder`.** UI,
   bubble and service only observe. Deleting the actively-recording journey
   must go through `repository.delete` (stops the recording first — otherwise
   FK-cascade + in-flight inserts kill the pipeline).
6. **Never set `serviceInfo.packageNames`.** Framework-level narrowing stops
   foreign-package WINDOW_STATE_CHANGED delivery, which makes the
   "Left/Returned to target app" markers unreachable. The in-code package gate
   in `JourneyAccessibilityService.onAccessibilityEvent` is the filter; own
   package is always excluded (bubble taps must never be recorded).
7. **Keep first-frame composition cheap; keep window backgrounds in sync.**
   Nav transitions alpha-blend screens, so `res/values*/colors.xml`
   `window_background` must equal `ui/theme/Color.kt` Light/DarkBackground
   (mismatch = flash). No `IntrinsicSize` in lazy-list rows (double-measure
   jank during enter transitions — the timeline line uses `drawBehind`).

## Build · test · verify

```bash
./gradlew compileDebugKotlin   # fast compile check (after every edit)
./gradlew test                 # all JVM unit tests
./gradlew assembleDebug        # debug APK
```

- No CI, no remote yet. Local `main` branch.
- JDK 17+. compileSdk 37 (minor 1), minSdk 24, targetSdk 36. Kotlin 2.2.10,
  AGP 9.x (built-in Kotlin), Compose BOM 2026.02.01, Room + KSP.
- Dependencies live in `gradle/libs.versions.toml` — add there, never inline.
- `gradle.properties` has `android.disallowKotlinSourceSets=false` — required
  for KSP with AGP 9 built-in Kotlin. Don't remove.

### Unit-test conventions

- Pure classes are JVM-tested: `MarkdownGenerator` (golden full-output
  strings, TimeZone pinned to UTC in `@Before`), `SensitiveTextGuard`,
  `StepPipeline` (FakeDao + `kotlinx-coroutines-test`).
- `RawCapture`/`ElementInfo` are pure Kotlin **by design** (bounds are a
  pre-formatted string, no `android.graphics.Rect`) so pipeline tests compile
  on the JVM. Keep it that way.
- Coroutines-test gotcha: pipeline timers live in `backgroundScope`, whose
  delays are invisible to `advanceUntilIdle()` — use
  `advanceTimeBy(...)` + `runCurrent()` to fire quiet-window flushes.

### Running on a device (on demand)

Compile + tests are the default gate. Device-verify only when asked or via
**`/verify-ui`** (drives the `run-on-device` skill). Preferred test device:
**`RRCX8047V9J`** — always pass `-s RRCX8047V9J`.

```bash
adb -s RRCX8047V9J install -r app/build/outputs/apk/debug/app-debug.apk
adb -s RRCX8047V9J shell am start -n com.ryccoatika.journeyrecorder/.ui.MainActivity
```

Sideload trap: Android 13+ greys out the accessibility toggle for
browser/file-manager installs ("Restricted setting"). `adb install` is exempt.

## Where things live

```
JourneyRecorderApp.kt   Application: Graph.init + startup orphan sweep
di/Graph.kt             manual DI singletons (db, dao, repository,
                        recorderState, stepPipeline) — no Hilt, on purpose
data/db/                Room: JourneyEntity, JourneyEventEntity (sequence,
                        confidence, masked…), EventType, JourneyDao
data/JourneyRepository.kt  start/finish/rename/delete + orphan recovery;
                        RecordingPipeline interface (implemented by StepPipeline)
recorder/               capture engine:
  ├ JourneyAccessibilityService.kt  thin event gate, owns bubble + Main scope
  ├ EventInterpreter.kt / ElementIdentity.kt  ALL node access; fallback
  │                     locator chain (id → desc → text → descendants → hint)
  ├ ScreenTracker.kt    screen-label ladder (activity class → dialogs →
  │                     paneTitle/heading scan); zero-id detection
  ├ StepPipeline.kt     single-consumer coalescing/ordering/persistence
  ├ SensitiveTextGuard.kt  masking rules (pure, tested)
  └ bubble/BubbleController.kt  plain-View overlay (TYPE_ACCESSIBILITY_OVERLAY
                        primary), drag, stop-confirm pill, pulse
export/                 MarkdownGenerator (pure, golden-tested) +
                        ExportManager (share sheet, MediaStore Downloads 29+)
ui/                     Compose + Material3, indigo/red theme:
  ├ AppNav.kt           type-safe routes + shared-axis X transitions
  ├ home | setup | detail | settings | guide    screen + ViewModel packages
  ├ common/Components.kt  TargetAppIcon, PulsingRecordDot, InfoChip,
  │                     formatDuration, versionLabel — reuse before writing new
  └ theme/              palette (light+dark), NO dynamic color by design
util/                   PermissionChecks, DeviceInfoProvider
res/xml/accessibility_service_config.xml   event types + flags (see gotchas)
```

## Design system

- Indigo primary, **error slot doubles as the "recording" accent** (red),
  amber tertiary = recovered/warning badges. Palette in `ui/theme/Color.kt`
  only — no inline `Color(0x…)` in screens.
- **Dynamic color is off deliberately** — recording states must look identical
  on every device.
- ViewModels: plain classes with default ctor params reading `Graph` (no
  factories), except `DetailViewModel` which needs
  `viewModel { DetailViewModel(createSavedStateHandle()) }`.
- Strings are hardcoded in Kotlin **by design** (internal tool, single
  locale). Don't migrate to resources without being asked.
- Step numbers shown to users are 1-based (`sequence + 1`) to match markdown.

## Commits

- **Conventional Commits** (`feat:`, `fix:`, `perf:`, `docs:`…), imperative
  subject ≤ ~50 chars, body explains why. Terse.
- Local-only repo; commits land on `main`. Commit only when asked.
- Agent-authored commits carry the trailer the tool requires.

## Gotchas (read before editing these areas)

- **`accessibility_service_config.xml`**: `flagReportViewIds` missing ⇒ every
  element id silently null. `canRetrieveWindowContent` must stay true.
  `notificationTimeout` stays ≤ 50ms (higher merges fast list taps). Never add
  touch-exploration/key-filter flags — they change how the device behaves for
  the user. NOT subscribed: `TYPE_WINDOW_CONTENT_CHANGED` (event flood).
- **Compose/Flutter/RN targets expose no view ids** — that's why
  `ElementIdentity` has a fallback chain and events carry `confidence`. The
  in-app Guide screen documents this per framework; keep it accurate.
- **Bubble window type**: `TYPE_ACCESSIBILITY_OVERLAY` primary (no permission,
  trusted touches), `TYPE_APPLICATION_OVERLAY`/`TYPE_PHONE` fallback at
  alpha ≤ 0.8 (Android 12+ untrusted-touch blocking). Plain Views, not
  Compose (a WindowManager ComposeView needs fake lifecycle owners).
- **No foreground service** — an enabled a11y service already holds the
  process at FGS importance. Don't add one (targetSdk 34+ would demand a
  `foregroundServiceType`).
- **Text coalescing**: 900ms quiet window; flush triggers include any
  non-TEXT capture (BEFORE processing it) so "type email → tap Next" orders
  correctly. Empty final text after non-empty before ⇒ explicit Clear step.
- **Downloads export is API 29+ only** (MediaStore). No legacy
  WRITE_EXTERNAL_STORAGE path — share sheet covers older devices. Share caps
  `EXTRA_TEXT` at 100k chars (binder limit) and truncates filenames.
- **Predictive back** is enabled in the manifest
  (`enableOnBackInvokedCallback`) — don't intercept back with legacy APIs.
- **Known v1 limitations** (documented in-app, don't "fix" silently):
  swipes/pinch gestures not captured; accessibility-blocking banking apps
  unrecordable; split-screen unsupported.
