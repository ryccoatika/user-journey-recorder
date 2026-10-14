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
*(Play limit: 4000 characters. This one: ~2,350.)*

```
Turning a manual test into an automated one usually means writing every tap and field by hand. Journey Recorder does the tedious part for you: record yourself walking through a flow in any app, review the steps, and export a clean Markdown script you can hand to an AI agent or an engineer to turn into a Maestro, Appium, or Espresso test.

Perfect for QA engineers, manual testers, and developers who want repeatable test cases without transcribing flows by hand.

▶️ RECORD ANY FLOW
Pick the app you want to record, start the recording, and walk through the journey. Journey Recorder captures every tap, long-press, text entry, scroll, and screen change — and keeps them in the exact order you performed them.

📝 EXPORT READY-TO-AUTOMATE STEPS
Get a numbered, screen-by-screen Markdown step list with element ids, labels, and bounds — plus app, device, and Android version in the header. Share it anywhere or save it straight to your Downloads.

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

**⚠️ Accessibility-use policy (read before publishing)**
Journey Recorder uses the `AccessibilityService` API to record other apps. Google
Play restricts this: you must declare the accessibility use in Play Console
(App content → "Accessibility" / permissions declaration), explain in-listing
and in-app why the service is needed, and the app may face extra review or
rejection if the use isn't an accessibility aid. Make sure the listing and the
in-app Guide clearly state the recording purpose, link the privacy policy, and
keep the `IsAccessibilityTool`/permissions declarations accurate. If Play
approval is uncertain, this is designed to also work as an internal sideload /
closed-testing distribution.
