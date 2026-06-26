# B09 — Mid-week regenerate preserves logged days

**Type:** Feature
**Cluster:** AI generation seam (with B08, B10) — highest risk; coordinate; sequence after/with B10
**Outcome-only:** describes the desired end result, not the implementation.

## Context
The app already has a manual "regenerate the week" action on the Program tab. Today regenerating rebuilds the whole week. The user wants the default regenerate to keep days they've already trained and only (re)generate the rest of the week, with the AI taking the already-trained work into account. A full fresh-week regenerate (which can overwrite already-trained days' plans) should still exist, but move behind Settings. This is the highest-risk item — it sits on the hot AI-generation seam and must never destroy logged data.

## What the user wants (end result)

### Default regenerate (the Program-tab button becomes this)
- **Preserve rule:** any day that has **at least one logged exercise** is preserved (kept as-is). "Logged exercise" — not "a completed workout": even a single logged exercise on a day locks that day.
- **Per-day, not a time boundary:** every day **without** a logged exercise is regenerated — including days earlier in the current week than today that have nothing logged. Example: if only **Wednesday** has a logged exercise, regenerate **all days except Wednesday**.
- **AI rebalances around logged work:** the AI is given what was already trained and **rebalances the regenerated days around it** (muscle-group focus, volume, recovery). It is **allowed to overwrite still-unlogged planned days** — i.e. a day that already had a plan but has no logged exercise can be reworked, not just left alone. Logged days are untouched; any not-yet-logged day (even one with an existing plan) can be reworked.
- This becomes the behavior of the existing Program-tab regenerate button.

### Full fresh-week regenerate (moves to Settings)
- Regenerates **all** days, including days that already have logged exercises — but it overwrites only the **planned** exercises for those days.
- It must **never** delete the user's **logged sets, workout history, or any other user data**. Only the plan is replaced.
- Accessed from Settings (not the Program tab).

### Week model
- The current **Monday→Sunday** week model is unchanged. "The week" being regenerated is the **current** week, and the day preserved is a logged day from **this** week (not a prior week).

### Out of scope for this item
- No change to the **automatic** start-of-week generation (confirmed). This item is about the **manual** regenerate action only.

## Acceptance criteria
- Done when invoking the default regenerate keeps every day that has ≥1 logged exercise exactly as it was, and regenerates every day with no logged exercise.
- Done when the example holds: with only Wednesday logged, regenerating leaves Wednesday untouched and regenerates all other days (including earlier-in-week days with nothing logged).
- Done when the regenerated days are rebalanced by the AI around what was already trained (the AI receives the already-logged work as context), and when the AI may rework an already-planned-but-unlogged day rather than only filling empty days.
- Done when the full fresh-week regenerate (from Settings) replaces the plan for all days including logged ones, **without** deleting any logged sets/history/user data.
- Done when no path through either regenerate option deletes logged data.
- Done when the automatic start-of-week generation behaves exactly as before.

## Scope and constraints
- In scope: the manual regenerate behavior (default = preserve-logged-days on the Program tab; full fresh-week behind Settings), and feeding already-logged work into the AI as context for rebalancing.
- Out of scope: automatic start-of-week generation; the rest-day input itself (see B08, though they compose).
- Hard constraint: **logged sets / workout history / user data must never be deleted by either regenerate path.** Only planned exercises may be replaced.

## Decisions baked in
- Preserve trigger = ≥1 logged exercise on a day (not a completed workout).
- Per-day rule (not today-onward).
- AI may overwrite still-unlogged planned days to rebalance.
- Default behavior replaces the existing Program-tab regenerate button; full fresh-week moves to Settings.
- Current Monday→Sunday week; preserved day is from this week.
- No change to automatic start-of-week generation.

## Considerations for whoever builds it
- This shares the generation prompt and active-program logic with B08 and depends on B10's hardened response handling — coordinate and sequence accordingly so a regenerate can't reintroduce the prose-instead-of-JSON stall.
- The AI needs to be told which days are fixed (logged) and which it may (re)plan, plus the already-trained content for rebalancing.
- Be deliberate about the data model: replacing a day's plan must leave that day's logged sets intact.
- Standard cross-cutting constraints apply (build via `./build.sh`; no commits/releases or UI tests unless asked).
