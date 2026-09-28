---
description: Compile check, unit tests, and Android lint — the pre-commit gate
---

Run the full local quality gate, in order, fixing failures before moving on:

1. `./gradlew compileDebugKotlin`
2. `./gradlew test` — all JVM unit tests must pass (goldens, guard, pipeline)
3. `./gradlew :app:lintDebug` — report new errors/warnings introduced by the
   current diff (pre-existing baseline noise is not yours to fix unless asked)

Report: one line per gate (green/red), then only the failures that matter,
each with `path:line` and the shortest decisive error line.
