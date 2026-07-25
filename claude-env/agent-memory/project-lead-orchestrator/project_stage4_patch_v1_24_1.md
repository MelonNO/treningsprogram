---
name: stage4-patch-v1-24-1
description: "v1.24.1 patch (2026-07-03): F3 History-stuck-on-skeletons root cause = double stateIn(WhileSubscribed) chain — NEVER layer stateIn->combine->stateIn; release-dex forensics recipe (dexdump + mapping.txt) for device-only bugs"
metadata:
  type: project
---

Stage-4 patch SHIPPED as **v1.24.1** (2026-07-03, commit 3b05561, tag/release/asset
API-verified; 840 tests both variants, +10; 0 API calls; DB unchanged v18).

**F3 root cause (the headline):** v1.24.0's History "Sessions" list was permanently stuck on
skeletons on-device (release build) because `filteredSessions` became the app's ONLY
**double-shared chain**: Room flow → `stateIn(WhileSubscribed, null)` → `combine` →
`stateIn(WhileSubscribed, null)`, with the null "loading" sentinel threaded THROUGH the combine
transform. The first Room emission never got through on-device, and since the transform returned
null while the timeline was absent, search/range changes could never produce output either — an
unrecoverable state. The pure chain PASSES on JVM (coroutines 1.8.0) and the R8 bytecode was
verified correct, so the loss is Android-runtime-conditional — but every chain confirmed working
on-device in the same APK (Home/Program/Progress) is single-layer.

**How to apply:**
- **Never layer `stateIn` → `combine`/`map` → `stateIn` in this app.** Derive with plain
  operators and share ONCE at the end. A "loading" null belongs ONLY in the final stateIn's
  initialValue, never threaded through a transform (that pattern turns any missed first emission
  into a permanently dead UI with no user-side recovery).
- The filter logic now lives in pure `domain/HistorySearch` (logical-day date matching +
  range) with `HistorySearchTest` + `F3HistoryListChainTest` locking default/search/range paths.
- **Release-dex forensics recipe** (for device-only bugs when Waydroid is off-limits): unzip
  `app-release.apk` → `~/android-sdk/build-tools/34.0.0/dexdump -d classes.dex`, resolve names
  via `app/build/outputs/mapping/release/mapping.txt` (grep "ClassName -> "), read the
  constructor wiring. Unit tests run UNMINIFIED — R8-specific breakage is invisible to the whole
  830+ suite, and release on-device coverage of any given screen may be MONTHS old (History was
  last device-verified ~v1.10.4).
- **F1 was a test-scope miss, not a bug:** brief-16's tap-outside dismiss targets the CUSTOM
  weight keypad (`layoutWeightKeypad`); the reps field's SYSTEM IME is explicitly out of scope
  in the brief. Waydroid tester exercised the system IME. If a future finding says "keypad
  doesn't dismiss", first ask WHICH keypad.
- F2: Recap top-set label reads "BW × reps" when `topWeightKg == 0`; other "kg ×" surfaces
  (PR rows, strength history) can't show 0 kg — their queries exclude it.
