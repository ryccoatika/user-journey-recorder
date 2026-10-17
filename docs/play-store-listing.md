# Google Play Store Listing — Journey Recorder

Copy for the Play Console store listing. Targeted at **QA engineers, testers,
and developers** who want to capture a user flow in any app and turn it into
test automation. Keywords are woven in naturally (Play policy forbids keyword
stuffing / repetition).

---

## Short description
*(Play limit: 80 characters. This one: 71.)*

```
Record a flow in any app and export it as ready-to-automate test steps
```

**Alternates (all ≤ 80):**
- `Record user journeys in any app, export the steps as Markdown for test cases` (76)
- `Capture taps & screens in any app. Export Markdown test steps for automation.` (77)

---

## Full description
*(Play limit: 4000 characters. This one: ~3,220.)*

```
Turning a manual test into an automated one usually means writing every tap and field by hand. Journey Recorder does the tedious part for you: record yourself walking through a flow in any app, review the steps, and export a clean Markdown script you can hand to an AI agent or an engineer to turn into a Maestro, Appium, or Espresso test.

Perfect for QA engineers, manual testers, and developers who want repeatable test cases without transcribing flows by hand.

▶️ RECORD ANY FLOW
Pick the app you want to record, start the recording, and walk through the journey. Journey Recorder captures every tap, long-press, text entry, scroll, and screen change — and keeps them in the exact order you performed them.

📝 EXPORT READY-TO-AUTOMATE STEPS
Get a numbered, screen-by-screen Markdown step list with element ids, labels, and bounds — plus app, device, and Android version in the header. Share it anywhere or save it straight to your Downloads.

♿ ACCESSIBILITY SERVICE API — HOW AND WHY IT'S USED
Journey Recorder uses Android's AccessibilityService API. It is the app's core feature: recording a journey in another app is not possible without it. Journey Recorder is a testing tool, not an assistive tool for people with disabilities.
• What it reads: only while a recording you started is running, and only in the app you picked — your taps, long-presses, scrolls, screen changes, the labels/ids/positions of the controls you touch, and the text you type (passwords and sensitive fields are masked and never stored).
• Why: to turn your journey into a reviewable, exportable list of test steps, and to show the floating stop button over other apps.
• Sharing: recordings stay on your device and are never uploaded; they leave only when you export or share them. The service never changes your settings, never performs actions on your behalf, and never records call audio.
• Consent: the app shows a disclosure and asks you to Agree or Decline before you enable the service. You can turn it off any time in Settings › Accessibility.

🧭 WORKS BEYOND NATIVE APPS
Recording native Android, Jetpack Compose, Flutter, React Native, or WebView? When an app exposes no element ids, Journey Recorder falls back to text, content descriptions, and screen coordinates — and marks those steps so your automation knows.

🫧 STAY IN CONTROL WHILE YOU RECORD
A floating bubble lets you pause, resume, or stop at any time, and an ongoing notification keeps a stop button handy. If the recorder is interrupted, your journey is kept and marked "recovered" — captured steps are never lost.

🔒 PRIVATE BY DESIGN
Your recordings stay on your device until you choose to export them. Passwords and sensitive fields (PIN, OTP, CVV, card numbers) are masked automatically before anything is saved — the raw value is never stored. Review every step and redact any value before you share it. No account, no sign-up, no ads.

✅ WHY YOU'LL LIKE IT
• Clean, modern, easy-to-use design.
• Turn a 5-minute manual run into a documented test case.
• Nothing to set up on the app you're testing.
• Built for the real frameworks QA teams use.

Stop transcribing test flows by hand. Record it once, export it, automate it — download Journey Recorder today.
```

---

## ASO notes (not part of the listing)

**Primary keywords targeted:** record user journey, record app flow, UI test
recorder, test automation, QA testing tool, export test steps, no-code test
recording, Maestro / Appium / Espresso, accessibility recorder, manual testing,
test case generator, screen recorder for testing.

**Tips**
- The lead benefit is **record a flow → export automatable steps** — it opens
  both the short and full descriptions and gets the first bullet (Play weights
  the short description and the first lines heavily; they show in search
  snippets).
- The app **title** carries the most ASO weight (30 chars). Consider:
  `Journey Recorder: QA Flows` or `Record App Flows for Testing`.
- Keep the first ~3 lines of the full description punchy — that's all users see
  before "read more".
- Don't repeat a keyword many times (Play flags stuffing); this copy keeps each
  term to a natural frequency.

**⚠️ Accessibility-use policy (read before every submission)**
The first 1.0 submission was rejected for (1) no AccessibilityService mention in
the long description and (2) a non-compliant prominent disclosure. Both are
fixed — keep them that way:
- Long description must keep the "ACCESSIBILITY SERVICE API" section above.
- In-app: `AccessibilityDisclosureDialog` (two buttons, Agree/Decline; back =
  decline) must appear before every jump to Accessibility settings while the
  service is off. Never reintroduce a direct `ACTION_ACCESSIBILITY_SETTINGS`
  launch.
- Play Console › App content › **Accessibility API** declaration: core
  functionality = recording user journeys for QA test automation; NOT an
  `isAccessibilityTool`. Do not set `android:isAccessibilityTool`.
- **Every submission needs a fresh demo video link** (YouTube unlisted is fine)
  showing: open app › tap enable › disclosure dialog with both buttons › Agree ›
  Accessibility settings › enable › record a short journey in another app ›
  stop › review › export.
- Privacy policy (`docs/privacy-policy.html`) must describe the same data the
  disclosure lists.
