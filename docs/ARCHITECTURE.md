# Architecture

The architecture contract for Journey Recorder. `AGENTS.md` covers day-to-day
conventions; this file owns structure: layers, the capture-pipeline
invariants, dependency rules, and enforcement.

## Decision: single-module, layered packages

**One Gradle module (`:app`), layered packages, manual DI.** No Hilt, no
multi-module split. The app is a handful of screens, one database, one
accessibility service, and a pure export layer — compiler-walled module
boundaries would cost more on every AGP bump than they return. Extraction stays
available (the package layout is already shaped for it); the **split
tripwires** at the bottom say when to revisit.

Manual DI is deliberate: `di/Graph.kt` holds lazy singletons (db, dao,
repository, recorder state, step pipeline, prefs, analytics). ViewModels are
plain classes that read `Graph` through default constructor params, so they
stay fakeable in tests without a DI framework.

## Layers

```
com.ryccoatika.journeyrecorder/
├── JourneyRecorderApp.kt            Application: Graph.init + startup orphan sweep
├── di/Graph.kt                      manual DI singletons (no Hilt, on purpose)
├── data/
│   ├── db/                          Room: entities, DAO, EventType, migrations
│   ├── AppPrefs.kt                  DataStore preferences
│   └── JourneyRepository.kt         start/finish/rename/delete + orphan recovery;
│                                    SOLE writer of RecorderStateHolder
├── recorder/                        capture engine
│   ├── JourneyAccessibilityService  thin event gate; owns bubble + Main scope
│   ├── EventInterpreter             ALL node access → immutable RawCapture
│   ├── ElementIdentity              fallback locator chain
│   ├── ScreenTracker                screen-label ladder
│   ├── StepPipeline                 single-consumer coalescing/ordering/persist
│   ├── SensitiveTextGuard           masking rules (pure, tested)
│   ├── RecorderStateHolder          StateFlow of recording state (+ pause)
│   └── bubble/BubbleController       plain-View overlay (drag / pause / stop)
├── export/
│   ├── MarkdownGenerator            pure (journey, events) → String (golden-tested)
│   ├── ExportManager                share sheet + MediaStore Downloads (29+)
│   ├── JourneyNotifier              live + result notifications
│   └── JourneyActionReceiver        notification action routing
├── analytics/AppAnalytics.kt        Firebase wrapper (anonymous diagnostics)
├── ui/                              Compose + Material 3 (indigo/red theme)
│   ├── <feature>/                   home | setup | detail | settings | guide |
│   │                                onboarding — each Screen(s) + ViewModel
│   ├── common/Components.kt         shared composables — reuse before adding new
│   ├── theme/                       palette (light+dark), NO dynamic color
│   └── AppNav.kt                    type-safe routes + shared-axis transitions
└── util/                            PermissionChecks, DeviceInfoProvider
```

Dependency direction: **ui → data / recorder / export**, with `di/` composing
everything and `ui/AppNav.kt` as the cross-feature composition point.
ViewModels are the ui↔data seam; composables consume data/db types and plain
state, never touch the Room DAO directly.

There is deliberately **no domain/use-case layer**. The pure logic that would
live there is already isolated in testable classes (`MarkdownGenerator`,
`SensitiveTextGuard`, `StepPipeline`).

## Capture-pipeline invariants (the golden rules)

These are load-bearing. Breaking one is a correctness or privacy regression,
not a style nit. Treat a change touching them as critical and extend the JVM
tests in the same change.

| # | Invariant |
|---|-----------|
| G1 | **Sensitive text never touches disk.** Masking happens in the capture pipeline *before* persistence (`SensitiveTextGuard`: password flags, name regex, Luhn; fail closed on unknown fields). Masked rows store `typedText = null, masked = true` — never a stand-in string. `StepPipeline.sanitizeTarget` strips a tapped editable field's node text from CLICK/SELECT rows. |
| G2 | **`sequence` drives step ordering, never timestamps.** Coalesced steps are emitted late but sort by when they *started*. All DAO queries `ORDER BY sequence`; Markdown numbering = sequence order. |
| G3 | **`AccessibilityNodeInfo` never leaves `EventInterpreter` / `ElementIdentity`.** Nodes are live IPC proxies that go stale and can throw. Extract an immutable `RawCapture` synchronously inside `onAccessibilityEvent`; wrap all node traversal in try/catch — a throw degrades to event-payload extraction with `Confidence.LOW`, never a crash (a service crash kills the recording). |
| G4 | **`StepPipeline` is a single-consumer channel.** One consumer coroutine owns ALL mutable pipeline state (sequence counter, coalescers, dedup, guard) — no locks, deterministic, JVM-testable. Every finalized step is written through to Room immediately. `flushAndEnd()` is a barrier repositories await before closing a journey. |
| G5 | **`JourneyRepository` is the sole writer of `RecorderStateHolder`.** UI, bubble and service only observe. Deleting the actively-recording journey must go through `repository.delete` (stops the recording first). |
| G6 | **Never set `serviceInfo.packageNames`.** Framework-level narrowing stops foreign-package `WINDOW_STATE_CHANGED` delivery, which makes the "Left/Returned to target app" markers unreachable. The in-code package gate in `onAccessibilityEvent` is the filter; own package is always excluded (bubble taps must never be recorded). |
| G7 | **Recorded journey content never leaves the device via telemetry.** `AppAnalytics` events carry only aggregate counts and lifecycle signals — never typed text, screen text, or the identity of the recorded app. Every Firebase call is guarded so a telemetry failure can't crash the app or the service. |

## Structural rules

| Rule | Contract |
|------|----------|
| R1 | Pure classes stay pure & JVM-testable: `MarkdownGenerator`, `SensitiveTextGuard`, `StepPipeline`, and `RawCapture`/`ElementInfo` import no `android.*` (bounds are a pre-formatted string, not `android.graphics.Rect`). |
| R2 | `AccessibilityNodeInfo` types appear only under `recorder/` (G3). |
| R3 | UI files that are not `*ViewModel.kt` never touch the Room DAO — the ViewModel is the data seam. |
| R4 | No feature package under `ui/` imports another feature package; only `ui/AppNav.kt` composes features. |
| R5 | `Color(0x…)` only under `ui/theme/` — screens use the palette, never inline colors. Dynamic color stays off. |
| R6 | User-facing strings live in `res/values/strings.xml` (prefixed keys); composables use `stringResource`, non-Compose uses `context.getString`. Glyph/emoji symbols and Markdown tokens stay inline. |
| R7 | No foreground service — an enabled a11y service already holds the process at FGS importance. |
| R8 | `window_background` in `res/values*/colors.xml` must equal the Light/Dark background in `ui/theme/Color.kt` (mismatch = nav-transition flash). |

## Accessibility-service gotchas

- `accessibility_service_config.xml`: `flagReportViewIds` missing ⇒ every
  element id silently null. `canRetrieveWindowContent` must stay true.
  `notificationTimeout` ≤ 50 ms. Never add touch-exploration/key-filter flags.
  Not subscribed: `TYPE_WINDOW_CONTENT_CHANGED` (event flood).
- Bubble window type: `TYPE_ACCESSIBILITY_OVERLAY` primary (no permission,
  trusted touches); `TYPE_APPLICATION_OVERLAY`/`TYPE_PHONE` fallback at
  alpha ≤ 0.8. Plain Views, not Compose.
- Text coalescing: 900 ms quiet window; any non-TEXT capture flushes first so
  "type email → tap Next" orders correctly.

## Split tripwires — when to revisit modularization

Escalate a package to a real Gradle module only when one fires (measure):

- **T1 build pain**: clean `:app:compileDebugKotlin` > 120 s or warm
  incremental > 30 s.
- **T2 team growth**: ≥ 2 distinct human committers in 90 days.
- **T3 second consumer**: a Wear/widget/second-APK target, or a flavor that
  must strip Firebase.
- **T4 sheer size**: main source > 20 k Kotlin lines or > 10 feature packages.

Extraction order when one fires: pure classes first (`MarkdownGenerator`,
`SensitiveTextGuard`, the pipeline value types → a `core` module), then the
capture engine, then features — the layout above makes each a `git mv` plus a
build-script stub.
