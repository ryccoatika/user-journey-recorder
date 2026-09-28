---
name: run-on-device
description: Use when you need to see a UI change actually render (not just compile/test) — build the debug APK, install it on the test device, launch it, drive it, and capture screenshots via adb. Also covers enabling the accessibility service and verifying an end-to-end recording. Trigger on "run the app", "screenshot", "does it look right on device", or after any Compose UI change.
---

# Run Journey Recorder on a device

Unit tests never exercise the UI or the AccessibilityService. To confirm
behavior, install and drive the app over `adb`, then Read the screenshots.

## 0. Device

```bash
adb devices -l
```

Preferred device: **`RRCX8047V9J`** — always pass `-s RRCX8047V9J`. If other
serials are attached, do NOT use them. If none, ask the user to connect it.

## 1. Build + install

```bash
./gradlew :app:assembleDebug
adb -s RRCX8047V9J install -r app/build/outputs/apk/debug/app-debug.apk
```

`adb install` matters beyond convenience: Android 13+ blocks the
accessibility toggle for browser/file-manager sideloads ("Restricted
setting") — adb-installed builds are exempt.

## 2. Launch

```bash
adb -s RRCX8047V9J shell am start -n com.ryccoatika.journeyrecorder/.ui.MainActivity
adb -s RRCX8047V9J shell sleep 2   # device-side sleep; host `sleep` may be blocked
```

## 3. Screenshot → look at it

```bash
adb -s RRCX8047V9J exec-out screencap -p > /path/to/scratchpad/shot.png
```

Then **Read the PNG** — the point is to visually inspect, not assume.

## 4. Drive the UI

`adb shell input` uses **real device pixels** (RRCX8047V9J is 1080×2340). The
Read tool reports the displayed size and a multiplier — multiply eyeballed
coordinates back up before tapping.

```bash
adb -s RRCX8047V9J shell input tap <x> <y>
adb -s RRCX8047V9J shell input swipe <x1> <y1> <x2> <y2> <ms>
adb -s RRCX8047V9J shell input keyevent KEYCODE_BACK
adb -s RRCX8047V9J shell input text 'hello'   # types into focused field
```

## 5. Verify an end-to-end recording (when capture is what changed)

The accessibility service can be enabled from adb on a debug device:

```bash
adb -s RRCX8047V9J shell settings put secure enabled_accessibility_services \
  com.ryccoatika.journeyrecorder/com.ryccoatika.journeyrecorder.recorder.JourneyAccessibilityService
adb -s RRCX8047V9J shell settings put secure accessibility_enabled 1
```

Then: Setup → pick a simple target (Settings/Calculator) → Start → drive a few
taps + one text input in the target → tap the bubble → ✓ → open the journey in
Detail and screenshot the step list. Check: steps present and ordered, typed
text captured (or masked for password fields), no bubble-tap step recorded.

## 6. Verify transitions / motion

Screenshots miss motion. Record and frame-dump instead:

```bash
adb -s RRCX8047V9J shell screenrecord --time-limit 6 /data/local/tmp/nav.mp4 &
# ...drive the navigation while it records...
adb -s RRCX8047V9J pull /data/local/tmp/nav.mp4 <scratchpad>/
ffmpeg -i nav.mp4 -vf "select='gt(scene,0.02)',scale=300:-1" -vsync vfr f_%02d.jpg
```

Read the frames; for jank numbers use
`adb -s RRCX8047V9J shell dumpsys gfxinfo com.ryccoatika.journeyrecorder reset`
before the interaction and dump (no reset) after. Clean up `/data/local/tmp`.

## Notes

- Package / launcher: `com.ryccoatika.journeyrecorder/.ui.MainActivity`.
- Foreground activity check:
  `adb -s RRCX8047V9J shell dumpsys activity activities | grep -m1 ResumedActivity`.
- Save screenshots to the session scratchpad, not the repo.
- Screens to spot-check after a design change: Home (cards, banner, FAB),
  Setup (checklist states, picker, start bar), Detail (timeline, redact,
  export bar), Settings, Guide. Check **dark mode** — window backgrounds must
  not flash during transitions.
