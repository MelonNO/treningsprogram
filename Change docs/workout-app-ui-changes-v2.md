# Workout App — Change Set v2 (for a coding agent)

**Purpose:** Next round of improvements based on the current build. Each item states the **desired end result** plus a "Done when" check. It does not prescribe implementation. "Now" lines describe current observed behaviour only to locate the work.

**Out of scope:** Architecture, data model, libraries, styling method.

---

## Resolved in this build — keep, do not regress
- Recent Workouts now summarise content (focus + exercise + set counts), not just duration.
- Completion modal now shows real session output (exercises, sets, total volume), Personal Records, and Achievement-unlock — keep all of it.
- Rest timer with "AI suggested" value and Skip / −30s / +30s adjustment — keep.
- "Next" is now visually secondary to the primary LOG SET action — keep.
- Today's Plan now has an actionable "View Today's Session" button — keep.
- Time estimates rounded to whole minutes — keep (extend the same principle to weights, see P3-10).
- Profile/Stats screen with totals, PR list, and Achievements (x/14) — keep.
- Session progress bar fills through the workout — keep.

---

## P1 — Correctness (do first)

**2. Week progress bar reflects actual completion.** *(carried over — still unfixed)*
Now: the bar under the day pills appears full while the week shows 0/20 done.
Desired result: the bar's fill matches the "X / Y done" figure.
Done when: the bar is near-empty at 0/20 and fills as sessions complete.

**3. Exercise images match the exercise and are legible.**
Now: the Log image is a busy real gym photo that doesn't clearly depict the named movement (e.g. "Barbell Row" shows an unrelated/cluttered scene).
Desired result: each exercise shows an image that clearly depicts that exercise, readable at a glance, in a consistent visual style across all exercises.
Done when: every exercise's image matches its name and reads clearly; no cluttered or irrelevant stock photos.
*Note: illustration source is a deferred/POC choice. Flagging only that the current photos read worse than the earlier line-art for legibility/consistency — pick whichever source meets the result.*

**4. Weekly Challenges track real progress and self-complete.**
Now: challenge items appear as empty circles; unclear if they reflect actual progress.
Desired result: each challenge reflects current progress and marks itself complete (awarding its XP) when its condition is met (e.g. "Log 10+ sets in one session").
Done when: meeting a challenge's condition visibly completes it and grants the XP.

---

## P2 — Clarity

**5. Progress bars distinguish filled from remaining.** *(carried over)*
Now: across Home XP, level bar, completion modal, and Profile, the remaining portion is orange and reads as a second value.
Desired result: completed portion uses the accent; remaining portion uses a neutral/muted track.
Done when: no progress bar uses two saturated colours implying two metrics.

**6. Each colour has one consistent meaning.** *(carried over)*
Now: orange is used for XP/rewards, weight values, progress-bar remainders, and stepper buttons.
Desired result: one accent for primary actions, one colour reserved for rewards/XP, neutral for value tags and tracks; no colour with multiple unrelated meanings.
Done when: colour meaning is consistent across all screens.

**7. Exercise cards use informative, consistent identifiers.** *(carried over)*
Now: cards still show cryptic 2-letter badges (BA / AR) that don't clearly map to the exercise.
Desired result: each card shows a meaningful, consistent visual identifier for the exercise, ideally matching the Log-screen image.
Done when: the card visual is meaningful and consistent with the active-workout screen.

**8. One timer on the Log screen.** *(carried over)*
Now: two timers show the same value ("00:10" top + "0:10 on this exercise").
Desired result: a single elapsed indicator; no duplicate, and no per-exercise count-up that pressures the user to rush sets.
Done when: only one timer is visible.

**9. Weekly Challenges live in one place.**
Now: the identical Weekly Challenges list appears on both Home and Profile.
Desired result: challenges have a single canonical location (or a clear, non-redundant reason to appear in both).
Done when: the user isn't shown the same challenge list twice without purpose.

---

## P3 — Polish

**10. No false precision in weights.** *(time already fixed; apply the same to weights)*
Now: weights show "80.0 kg", "90.0kg", "10.0kg" across Program, Log, PRs, and completion.
Desired result: whole-number weights show no trailing ".0"; decimals appear only when real (e.g. 22.5).
Done when: no whole-number weight shows ".0" anywhere.

**11. Correct pluralisation.**
Now: Recent Workouts shows "1 sets".
Desired result: singular/plural agree with the count ("1 set", "3 sets").
Done when: counts and their nouns agree everywhere.
