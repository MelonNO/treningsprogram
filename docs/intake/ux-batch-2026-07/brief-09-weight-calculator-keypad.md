# Brief 09 — Calculator keypad (+/−) for manual weight entry

**Type:** Feature
**Cluster:** A — Log-workout screen (with item 8); one worker.
**Outcome-only:** This brief describes the end result and user experience. It deliberately does not prescribe implementation.

## Context
On the logging screen, the weight field is a manual number entry (it also has separate −/+ 2.5 kg step buttons beside it). When the user taps the weight field to type a value, a plain numeric pad appears. As the user physically loads plates, they think additively ("I have 60 on the bar, I'm adding a 5") and want the manual entry to do that arithmetic for them.

> Re-scope note: this is **not** about adding preset increment buttons (those already exist as −/+ 2.5 kg). It is specifically about the **manual-entry pad** that pops up when tapping the weight field.

## What the user wants (end result)
When the user taps the weight field to enter a value manually, the pad they get is a **calculator-style keypad that supports addition and subtraction**: they can enter a value, then press **+** or **−** and another number to adjust it arithmetically, and the field ends up with the resulting total (e.g. type `60`, then `+ 5` → `65`). Add/subtract only — **no** multiply/divide. kg only.

## Acceptance criteria (Done when …)
- Tapping the weight field for manual entry presents a keypad that includes **+** and **−** operators alongside the digits (and a decimal point, matching the existing decimal weight input).
- The user can type a number, apply **+N** or **−N**, and the weight field resolves to the arithmetic result.
- The result is a normal weight value that saves/behaves exactly like a typed weight (no change to how the set is stored or displayed).
- Subtraction cannot drive the weight below zero (it floors at 0, matching the existing − button behavior).
- The keypad handles the plate-loading flow smoothly: current value → `+ 5` → new total, and can be repeated (e.g. `+5` then `+5`).
- Values remain in **kg**; no unit toggle is introduced.

## Scope and constraints
- **In scope:** the manual weight-entry input on the logging screen becoming a +/− calculator pad.
- **Out of scope:** multiply/divide; per-side plate math (the user wants the total value adjusted, not "plate on each side"); any unit (lb) toggle; changing the existing separate −/+ 2.5 kg step buttons.
- Applies to the **weight** field specifically (not reps).

## Decisions baked in
- **+ and − only.** No × ÷.
- Operates on the **total** weight value shown.
- **kg only.**
- Floors at 0 on subtraction.

## Assumptions (user may override)
- **[A-4]** The +/− acts on the single total weight number (not per-side). Per-side plate math was explicitly not requested.
- **[A-9a]** The existing separate −/+ 2.5 kg quick-step buttons remain as they are (this item is about the pop-up manual pad, not those buttons). Remove/keep them only if the user later asks.

## Considerations for whoever builds it
- Android's standard soft keyboard has no arithmetic-aware numeric mode, so this likely needs a custom entry surface for the weight field — the *how* is the orchestrator's call; the required *outcome* is the +/− behavior above.
- Same screen/layout as item 8 — coordinate as one worker.
- Keep entry fast and thumb-friendly mid-workout; don't regress the ability to just type a plain number and be done.

## Standing constraints
- Build with `./build.sh` (not `./gradlew`). No commits/releases unless asked. No on-device/automated UI tests unless asked.
