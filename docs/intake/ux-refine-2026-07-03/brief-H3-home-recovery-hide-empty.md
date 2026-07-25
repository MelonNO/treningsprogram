# H3 — Home muscle-recovery card: hide when nothing is recovering

**Type:** Refinement (density)
**Cluster:** A (Home) — after H1
**Outcome-only.**

## Context
The Home muscle-recovery card (`fragment_home.xml` card #5, rendered by `HomeFragment.renderRecovery`) is **always visible**. When no muscle is currently recovering it renders a single line — "All muscles are rested and ready." — inside an otherwise empty card with its "MUSCLE RECOVERY" eyebrow. That means on any day where the user is fresh (including fresh installs, deload weeks, or well-rested days) Home carries a full card whose only content is a placeholder line.

Every other optional card on Home (goal nudge, Wrapped-ready, rest-day recovery, deload banner) follows a "show only when it has something to say" rule. The recovery card is the exception, and it costs fold space for no information.

## What the user wants (end result)
The recovery card behaves like the app's other conditional cards: it appears only when at least one muscle is actually recovering. When nothing is recovering, the whole card (eyebrow included) is absent, so Home is tighter and the visible cards all carry real content.

## Acceptance criteria (Done when …)
- When one or more muscles are recovering, the card shows exactly as today (amber dot, muscle name, "trained Xh ago", progress bar, "Xh left", tap → Recap).
- When nothing is recovering, the entire recovery card is hidden (no eyebrow, no empty line, no blank card).
- The tap-through behaviour (row → last session that trained that muscle, via Recap) is unchanged when the card is shown.
- No regression to the recovery calculation itself.
- Build passes; recovery unit tests remain green.

## Scope and constraints
- **In scope:** visibility gating of the recovery card on Home.
- **Out of scope:** the recovery model/`MuscleRecovery` maths, the fine-muscle labels, the Recap surface.

## Decisions baked in
- The recovery card is content-gated like Home's other optional cards.

## Assumptions (user may override)
- **A-H3a** — Hide the card entirely when empty. (Alternative the user might prefer: keep a permanent one-line "all rested" reassurance — if so, this item is dropped.)

## Considerations for whoever builds it
- `renderRecovery` currently early-returns with the "all rested" TextView on an empty list; the card container's visibility is what needs gating, from the `muscleRecovery` collect block.
- Confirm the empty case is genuinely "no muscles recovering" and not "data still loading", so the card doesn't flicker on first frame.
