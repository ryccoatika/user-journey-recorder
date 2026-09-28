---
description: Build, install, launch, and screenshot the app on the test device to verify a UI change
argument-hint: "[screen or thing to check, e.g. 'detail timeline' or 'dark mode transition']"
---

Verify the current UI on the test device — this is the **only** time to
device-verify (it is not done automatically after UI edits).

Invoke the **`run-on-device`** skill and follow it end to end:

1. `adb devices -l` — the preferred device is **`RRCX8047V9J`**; always pass
   `-s RRCX8047V9J`. If it isn't attached, stop and ask the user to connect it
   (do NOT fall back to another attached serial).
2. `./gradlew :app:assembleDebug`
3. `adb -s RRCX8047V9J install -r app/build/outputs/apk/debug/app-debug.apk`
4. Launch: `adb -s RRCX8047V9J shell am start -n com.ryccoatika.journeyrecorder/.ui.MainActivity`
5. Drive to and screenshot the relevant screen(s), **Read each PNG**, and
   report what you actually see. Check dark mode too when the change affects
   theme/transitions.

Focus of this check: **$ARGUMENTS**

If nothing was specified, screenshot Home and any screen touched by the
current diff. Save screenshots to the session scratchpad, not the repo.
