# CLAUDE.md

The project guide lives in **AGENTS.md** — read it. It is imported below.

@AGENTS.md

## Claude Code specifics

- **Gates after every non-trivial edit**: `./gradlew compileDebugKotlin`;
  before any commit also `./gradlew test`. Fix failures before moving on —
  never batch a screen's worth of edits blind.
- **Don't device-verify UI changes by default.** Compile + unit tests are the
  gate; run on a device only when the user asks or via **`/verify-ui`**.
  Device: always `adb -s RRCX8047V9J` (never other attached serials).
- **Delegate to the project subagents** (`.claude/agents/`): `ui-designer`
  for visual work, `test-writer` for tests, `code-reviewer` before merging
  non-trivial changes.
- **Commands**: `/lint`, `/verify-ui`.
- **Commit trailer** (required): use the Co-Authored-By trailer for the model
  actually running (as instructed by your environment), e.g.
  `Co-Authored-By: Claude <model> <noreply@anthropic.com>`.
- **Privacy is the red line**: any change touching `SensitiveTextGuard`,
  `StepPipeline` masking, or export must keep "raw sensitive text never
  persisted, review-before-export stays structural" true — treat regressions
  as critical, and extend the JVM tests in the same change.
- For multi-step features use the Superpowers flow (brainstorming →
  writing-plans → execute) and save spec/plan under `docs/superpowers/`.
