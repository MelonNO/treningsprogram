---
name: project_generation_efficiency_fix
description: v1.10.6 generation reliability fix (efficiency prompt + rest-first auto-trim); adaptive thinking tested and REJECTED
metadata: 
  node_type: memory
  type: project
  originSessionId: 0bc9a036-2db1-49b1-a569-6e553d9e82ee
---

Post-v1.10.5 bug: "Generate AI Program" ran 3 attempts, overran the 360s deadline, saved nothing ("Generation took too long and was stopped"); single-day Regenerate still worked. **SHIPPED v1.10.6 (2026-06-27, commit 48fb61b on main, tag live, asset `treningsprogram-v1.10.6.apk` md5 66f064c4…, DB unchanged, vc 44→45).** Full pipeline (intake→orchestrator→workers→build-release-shipper), release API-verified live.

Root cause (from the user's Prompt Log export): the non-thinking `claude-sonnet-4-6` generation prompt demanded heavy "silent" planning — with no thinking channel the model externalised ALL reasoning as visible output, exhausting max_tokens before emitting any JSON (the "I'll plan silently…" rambling, attempt 1 cut off mid-sentence). When retries DID produce valid JSON, the strict per-day time-budget gate rejected them for one day being over the window, and the model played **whack-a-mole** (fixes the flagged day, overshoots a different one) — for the user's 50-min/5-day/Hypertrophy profile the strict gate is **structurally unsatisfiable**, so 0/3 loops saved.

Fix (two parts, both in `AiRepository.kt`): (1) **efficiency prompt fix** — blunt JSON-only output directive on every attempt, terse one-clause notes, ≤2-sentence rationale, removed the "compute/narrate silently" invitations. Live-proven ~7-8× faster (~35s/attempt, clean JSON, 6/6) vs the old ~256-275s/no-JSON. (2) **deterministic rest-first auto-trim salvage** (`trimOverflowToWindow`, ladder REST→SETS→REMOVAL: reduce inter-set rest within the goal band 60-120s first, then drop a set, then remove a trailing accessory; guards: never the primary, day stays ≥floor, muscle stays covered, ≥4 exercises; returns null→normal reject if un-trimmable or any under-day). Wired as a FALLBACK at the terminal attempt → re-validate the trimmed plan via `validateProgram` → save with an honest "auto-trimmed to fit" note. Gate math, ±10 window, `WorkoutTimeEstimator`, 360s deadline, 3-attempt cap, maxTokens default 16384 all UNCHANGED. The user reversed their earlier "no salvage / keep gate strict" stance (Option A→B) ONCE shown the strict gate was structurally unsatisfiable for the 50-min profile.

**KEY NEGATIVE RESULT — do NOT re-try adaptive thinking on this prompt:** enabling `thinking:{type:"adaptive"}` on the generation call (with max_tokens bumped to 32000) was live A/B-tested and REGRESSED HARD — ~508-522s/gen (OVER the 360s deadline), the full 32k budget burned on thinking, **NO JSON emitted**, ~10× cost (~$0.50 vs ~$0.05). Adaptive thinking thinks unboundedly on a prompt this large and starves the output. Fully reverted before ship (generation request sends no `thinking`, max_tokens back to 16384). Opus+thinking was not tested (spend limit) — no reason to expect a slower/pricier model to fare better.

RESIDUALS: efficiency fix is live-proven; the auto-trim's full save-path end-to-end was NOT live-run (spent the API budget on the thinking A/B, then hit the monthly Claude Code spend limit) — it is thoroughly unit-tested (`G2TrimOverflowTest`, 9 tests; total suite 541 green). User verifies the real on-device save after updating (standing Waydroid-skip). Intake brief at `docs/intake/generation-efficiency-2026-06/`. [[project_generation_retry_hang_fix]] [[feedback_always_skip_waydroid]]
