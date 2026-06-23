# Recap & Trends — Feature Spec (UX & outcomes)

**What this is:** A description of what the user should see, do, and feel — not how to build it. The feature is two screens that live under the existing **Stats** tab.

**Design intent:** Modern, glanceable, recap-first. The most useful information (how today compared, PRs) is visible immediately; depth is one tap away. Honest and non-punishing — a hard day or planned deload must never read as failure.

---

## Where it lives & how it's reached
- A **new menu named "Recap"** is added under the Stats tab, alongside the existing Stats / History / Progress menus.
- Entering Recap (by any route) keeps the **Stats** tab highlighted.
- **Entry points into the Recap screen:**
  - The **Home post-workout button** → opens the **latest** completed session's recap.
  - The **"Recap" menu** under Stats → opens the latest session by default.
  - The **Workout Complete modal** gains a **"View full analysis"** action → opens the recap. (The recap is the expanded, persistent version of that modal — the two must feel like one continuous thing, not two different versions of the same numbers.)
  - Tapping a session in **History** → opens **that** session's recap.
- A **session selector** lets the user switch to older sessions. Default is always the **last performed** session.

---

## Screen 1 — Recap (session-scoped)
**Purpose for the user:** "How did this session go, and how does it compare to before?"

**Header:** the session's date/name, focus (e.g. "Arms / Chest"), and duration, with a clear, easy session-selector control.

**Sections, in priority order (lead with actionable; keep vanity subordinate).** For a **strength** session:

1. **Per-exercise "vs last time" deltas — the headline.** For each exercise, what was done this session versus the last time it was performed, shown as a clear, glanceable up/down delta (e.g. "Bench 80 kg ×5 — +2.5 kg vs last"). This is the single most valuable thing on the screen and should be impossible to miss.
2. **Personal records.** PRs **hit this session** are celebrated prominently. For exercises where no PR was set this session, show the **existing PR and when it was set** ("PR: 82.5 kg · 3 weeks ago"), so the user always sees both "new best!" and "your best so far."
3. **Adherence.** Sets completed vs planned, whether target reps were hit, and which exercises were skipped or swapped — i.e. did the user actually do the prescription.
4. **Effort distribution.** How the session skewed across Easy / Moderate / Hard.
5. **Per-muscle-group volume (this session).** A glanceable breakdown of which muscles this workout actually hit.
6. **Estimated vs actual duration.** "Planned ~48 min · Actual 61 min."
7. **Time breakdown — honest, not a bare average.** Show the session as **work / rest / idle**, and **rest adherence** (average rest vs the prescribed rest window), rather than a bare "average time per set." Make clear that "time on an exercise" is wall-clock (includes rest and idle), not a measure of effort.
8. **Session totals (subordinate).** Total volume, total sets, total time — present, but visually quieter than the actionable items above; treat tonnage as a fun figure, not a headline.

**Every exercise row is tappable → opens its Trends screen (Screen 2).**

**Cardio sessions/exercises** use a **cardio-appropriate recap** instead of the strength layout: duration, distance, pace/splits, and "vs last time" deltas in those terms. No tonnage, no estimated-max, no set/rep framing.

---

## Screen 2 — Trends (exercise-scoped)
**Purpose for the user:** "How am I progressing on *this* exercise over time?" Reached by tapping an exercise on the Recap. It shows the **same progress visuals the user already knows from the Progress menu, scoped to the single exercise** (so it feels familiar, not like a new thing to learn).

**Content:**
- Per-exercise progress over time, with a **selectable time window**: estimated max-strength (clearly labelled an **estimate**), best set, working weight, and volume.
- **PR history** for this exercise — each PR and when it was set.
- **Today's data point clearly marked** on the trend, so the user sees exactly where this session lands.
- Easy navigation back to the Recap, and ideally the ability to move between the session's exercises without returning first.
- **Cardio exercise** → the trend is shown in cardio terms (pace / distance / duration over time), not strength terms.

---

## States & edge cases (must feel intentional, never broken)
- **First session ever, or first time doing an exercise** → no history to compare or graph. Show a friendly "First time — baseline" state, not an empty/broken delta or chart.
- **Planned deload / a lighter or harder day** → a downward movement must be presented neutrally, never as failure.
- **Gaps** (illness, time off) → handled gracefully in trends, not shown as a broken line.
- **Warm-up sets** → excluded from volume, PRs, and progress so they don't distort the recap.
- **Trivial/empty sessions** → do not generate a nonsense recap (consistent with the app's "real workout" threshold).
- **Mixed or cardio-only days** → each exercise is shown in its appropriate (strength vs cardio) form.

---

## Look & feel (modern, intuitive)
- **Recap-first and glanceable:** the headline deltas and PRs are visible without scrolling; everything else is progressive disclosure (summary cards → tap for the Trends detail).
- **Clear, consistent delta language:** up/down indicators and color used consistently, and aligned with the app's single color language (do not reintroduce the overloaded-orange problem).
- **Tasteful PR celebration:** consistent with the completion modal's PR/achievement style — rewarding, not noisy.
- **Honest framing:** actionable metrics (deltas, PRs, adherence, rest adherence) lead; vanity metrics (total tonnage) are present but subordinate.
- **Visually consistent** with the existing Stats / History / Progress sub-screens — it should feel like a native part of the Stats tab, not a bolt-on.
- **Friendly empty/first-time states**, never a broken-looking chart.

---

## Acceptance (UX-level "done when")
- [ ] A "Recap" menu appears under Stats; entering it highlights the Stats tab.
- [ ] The Home post-workout button and the completion-modal "View full analysis" link both open the **latest** session's recap, and feel continuous with the modal.
- [ ] A session selector lets the user view older sessions; default is the last performed.
- [ ] The Recap shows, for the session: per-exercise vs-last-time deltas, PRs (this-session celebrated + existing PR with date), adherence, effort distribution, per-muscle-group volume, estimate-vs-actual duration, and a work/rest/idle + rest-adherence time view; session totals are present but subordinate.
- [ ] Tapping an exercise opens its Trends screen with progress over time, PR history, and today's point marked — visually consistent with the existing Progress menu.
- [ ] Cardio sessions/exercises use a cardio-appropriate recap and trend (duration/distance/pace), not the strength layout.
- [ ] First-time/no-history, deload, gaps, and trivial sessions all render gracefully and honestly.

---

## Decisions to settle (pick a sensible default, report it)
- The exact session-selector affordance (a date picker vs. a scrollable list of recent sessions).
- Confirm History rows also open the recap (recommended).
