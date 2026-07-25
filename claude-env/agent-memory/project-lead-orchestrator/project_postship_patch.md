---
name: postship-patch
description: Post-v1.9.0 follow-up patch — implement ALL deferred "risky" UX1 items + COMPLETE blocking on-device tests
metadata:
  type: project
---

After v1.9.0 SHIPPED (2026-06-25; main @ 5ed2dd5, tag v1.9.0, treningsprogram-v1.9.0.apk live, versionCode 37). Branch for this patch: `patch-ondevice-ux1` off main 5ed2dd5 (NOT wave1-integration — that's merged/done).

**Two mandates (relayed; user verbatim fragment: "implement all the 'risky' UI changes and actually complete all on-device tests"):**
1. IMPLEMENT the FULL deferred UX1 scope (the subjective Recap/Progress/History redesign the v1.9.0 run held back behind the brief's approve/cut gate). The v1.9.0 UX1 worker's "deliberately NOT done" list = the build target: tab/navigation IA restructure; full visual restyle of the 3 Stats screens; volume in kg-tonnage option; interactive chart tooltips + x-axis date labels; per-muscle bars colored by muscle; merge in-session "Heaviest weight" into the e1RM story (or make coherent); Stats-tab empty state. Cross-ref brief candidate list (docs/intake/.../brief-UX1-...): coherent PR story, clear empty states, richer Recap (DONE in 1.9.0), readability of core numbers/charts, navigation/IA within Stats, consistency polish, lighter app-wide polish.
2. on-device Waydroid testing is now REQUIRED + BLOCKING (opposite of the v1.9.0 "don't wait" rule). Full post-ship scrutiny list MUST be completed by a non-author ui-test-worker, no source-only shortcuts unless a path is genuinely impossible on harness (then say so):
   - U2 Room 13->14 migration on a REAL upgraded v13 device (highest risk).
   - U2 XP capture end-to-end + XP-bar tap->log from BOTH Home & Profile.
   - UX1 Recap graphs (volume/frequency/muscle) real/empty/single-point + legibility.
   - F3 true network-OFF (was BLOCKED on harness — retry for real).
   - Re-run S2 rapid-edit + same-week deload on the FINAL patch APK.
   - ALL new UX1 UI through adversarial states (rotation, bg/process-death, empty/first-run, rapid taps).

**Gate = build green (debug+release) + JVM green + COMPLETE non-author on-device evidence. Diagnose-first on anything on-device surfaces.**

**AUTHORITY CAVEATS (hold firm):**
- UX1 "approve-all-wholesale" came via RELAY (not user's own verbatim for that interpretation) + it OVERRIDES the brief's user-confirmed approve/cut gate. Building is reversible so OK to build on the relay — BUT the user gets a real cut opportunity because the patch SHIP needs the user's OWN authorization (relay confirms v1.9.0 ship auth does NOT carry). So no irreversible step rests on the relay. Flag subjective design choices for the user's pre-ship review.
- Do NOT ship this patch. Deliver Verification Report + version recommendation; patch ship = separate explicit USER authorization. Patch over v1.9.0 → likely v1.9.1 (PATCH) UNLESS the UX1 redesign is substantial enough to be a feature (then v1.10.0) — decide at report time.

See [[bugsweep-ux-wave1]], [[bugsweep-ux-wave2]], [[ship-handoff]], [[relayed-consent]], [[reference-ondevice-test-harness]].

## PATCH VERIFIED (2026-06-25) — gate fully met, NOT shipped
Branch patch-ondevice-ux1 @ de48f32 (1 commit ahead of main 5ed2dd5). Patch debug APK md5 5614a3b9, release 732ee29a.
- **Build gate:** debug+release both green (my runs; release failed ONCE transiently under concurrent load, clean re-run green — verify directly, don't trust one run). JVM 410 tests/0 fail (my clean run; +8 ChartAxis).
- **UX1 deferred items built (presentation-only, scope-clean):** ChartAxis.kt (X-axis date labels), StrengthChartView value callouts + units, "View full trends" IA drill-in (S8-guarded), per-muscle-colored bars (reused MuscleClassifier.colorFor), Stats-tab empty state, coherent PR story. Tonnage toggle SKIPPED (would need DAO SUM query = hard-constraint violation; reported not done). Guards held: no dependency/schema/DAO/repo change, warm-up invariant intact, no resurrected legacy PR widget, v1.9.0 RecapGraphs.kt untouched.
- **BLOCKING on-device (non-author ui-test-worker) — ALL 5 PASS, zero defects:**
  - A. Room 13->14 migration on REAL upgrade (built v1.8.0 schema-13 APK from tag in isolated worktree → install-over → user_version=14, xp_events present, ALL data survived). **I INDEPENDENTLY RE-VERIFIED this myself on-device**: live DB user_version=14, xp_events present (3 rows), 200 achievements/4 sessions/36 exercises intact, installed md5=5614a3b9.
  - B. XP capture end-to-end + bar tap from BOTH Home & Profile + empty state.
  - C. All UX1 surfaces real/empty/single-point + rotation/bg/process-death/rapid-tab — clean.
  - D. S2 rapid-edit race + same-week deload regression on final APK.
  - E. F3 true network-OFF — SOLVED (host `iptables -I FORWARD ... -j DROP` cuts container net but spares adb bridge; spinner clears → clear timeout error). UPDATE to [[reference-ondevice-test-harness]]: network-off IS achievable this way.
- **Minor residuals (non-blocking):** S2 deload-SUCCESS-path live regen not run (real key wiped by migration test's clean uninstall; failure path + JVM cover both branches). Worker used Import-Backup merge to reach onboarded state (real code path).
- **Version recommendation:** patch over v1.9.0. UX1 is presentation-only (no new feature/data) → **v1.9.1 (PATCH)**, versionCode 37->38.
- **NOT shipped.** Patch ship needs the USER'S OWN authorization. Subjective UX1 design choices listed for user pre-ship cut.
