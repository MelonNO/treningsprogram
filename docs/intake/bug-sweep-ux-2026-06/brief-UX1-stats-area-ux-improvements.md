# UX1 — UX improvements (weighted to Recap / Progress / History)

**Type:** UX improvement set (propose → user approves/cuts)
**Phase:** 2 (UX improvements — after the bug sweep stabilizes the app)
**Outcome-only:** Describes desired experience outcomes, not implementation.

> **Decision (recorded):** the user has **no fixed UX list**; the understander proposes a prioritized set and the user approves or cuts. The user explicitly named **Recap, Progress, History** as the worst-feeling flows — these get the deepest attention; the rest of the app gets lighter polish.
>
> **IMPORTANT — this brief is itself a proposal gate.** The items below are *candidate* UX improvements. The user authorized the *batch and its shape*; the **specific UX items each still need the user's approve/cut call before implementation.** Each is tagged **MUST-HAVE candidate** or **NICE-TO-HAVE** to make cutting easy. The orchestrator should treat the chosen subset as the work, not the whole list by default.

---

## Context

Phase 1's sweep will surface defects in the Stats/History area (S4, F2); this brief covers the **experience** problems there that aren't defects — things that work but feel bad. It deliberately follows Phase 1 so polish lands on an app whose numbers and charts are already correct (avoids polishing flows that are about to change). The relevant surfaces: `ui/history` (Recap, Progress, History tabs), `RecapTrendsFragment`, `StrengthChartView`, `SessionAdapter`.

## Desired outcome

The user's worst-feeling flows — **Recap, Progress, History** — become clearer, faster to read, and more pleasant, with a single coherent story (no contradictory PR numbers, consistent terminology, sensible empty states), without changing the underlying correct data.

## Candidate UX improvements (user approves/cuts each)

The understander will propose a **prioritized, concrete list** here for the user. Until the user reviews on-device findings, these are framed as outcome themes, not prescribed designs:

- **[MUST-HAVE candidate] Coherent PR/strength story** — after F2, ensure the Stats/History area presents one consistent, understandable PR/1RM narrative (naming, placement, what each chart means).
- **[MUST-HAVE candidate] Clear, non-broken empty/first-run states** across Recap/Progress/History — friendly guidance instead of blank or confusing screens for new users (overlaps the Phase-1 audit but framed as *experience*, not crash-prevention).
- **[MUST-HAVE candidate] Richer, more visually interesting Recap (user-tested item #7).** The user explicitly asked to make the **Recap tab more visually interesting — more graphs**. The understander/orchestrator chooses the graph set using best judgment (the user delegated this), e.g. volume over time, per-muscle distribution, training frequency, PR/estimated-1RM trend. This is a **confirmed user request**, not just a candidate — but the *specific graph set* is still proposed for the user to react to. Coordinate with the C1 PR/1RM trend and the F2 one-PR-truth so Recap visuals aren't duplicated or contradictory.
- **[MUST-HAVE candidate] Readability of the core numbers/charts** — the most-looked-at stats are easy to parse at a glance (labels, units, date/week framing, chart legibility on small screens).
- **[NICE-TO-HAVE] Navigation/IA within the Stats area** — reduce friction moving between Recap, Progress, History and selecting an exercise.
- **[NICE-TO-HAVE] Consistency polish** — terminology, formatting, and visual consistency across the three tabs and with the rest of the app.
- **[NICE-TO-HAVE] Lighter app-wide polish** — a small set of high-value consistency fixes outside the Stats area surfaced during the Phase-1 sweep.

## Acceptance criteria ("Done when …")

- **Done when** a prioritized UX item list (grounded in Phase-1 on-device findings) has been **presented to the user, who approves or cuts each item**, before any UX implementation begins.
- **Done when** each *approved* item delivers its stated experience outcome, verified on-device, without regressing the now-correct data from Phase 1.
- **Done when** the Stats/History area tells one coherent story (consistent terms, one PR truth, sensible empty states).

## Scope & constraints

- **Propose-then-approve:** no UX item is implemented until the user picks it. The user may cut any/all.
- **Phase ordering:** runs **after** Phase 1 so it polishes a stabilized app.
- **No don't-touch zones** (user confirmed), but UX changes must not reintroduce defects fixed in Phase 1.
- **Cross-cutting:** build via `./build.sh`; no commits/releases unless asked; on-device verification required (SEQUENCE.md).

## Deferred decisions (flagged)

- The **exact UX item list and any visual/design choices** are deferred to the user's approve/cut review (informed by Phase-1 findings). The understander captures *what* should improve; the *how* is the orchestrator's, and the *which* is the user's.
