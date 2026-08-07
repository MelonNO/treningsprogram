# Brief 02 — Levels rebuilt around real strength, per muscle group

**Type:** New feature — **replaces an existing system**. The largest item this batch.
**Cluster:** Independent of brief 01. Internally large; see the INDEX for staging.

> **Outcome-only.** This brief describes the end result and the user experience. It does not
> prescribe an implementation, a formula, or a data source for strength standards. The "Grounded
> facts" section at the end is orientation, not a prescribed fix.

---

## Context

Today "level" measures **activity, not strength**. XP accrues for showing up — per workout, per set,
per PR, plus challenge bonuses — and the level is a curve over total XP. It only ever goes up, and it
says nothing about how strong the user is. Roughly 200 achievements exist; a large slice of them are
keyed to that level or to XP totals.

The user's request, verbatim:

> *"redo how the level system work. It should be a breakdown were each muscle groups has its own
> level and a total level that is calculated from the levels of each muscle groups. The levels of
> each muscle should be calculated from a perspective of what is average strength of the population.
> So it is not based on how many times exercises have been logged but how many kg and reps on
> exercises can be lifted."*

## What the user wants (end result)

**"Level" means strength from now on.** Each muscle group carries its own level, derived from what the
user can actually lift measured against what is normal for the population. A total level is composed
from the group levels. XP survives as points, but it no longer produces a level.

### The end state, point by point

1. **Level = strength.** Per muscle group, plus a total. This becomes *the* level in the app,
   everywhere a level is shown today.
2. **Rated against people of the user's body weight and sex.** Not raw kilos. A rating answers
   "how do I compare to people my size and sex", so it is strength *relative to body weight*.
3. **Age is ignored.** No new profile field, no extra setup.
4. **Current strength, not best-ever.** A rating reflects the best qualifying set within the **last
   three months**. Ratings **can go down**.
5. **Gaining body weight can lower a rating**, even with unchanged lifts. The user was shown this
   consequence explicitly and accepted it — it is what relative strength means, and it must not be
   "corrected".
6. **Only lifts with a real population standard count** — the classic barbell and bodyweight
   movements (bench press, squat, deadlift, overhead press, barbell row, pull-up, dip and the like).
   Machine and cable work still logs and still displays everywhere it does today, but does not move a
   rating. Reason: "40 kg" on one machine is not "40 kg" on another, so there is no population to
   compare it against.
7. **Within a muscle group, the main lift dominates and accessories nudge.** Not a flat average, not
   winner-takes-all.
8. **Six rated groups: Chest, Back, Shoulders, Arms, Legs, Core. Cardio gets no level at all.**
9. **Total level weights bigger groups more** — legs and back count for more than core.
10. **Groups with no qualifying data are "unrated" and are left out of the total** — they do not drag
    it down as a zero.
11. **An unrated group says what would unlock it** — e.g. "log an overhead press to get a Shoulders
    rating." Since only classic lifts count, a user who only ever does machine work for a muscle would
    otherwise see a permanently blank rating with no explanation.
12. **Ratings are shown as tier names, not numbers**: Untrained / Beginner / Novice / Intermediate /
    Advanced / Elite.
13. **Each rated group shows what would move it** — e.g. *"bench 5 kg more, or 2 more reps at your
    current weight, to reach Advanced."* Accepted improvement.
14. **The breakdown points out the user's weakest rated group**, next to the existing priority-muscles
    setting, with a shortcut to it. It **informs only** — the app never chooses priorities.
15. **XP survives, the XP level does not.** XP keeps accruing exactly as it does now (workouts, sets,
    PRs, challenges) and keeps feeding achievements and challenges. But it no longer produces a level,
    and the Rookie → Apex level titles go away.

## Explicitly NOT wanted

- **The AI must not auto-prioritise weak muscle groups.** This was proposed and **declined**:
  *"nogo for improvement a. prioritisiation should be user chosen."* Prioritisation stays with the
  user, through the **priority-muscles setting that already exists** and already feeds the program
  generator. The new levels may *inform* that choice (point 14); they may not make it.
- **No guessed ratings.** If body weight or sex is unknown, the app shows "unrated" and says what is
  missing. It never substitutes a typical value — the app already refuses to fabricate a body-fat
  number for exactly this reason.

## Acceptance criteria

- **Done when** each of the six groups — Chest, Back, Shoulders, Arms, Legs, Core — shows either a
  tier name (Untrained / Beginner / Novice / Intermediate / Advanced / Elite) or an explicit
  "unrated" state, and **Cardio shows no level anywhere**.
- **Done when** a total level is shown as a tier name, composed from the rated groups only, with
  larger groups carrying more weight than smaller ones.
- **Done when** a rating changes only in response to what was lifted (kg × reps) and the user's body
  weight and sex — never in response to how many sessions were logged.
- **Done when** logging a heavier or higher-rep qualifying set can raise a group's tier, and letting
  three months pass without a qualifying set can lower it.
- **Done when** a group with no qualifying lift in the window shows as unrated, is excluded from the
  total, and names a specific lift that would unlock it.
- **Done when** machine and cable work never changes a rating, while continuing to appear in history,
  stats and progress exactly as it does today.
- **Done when** each rated group states what would move it to the next tier, in terms of added weight
  **or** added reps at the current weight, using the user's actual numbers — not a generic
  explanation of the method.
- **Done when** the breakdown names the user's weakest rated group and offers a route to the existing
  priority-muscles setting, without changing it automatically.
- **Done when** warm-up sets never contribute to any rating.
- **Done when** missing body weight or missing sex produces an unrated state with a clear prompt to
  supply what is missing — never an estimated rating.
- **Done when** XP still accrues from workouts, sets, PRs and challenges exactly as before, and no
  level or Rookie→Apex title is derived from it any more.
- **Done when** an existing user's achievements that were already earned remain earned after the
  change, including any keyed to the old XP level.
- **Done when** a backup exported before this change still imports, and a backup exported after it
  round-trips without a user's ratings or achievements changing across export → import.
- **Done when** the tier boundaries and the weighting are covered by unit tests with hand-checkable
  values, including: a lift that sits exactly on a tier boundary, a body-weight change that moves a
  rating, an unrated group's exclusion from the total, and a warm-up set that is ignored.

## Scope and constraints

**In scope**
- Per-group and total strength ratings, their display, and the "what would move this" line.
- Retiring the XP-derived level and its titles, while keeping XP itself.
- Re-homing the achievements that were keyed to the XP level.
- Whatever profile prompting is needed when body weight or sex is missing.

**Out of scope**
- Changing how XP is earned. The amounts and sources stay as they are.
- Changing the program generator's behaviour based on ratings (explicitly declined).
- Adding an age field.
- Any change to how machine/cable work is logged or displayed.
- The pre-existing "PR widget counts warm-up sets" bug — related in spirit, not this item's job.

**Hard constraints**
- Build via **`./build.sh`**, never `./gradlew` directly.
- **No commits or releases** unless the user asks.
- **No on-device or automated UI tests** (standing rule). Verify via build + unit tests; the user does
  the device check.
- **Backup/export compatibility is mandatory** — older backups must import, and the recompute path
  that rebuilds stats from history after an import must not produce different ratings from the live
  path. Whatever the new ratings are, they must survive an export → import round-trip unchanged.
- **No live Anthropic API calls are needed for this item.** It touches no generation path. Keep the
  standing frugal-API rule.

## Decisions baked in (the user's own answers)

| # | Decision | Source |
|---|---|---|
| 1 | Rated against **body weight and sex**; unrated if either is missing | 2.1 = a |
| 2 | **Age ignored**, no new profile field | offered as the default, not contested |
| 3 | **Current** strength; ratings can fall | 2.2 = a |
| 4 | Only **well-known lifts** count; machines/cables do not | 2.3 = a |
| 5 | **Main lift dominates, accessories nudge** | 2.4 = c |
| 6 | **7 broad groups, minus Cardio** → six rated groups | 2.5 |
| 7 | **Bigger groups weigh more** in the total | 2.5 |
| 8 | Unrated groups **left out** of the total | 2.6 |
| 9 | **XP stays**, but "level" now means strength | 2.7 = c, then Q1 = a |
| 10 | **Tier names**, no numbers | 2.8 = b |
| 11 | Bodyweight gain lowering a rating is **accepted, not a bug** | Q2 = a |
| 12 | Unrated groups **say what unlocks them** | Q3 = b |
| 13 | Window for "now" = **3 months** | Q4 = b |
| 14 | **"What would move this level"** line — accepted improvement | *"go improvement (b)"* |
| 15 | **Weakest-group pointer** next to the priority setting | *"yes"* |
| 16 | AI auto-prioritisation **declined** | *"nogo for improvement a"* |

## Decisions made under delegation — **veto-able**

Made under the user's standing grant of creative freedom over the gamification layer, and shown back
to them in the confirmation without objection:

- **D1** — The **~28 achievements keyed to the old XP level** are **re-based onto strength tiers**
  (e.g. "reach Intermediate in any group", "reach Advanced overall"). The **~32 keyed to XP totals**
  are left exactly as they are.
- **D2** — **Anything already earned stays earned**, permanently, even if the rating that earned it
  later falls. Achievements are a record of what happened, not a live status.
- **D3** — The existing **level-up celebration now fires on reaching a new strength tier** instead of
  an XP level.
- **D4** — Missing body weight or sex → **unrated with a prompt**, never a guessed rating.
- **D5** — **Warm-up sets never count** toward a rating.
- **D6** — Strength from weight × reps is read as an **estimated one-rep max**, leaning on sets in a
  normal strength range rather than very high-rep sets, which estimate poorly.
- **D7** — The breakdown is reached by **tapping the level card on Profile**; the total level stays
  where a level is shown today. Layout is the builder's, matching the app's conventions.
- **D8** — Whether a tier also shows a **progress bar toward the next tier** is the builder's call.

## Assumptions applied (user may override)

- **A1** — Which specific lifts qualify as "well-known" was never enumerated to the user. The examples
  above (bench, squat, deadlift, overhead press, barbell row, pull-up, dip) are illustrative. The
  builder chooses the qualifying set, and it should be **visible to the user somewhere**, since it
  determines what counts.
- **A2** — The **source of the population standards** was never discussed. It must be a static,
  offline table shipped with the app — no network call, no API. Ratings must be computable with the
  phone in aeroplane mode.
- **A3** — Ratings are assumed to be **recomputable from logged history at any time** rather than
  being a running total that can drift. This matters for backup/import parity.
- **A4** — Body weight is assumed to be the **user's most recent weigh-in**, with the app already
  having a precedent for refusing to use a weigh-in that is too far from the date in question rather
  than fabricating one.
- **A5** — "Unrated" is assumed to be a **visible, explained state**, not a hidden row.
- **A6** — The six group names are assumed to be the app's existing broad-group labels, so ratings
  line up with the muscle-group language used elsewhere in the app.

## Open decisions the user deferred — **flag before building**

| Question | Note |
|---|---|
| What does a **bodyweight lift** (pull-up, dip, push-up) contribute, given it has no kilos? | Never put to the user. These are among the few movements with real population standards, so excluding them would be a loss. Recommend counting them, expressed against body weight — **not confirmed.** |
| Should **added weight** on a bodyweight lift (weighted pull-up) count as body weight + added load? | Follows from the above; never asked. |
| What happens to the **XP level's Rookie→Apex titles as a concept** — gone entirely, or reused as the strength tier names? | The user chose the standard tier names for strength, which implies the old titles simply retire. Not explicitly confirmed. |
| Should the **total** rating also state what would move it, or only the individual groups? | Only the per-group line was requested. |
| Is there a **minimum amount of data** before a group is rated at all (e.g. a single set of one lift is enough)? | Never asked. A rating from one lucky set may read as noise. |

## Considerations for whoever builds it

- **This item is a replacement, not an addition.** The failure mode to avoid is shipping a second
  level system alongside the old one. After this, the app has exactly one thing called a "level", and
  it means strength.
- **The blast radius is the achievement layer.** Roughly 200 achievements exist; a large slice read
  the XP level. Nothing already earned may be lost.
- **Backup parity is the sharp edge.** There is an existing recompute path that rebuilds stats from
  session history after an import and derives the level from XP. Whatever replaces that derivation
  has to produce identical results on both sides, or imported backups will show different ratings from
  the device they came from.
- **The app already has most of the raw ingredients** — an estimated-1RM formula, a body-weight-
  relative strength calculation, a per-exercise muscle map with primary/synergist weightings, and a
  warm-up-excluding strength history query. See grounded facts. What does not exist is the population
  standard itself.
- **Be careful with "best set" queries.** A bug shipped in v1.36.0 was caused by a query taking
  `MAX(weightKg)` alongside a bare `reps` column, which silently returned the reps from an unrelated
  row and hid rep progress app-wide. Any "best qualifying set" lookup here is exactly that shape.
- **Tier boundaries are a product surface, not an internal detail.** Once a user sits one kilo below
  "Advanced", the exact boundary becomes very visible. Round and explain them deliberately.

---

## Grounded facts (verified 2026-08-07 — orientation only, not a prescribed fix)

**What exists that this can build on**

- `app/src/main/java/com/migul/treningsprogram/domain/Epley.kt` — the app's **single** e1RM formula
  (`1RM ≈ weight × (1 + reps / 30)`), already used by goals and deload logic. Do not introduce a
  second one.
- `domain/RelativeStrength.kt` — already computes **e1RM ÷ body weight** over time. Its rules are
  directly relevant precedent: warm-ups excluded upstream, bodyweight-only (0 kg) sessions skipped,
  and body weight taken from the **nearest weigh-in within ±14 days — never a fabricated one**
  (`MAX_WEIGHIN_GAP_DAYS = 14`). It even carries `MILESTONES = 0.5×…2× body weight`, described in
  its own comment as "classic strength-standard ratios".
- `data/MuscleClassifier.kt` — broad groups returned by `fromName` are **Chest, Back, Shoulders, Arms,
  Legs, Core, Cardio**; `ALL_FINE_MUSCLES` (lines 200–208) lists 14 fine labels; `broadGroupFor`
  (line 214) maps fine → broad. `fineMusclesFor` returns **weighted contributions: 1.0 primary mover,
  0.6 major synergist, 0.3 minor** — this is exactly the "main lift dominates, accessories nudge"
  shape the user asked for.
- `data/db/entity/WorkoutSet.kt` — carries `reps`, `weightKg`, **`isWarmup`**, `rpeLabel`,
  `muscleGroup`, `loggedAtMs`. The warm-up exclusion has a field to hang off.
- `data/db/entity/BodyMeasurement.kt` — `dateMs` + `weightKg`. The weigh-in source.
- `data/preferences/PreferencesManager.kt` — `heightCm` (line 338) and `sex` (line 343) already exist
  as profile settings. The comment at line 337 is the app's stated no-fabrication rule: it refuses to
  *"substitute a 'typical' height or sex, because that would silently fabricate a body-fat figure."*
  The same principle applies here.

**What is being replaced**

- `data/db/entity/UserStats.kt` — single row (`id = 1`) holding `totalXp`, `level`, `currentStreak`,
  `bestStreak`, `totalWorkouts`, `totalPrs`, `lastWorkoutDateMs`.
- `data/repository/GamificationRepository.kt` — `xpForLevel(level) = ((level - 1)²) × 200`
  (line 523); `levelTitle` (lines 537–562) runs **"Rookie" (1) … "Apex" (100+)**.
- `data/backup/StatsRecomputer.kt` — rebuilds `totalXp` from sessions, sets, PRs and perfect weeks
  (lines 71–138) and sets `level = GamificationRepository.xpToLevel(totalXp)` (line 143). **This is
  the backup-merge parity path.**
- `domain/AchievementCatalog.kt` — `Kind.LEVEL -> stats.level` (line 159); level achievements are
  matched both by explicit entries and by an id-prefix rule, `id.startsWith("level_")` (line 137).
- `data/db/AppDatabase.kt` seeds **200 achievements**. Counted by id: **18 begin `level_`** and
  **21 begin `xp_`**; the catalog adds **10 further named LEVEL entries** and **11 named XP entries**.
  So roughly **28 are keyed to the XP level and 32 to XP totals** — *confirm the exact set before
  touching them; these counts are indicative.*
- `data/backup/BackupModels.kt:59` — `CURRENT_BACKUP_VERSION = 10`.
- Level is displayed in `ui/home/HomeFragment.kt`, `ui/profile/ProfileFragment.kt` and
  `ui/profile/ProfileViewModel.kt`.

**The prioritisation control that already exists (so it is not rebuilt)**

- `PreferencesManager.priorityMuscles` (lines 168–170) — a user-set preference.
- `data/repository/AiRepository.kt` feeds it to the generator: line 2175 (`Priority muscle groups:`),
  lines 2286–2287 (*"allocate at least 2 extra sets compared to non-priority groups. Train them twice
  per week."*) and lines 2560–2561. **This is the setting the weakest-group pointer should link to.**
