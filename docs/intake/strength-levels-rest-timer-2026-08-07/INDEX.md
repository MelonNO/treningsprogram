# Intake batch — Rest timer ends with the session, and levels rebuilt around real strength (2026-08-07)

**Prepared for:** Project-lead orchestrator
**Source:** Two user requests (the user's own numbering 1–2, preserved as brief IDs 01–02), clarified over three rounds.
**Status:** **CONFIRMED by the user.** Replies were relayed verbatim via the coordinator; the confirming words — *"yes go"* — are the user's own, given against the full restatement of both items including every delegated decision.
**Baseline:** post-v1.36.0 (`8a89422` on `main`).

> Creating these documents does **not** dispatch the orchestrator. That is a separate user instruction.

---

## Items

| ID | Title | Type | Brief file | Status |
|----|-------|------|-----------|--------|
| 01 | The rest timer must end when the workout does | Bug (cause known) | `brief-01-rest-timer-ends-with-session.md` | Confirmed |
| 02 | Levels rebuilt around real strength, per muscle group | New feature — **replaces an existing system** | `brief-02-strength-based-levels.md` | Confirmed |

Nothing was merged. The two items share no files, no surface and no risk.

---

## Merge / cluster / parallelisation guidance

### The two items are fully independent — run them in parallel

- **01** lives entirely in `ui/log/RestTimerManager.kt`, `ui/log/RestTimerService.kt` and the
  workout-session ending path in `ui/log/LogWorkoutViewModel.kt` / `LogWorkoutFragment.kt`.
- **02** lives in the gamification, stats, achievement, profile and backup layers.

There is no overlap. They can be built concurrently by different workers with no coordination.

### Item 01 — one worker, small, self-contained

Two related fixes in the same two files (the session-end stop and the process-restart false alert).
**Do not split them across workers** — they touch the same completion-alert path and would conflict.
Small enough to be a single pass.

### Item 02 — one worker's *ownership*, but stage it internally

This is the largest item intake has produced in some time. It replaces a system rather than adding
one, and its blast radius runs through the achievement layer and the backup recompute path. Suggested
internal staging, each stage leaving the app in a working state:

1. **The rating engine** — per-group and total ratings computed from history, pure and JVM-testable.
   No UI, no achievement changes. This is where the population standards land and where the sharp
   edges are (tier boundaries, weighting, the three-month window, warm-up exclusion, missing body
   weight or sex).
2. **Backup / recompute parity** — the rating must survive export → import and must match whatever
   the recompute path produces. Do this *before* any UI, because it constrains where ratings live.
3. **Display** — the breakdown surface, the tier names, the "what would move this" line, the
   unrated-with-unlock-hint state, the weakest-group pointer.
4. **Retiring the XP level** — re-base the level-keyed achievements onto strength tiers, move the
   level-up celebration onto tier-ups, remove the Rookie→Apex titles. **Last**, because it is the
   only stage that can destroy something a user already has.

### Cross-group hazards

- **Item 02 stage 4 is the one that can lose user data.** Roughly 28 achievements are keyed to the XP
  level. Nothing already earned may be lost. Treat "already-earned achievements survive" as a
  blocking acceptance criterion, not a nicety.
- **Item 02 must not become a second level system.** The failure mode is shipping strength tiers
  *alongside* the old XP level. After this, the app has one level, and it means strength.
- **Item 01 cannot be proven by JVM tests.** Notifications, vibration and process-restart behaviour
  need the user's device check. Say so plainly when reporting it as done.
- **Neither item touches the AI generation path**, so no live Anthropic API calls are required for
  this batch. The standing frugal-API rule stands.
- **"Best qualifying set" queries in item 02 are the exact shape of the v1.36.0 bug** — a
  `MAX(weightKg)` alongside a bare `reps` column silently returned reps from an unrelated row and hid
  rep progress app-wide. Any such query here wants a deliberate look.

### Summary

| Group | Items | Parallelism | Notes |
|---|---|---|---|
| — | 01 | **Independent, one worker** | Small. Two fixes, same files, one pass. |
| — | 02 | **Independent, one owner, staged internally** | Large. Stage 4 is destructive — do it last. |

### Suggested order

1. **01** — small, contained, immediate user-visible relief from a daily annoyance.
2. **02** — start in parallel; expect it to span the batch.

---

## Confirmed decisions (the user's own answers)

**Item 01**
1. Ending the timer is **silent** — no prompt, no dialog. (1.1 = *"correct"*)
2. **Abandoning** a workout ends the timer too, not just completing it. (1.2 = *"yes"*)
3. Fixed as a **general rule** — a session ending ends the timer — so future exit routes inherit it,
   rather than patching the Finish button. (Improvement a = *"correct"*)
4. The **process-restart false alert** is swept into the same fix. (Improvement b = *"go"*)

**Item 02**
5. **Level means strength** from now on — per muscle group plus a total, replacing the XP level as
   *the* level in the app. (2.7 = c, then Q1 = a)
6. Rated against people of the user's **body weight and sex**. (2.1 = a)
7. **Age ignored** — no new profile field. (offered as the default; not contested)
8. **Current** strength, not best-ever: best qualifying set in the **last three months**; ratings can
   fall. (2.2 = a, Q4 = b)
9. **Gaining body weight can lower a rating** even with unchanged lifts. Shown explicitly as a
   consequence and accepted. (Q2 = a)
10. **Only lifts with a real population standard count.** Machine and cable work still logs and
    displays, but never moves a rating. (2.3 = a)
11. Within a group, the **main lift dominates and accessories nudge**. (2.4 = c)
12. **Six rated groups** — Chest, Back, Shoulders, Arms, Legs, Core. **Cardio gets no level.** (2.5)
13. The **total weights bigger groups more**. (2.5)
14. Unrated groups are **left out of the total**, not counted as zero. (2.6)
15. An unrated group **says what would unlock it**. (Q3 = b)
16. Displayed as **tier names** — Untrained / Beginner / Novice / Intermediate / Advanced / Elite —
    not numbers. (2.8 = b)
17. **XP survives** and keeps feeding achievements and challenges, but produces no level and no
    Rookie→Apex titles. (Q1 = a)
18. Each rated group **states what would move it** — added weight or added reps at the current weight,
    using the user's real numbers. (accepted improvement, *"go improvement (b)"*)
19. The breakdown **points out the weakest rated group** beside the existing priority-muscles setting.
    It informs only. (*"yes"*)

**Explicitly declined**
20. **AI auto-prioritisation of weak muscle groups — declined**: *"nogo for improvement a.
    prioritisiation should be user chosen."* Prioritisation stays with the user via the
    **priority-muscles setting that already exists** and already feeds the generator
    (`PreferencesManager.priorityMuscles` → `AiRepository` lines 2175, 2286–2287, 2560–2561). Do not
    rebuild it, and do not let ratings drive it.

---

## Decisions made under delegation — **veto-able**

Made under the user's **standing grant of creative freedom over the gamification layer**, shown back
in the confirmation, and not objected to:

- **D1** — The **~28 achievements keyed to the old XP level** are **re-based onto strength tiers**;
  the **~32 keyed to XP totals** are left untouched.
- **D2** — **Anything already earned stays earned**, permanently, even if the rating that earned it
  later falls.
- **D3** — The **level-up celebration now fires on reaching a new strength tier** instead of an XP
  level.
- **D4** — Missing body weight or sex → **unrated with a prompt**, never a guessed rating. This
  matches the app's existing stated refusal to substitute a "typical" height or sex
  (`PreferencesManager.kt:337`).
- **D5** — **Warm-up sets never count** toward a rating.
- **D6** — Weight × reps is read as an **estimated one-rep max**, leaning on normal strength-range
  sets rather than very high-rep ones.
- **D7** — The breakdown is reached by **tapping the level card on Profile**; the total stays where a
  level is shown today. Layout is the builder's, matching the app's conventions.
- **D8** — Whether a tier shows a **progress bar toward the next tier** is the builder's call.

---

## Assumptions applied (user may override)

- **A1** — The exact list of qualifying "well-known" lifts was never enumerated to the user. It is the
  builder's, and it should be **visible somewhere in the app**, since it determines what counts.
- **A2** — Population standards must come from a **static offline table shipped with the app**. No
  network, no API — ratings must compute in aeroplane mode. Never discussed with the user.
- **A3** — Ratings are **recomputable from logged history** rather than a running total that can
  drift. Required for backup parity.
- **A4** — Body weight is the **most recent weigh-in**, following the existing precedent of refusing a
  weigh-in that is too far from the date in question rather than fabricating one
  (`RelativeStrength.MAX_WEIGHIN_GAP_DAYS = 14`).
- **A5** — "Unrated" is a **visible, explained state**, not a hidden row.
- **A6** — The six group names are the app's **existing broad-group labels**, so the ratings speak the
  same language as the rest of the app.

---

## Open decisions the user deferred — **flag before building**

| Item | Question | Note |
|---|---|---|
| 02 | What do **bodyweight lifts** (pull-up, dip, push-up) contribute, having no kilos? | Never put to the user. These are among the few movements with real population standards — excluding them would be a real loss. Recommend counting them against body weight. **Not confirmed.** |
| 02 | Does **added weight on a bodyweight lift** count as body weight + added load? | Follows from the above; never asked. |
| 02 | Do the **Rookie→Apex titles** disappear entirely, or get reused as strength tier names? | The user picked the standard tier names for strength, which implies the old titles retire. Not explicitly confirmed. |
| 02 | Should the **total** rating also say what would move it, or only the groups? | Only the per-group line was requested. |
| 02 | Is there a **minimum amount of data** before a group is rated at all? | Never asked. A tier awarded off one lucky set may read as noise. |
| 01 | Should anything else that ends a session — a future "discard", a crash-recovery path — be enumerated now? | Covered in spirit by the general rule the user chose; no specific list was put to them. |

---

## Cross-cutting constraints

- Build via **`./build.sh`**, never `./gradlew` directly.
- **No commits or releases** unless the user asks.
- **No on-device / automated UI tests** (standing rule). Verify via build + unit tests only; the user
  does the device check. **Item 01's notification, vibration and process-restart behaviour cannot be
  proven by JVM tests** — report it as device-verifiable, not verified.
- **Live Anthropic API calls stay minimal and decision-driven.** Neither item needs one.
- **Item 02 implies schema and backup work**: exported backups must round-trip the new ratings, older
  backups must still import, and the recompute-from-history path must agree with the live path
  (`data/backup/StatsRecomputer.kt`, `CURRENT_BACKUP_VERSION = 10`).
- **Nothing already earned by the user may be lost** in item 02.
