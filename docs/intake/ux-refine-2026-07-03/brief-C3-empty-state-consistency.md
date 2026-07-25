# C3 — Empty states: consistent lightweight treatment

**Type:** Refinement (consistency)
**Cluster:** C (cross-cutting sweep) — Wave 2, after per-screen items land
**Outcome-only.**

## Context
Empty states are handled inconsistently. Some screens have a rich, deliberate empty state (Program's "No Program Yet" — icon + title + body + button; the Program rest-day card — 😴 + "Rest Day" + subline). Others fall back to a single bare line of muted text with no shared voice:

- Home recent workouts: `no_sessions_yet` string, plain text in `tv_recent_sessions`.
- Home muscle recovery (when shown): "All muscles are rested and ready." (H3 may hide this case entirely).
- Profile PRs: "No PRs in the last 7 days — your next one is waiting."

The bare ones aren't broken, but they read in different voices and weights, and none matches the others. It's the kind of drift that accumulates across fast batches.

## What the user wants (end result)
The lightweight in-card empty states share one voice and one visual weight, so an empty section looks intentional and calm rather than like a missing value. This is a copy-and-tone pass, not a redesign — the goal is coherence, not adding heavy illustrated empty states everywhere.

## Acceptance criteria (Done when …)
- The bare in-card empty states (Home recent-workouts, Profile PRs, and — if still shown — Home recovery) use one consistent muted tone and one consistent visual weight.
- Copy voice is unified: short, encouraging, same person/tense across the three (e.g. all forward-looking one-liners).
- No rich icon+title empty state is forced onto small in-card sections (that would over-weight them); the Program full-screen empty states are left as they are.
- No behaviour change; only the empty-case presentation/copy.
- Build passes.

## Scope and constraints
- **In scope:** copy/tone/visual-weight consistency of the bare in-card empty states listed above.
- **Out of scope:** Program's existing rich empty states; adding new artwork; the populated states.
- Coordinate with H3 (if recovery empty is hidden entirely, drop it from this item's list).

## Decisions baked in
- Bare empty states share one voice and weight; heavy empty states are not added to small sections.

## Assumptions (user may override)
- **A-C3a** — Unify voice + visual weight of the existing bare empty lines only; do NOT add icon/title empty states to these small in-card sections.

## Considerations for whoever builds it
- Prefer moving the copy into `strings.xml` with a shared tone so future screens reuse it.
- Keep it genuinely light — this item earns its keep only if it stays small; if it starts growing into per-screen redesigns, stop and flag.
