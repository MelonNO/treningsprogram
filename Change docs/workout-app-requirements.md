# Workout App — Requirements (Proof of Concept)

**What this is:** A statement of the results the app must deliver. It describes *what the user should be able to see and do* and how the app should behave — not how to build it. Scope is a personal proof of concept; some items are explicitly deferred.

**Global character:** Modern, sleek, gamified — but gamification rewards *adherence to the plan, progress, and consistency over weeks*, never raw daily activity, and never punishes rest. Key information is shown up front; detail is available on demand, not dumped all at once.

---

## Tabs

### Home
A welcoming, visually polished landing screen that answers "what do I do right now?" and motivates.
- Shows a glanceable summary of genuinely meaningful stats (vanity figures kept out of the spotlight).
- Summarizes today's training, with **distinct states** for: a planned training day (what's on), a rest day (rest messaging, not a blank/failure state), and a day already completed (done state).
- Offers one clear primary action (e.g. start today's workout).
- Surfaces gamified elements — progress toward goals, recent achievements/PRs, consistency — framed per the global character above.

### Program (plan + active session)
The core of the app.

**Plan view**
- Shows the full week at a glance, each day labelled by focus (e.g. Push / Legs / Cardio / Rest).
- The plan is generated and adapted from the user's own past performance and stated goals.
- Tapping a day reveals that day's detailed prescribed workout (exercises, target sets/reps/loads).
- Because the plan adapts to performance, **later-week days are tentative and may change** after sessions are completed; near-term days are firmer. The user can tell which is which.
- The user can override the plan: swap exercises, adjust, or skip days, and the plan adapts *with* the user rather than discarding their changes.
- The user can start an **off-plan / freestyle session** to train something not prescribed.

**Active session (after "Start workout")**
- Collapses to a focused, **one-exercise-at-a-time** view for minimal distraction.
- Shows the exercise with a consistent illustration, the target, and **adjustable weight and reps**.
- Each completed set is logged with a deliberate confirming **animation/transition that makes accidental double-logging difficult**.
- Sets accumulate for the current exercise each time one is logged; **sets are counted until the user presses "Next exercise."** No fixed slot count is forced on the user.
- After each logged set, an **adjustable rest timer** appears, with a notification when rest ends (so the phone can be pocketed) and the ability to skip or extend it.
- The app still knows the prescribed target and can show progress against it and record actual-vs-intended for later analysis and plan adaptation — without constraining how the user logs.
- The flow does not break in a real gym: the user can **skip an exercise and return to it, reorder, go back to a previous exercise to fix a mistake**, and follow grouped exercises (supersets/circuits).
- A lightweight indicator shows how much of the session remains.
- Warm-up sets are distinguishable from working sets.
- The user can quickly work out barbell plate loading.
- After a set, the user can optionally give a quick **"how hard did that feel?" signal**, so the plan can adapt more intelligently than just hit-or-missed reps.

### Stats / Logs
(Full detail in the separate Stats spec; summarized here so this document stands alone.)
- **History:** a complete, accurate record of every past session, openable to exact detail, **editable and backdatable**; searchable by exercise and date; with a training-frequency view.
- **Progress:** per-exercise strength trends over time; volume trends (per exercise, muscle group, overall); personal records and when achieved; bodyweight/measurement trends and optional progress-photo comparison; a selectable time window. Any estimated strength figure is **clearly shown as an estimate** — reliable enough for trends, never implying false precision.
- **Stats:** meaningful totals (vanity clearly separated); consistency/frequency and streaks, shown gently and not punishing rest; **muscle-group balance highlighting neglected areas** (the key actionable insight); rep-range distribution; basic session patterns.

### User
Settings and administrative area.
- Profile, goals, experience level, available equipment, and training-days schedule. These also **seed the plan for a brand-new user** who has no history yet.
- Units and display preferences.
- Gamification preferences (the user can tone it down or dismiss it).
- Bodyweight / body-measurement entry.
- Data export / ownership — the user can get their full history out in a reusable form.
- Integrations (e.g. health platforms) — *deferred*.

---

## Cross-cutting behaviour (results, not methods)
- **Works fully offline.** Viewing and logging never depend on a connection (gym basements).
- **Active workouts survive interruptions.** Phone lock, app being killed, or a crash mid-session must not lose logged sets — the session resumes exactly where it was.
- **Plan and logs stay consistent.** What was planned and what was actually done always agree, so stats and the adaptive plan reflect reality.
- **Honest charts.** Gaps and deliberate deloads/breaks are never presented as failure or broken trends.
- **Edits stay consistent.** Correcting or backdating entries leaves all summaries and records correct.
- **Not overwhelming.** Default views show a few key things; everything else is one step deeper.

---

## Safety & wellbeing outcomes
- The plan must not progress load unsafely fast; it schedules rest/deloads; it responds sensibly to missed sessions and to user-reported difficulty or discomfort; it never prescribes movements the user lacks equipment for.
- A brand-new user gets a sensible starting plan from their onboarding answers, with a calibration period before fine-grained adaptation kicks in.
- Gamification must never encourage training through pain or skipping needed rest.

---

## Illustrations (deferred / POC note)
- **Desired result:** every exercise shows a clear movement illustration in a single, consistent visual style.
- **For the POC:** illustrations come from an existing free exercise library, chosen for whatever is easiest to get working; licensing is set aside while the app is personal-use only. They are intended to be **replaced by in-house artwork later**, at which point visual consistency is fully under control.
- The set of exercises the plan can prescribe should stay within what the chosen illustration source covers, so there are no missing visuals.

---

## Suggested priority for the proof of concept
*Not a requirement — a sensible order so something works early.*
- **First (make it usable):** week/day plan display, focused one-exercise logging with the confirm-animation + rest timer + "Next exercise", a basic editable history, offline operation, and reliable mid-workout resume.
- **Then (make it smart):** performance-based plan generation and adaptation, the difficulty signal, tentative-vs-committed future days, muscle-balance stats.
- **Later (make it shine):** full gamification, progress photos, integrations/export, in-house illustrations.

---

## Open decisions to settle before/while building
- How much the plan *regenerates* vs. nudges a stable template week to week (affects how stable the experience feels).
- Whether the difficulty signal is required or optional (affects adaptation quality vs. logging friction).
- How aggressive gamification is by default for the intended user.
