---
name: deload-trigger-notes
description: How to seed/fire the E2 stall-triggered deload, and the Waydroid live-AI timeout gotcha that blocks confirming the live deload end-to-end
metadata:
  type: reference
---

E2 deload (decision M2): fires when ≥2 lifts are CONCURRENTLY stalled (DeloadPolicy.STALL_TRIGGER_COUNT=2). See [[selectors-wave3]] [[db-seeding-recipe]].

## Stall detection (what to seed)
- A lift is stalled when its Epley e1RM = weight*(1+reps/30) does NOT improve by >0.5kg across its LAST 3 completed sessions (StallDetector.STALL_WINDOW=3, IMPROVEMENT_EPSILON_KG=0.5). <3 sessions ⇒ never flagged.
- `getStrengthHistory` groups by sessionId: per session StrengthPoint = (dateMs, MAX(weightKg), reps). Working sets only (isWarmup=0), completed sessions only, weightKg>0.
- To stall a lift: seed 3 completed sessions (dated AFTER any existing higher session so they ARE the last-3 window) with IDENTICAL weight AND reps each (e.g. Squat 60x5 → e1RM 70.0; Bench 50x5 → e1RM 58.33). The pre-existing session 2 (Squat 70x5, Bench 50x9) must fall OUTSIDE the last-3 window or it injects a non-flat point.
- Need ≥2 such lifts. Sled Push has only 1 session ⇒ never counts. So seeding Squat+Bench = exactly 2 stalled ⇒ trigger fires.

## How the deload fires + persists
- Reachable trigger: Program tab → Options → "Regenerate program now" → ProgramViewModel.regenerateFullProgram. It calls computeStalledLifts(), DeloadPolicy.nextDeloadState(currentlyDeloading=active.isDeloadActive, stalledCount). If active was NOT deloading and stalledCount≥2 ⇒ isDeload=true. On generation SUCCESS it does savePlan() THEN setActiveDeload(isDeload) → programDao.setDeload(activeId, true).
- nextDeloadState EXITS deload (returns false) if currentlyDeloading was already true — deload is exactly ONE week. So to enter deload, the active program must have isDeloadActive=0 first.
- The deload flag is set ONLY on generation success (.onSuccess). If generation throws (network timeout, parse fail, or all attempts rejected) → .onFailure → Snackbar error, NO savePlan, NO setActiveDeload.

## Live generation pipeline (important for timing/timeouts)
- generateAdaptedProgram does up to MAX_GENERATION_ATTEMPTS=3 attempts; EACH attempt = 1 generate POST + 1 validate POST (validateProgram is a 2nd Claude call) + a deterministic ±10-min/day duration gate. Accept only when BOTH duration check AND LLM validator pass. So a single regenerate can be up to 6 sequential api.anthropic.com POSTs.
- OkHttp readTimeout = 120s (NetworkModule). On Waydroid here, the generate call frequently runs 60–120s and INTERMITTENTLY hits the 120s read timeout → SocketTimeoutException → whole generation fails. Watch `okhttp.OkHttpClient: --> POST ... / <-- 200 ... (Nms) / <-- HTTP FAILED: SocketTimeoutException`.
- A valid sk-ant key IS configured on the device (POST bodies ~20–26KB go out, 200s come back). Live path is real, but flaky to complete end-to-end on Waydroid due to the read-timeout.

## Verifying the deload INDICATORS deterministically (fallback the brief allows)
- Set the trigger's persisted EFFECT directly: `UPDATE programs SET isDeloadActive=1 WHERE isActive=1;` + wal_checkpoint, relaunch. Then assert Home `card_deload` (scroll down) + Program `tv_program_deload_chip` both visible. This is the user-visible-indicator check without depending on a live AI round-trip completing.

## Robustness
- Rapid spinner switching (programmatic-selection suppression guard `suppressProgramSpinner`), options-dialog open/dismiss storm, and Home↔Program tab churn: NO crash, switcher stays present, "exactly one active" invariant holds, plan rows stay separate (no merge). Guard works.
