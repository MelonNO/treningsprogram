# S4 — Sweep: Stats / History / Recap / Progress (incl. 1RM trends & plateau readouts)

**Type:** Bug sweep (diagnose-first)
**Phase:** 1 (bug sweep & fixes)
**Cluster:** SW-C (analytics & display) — see INDEX.
**Outcome-only:** Defines *where to look* and *the bar to hold*. Workers diagnose and fix.

---

## Context

The Stats/History area — **the user's named worst-feeling flows** (Recap, Progress, History) and therefore the deepest-attention surface in this batch. Files: `ui/history/HistoryFragment`, `HistoryLogFragment`, `HistoryProgressFragment`, `HistoryStatsFragment`, `HistoryRecapFragment`, `RecapTrendsFragment`/`RecapTrendsViewModel`, `RecapTargetViewModel`, `StrengthChartView`, `SessionAdapter`. Plus the v1.8.0 analytics: **C1 estimated-1RM/PR trends** (`Epley`, `OneRmTrend`) and **B3 plateau/stall readouts** (`StallDetector`) where surfaced. The legacy "Personal Records" widget here counts warm-ups as PRs — that is handled by its own fix brief **F2 (P-3)**; this sweep covers everything else in the area and coordinates with F2.

## The hunt (where to look)

- **Recap / Progress / History tabs:** correctness of every displayed number (volume, counts, streaks, week keys), charts (`StrengthChartView`), per-exercise selection, date/week bucketing, and the session log list.
- **1RM/PR trends (C1):** the estimated-1RM curve and PR timeline correctness; warm-up exclusion; exercise picker; chart with sparse data.
- **Plateau readouts (B3):** wherever stall state is shown in this area — correctness and empty state.
- **Edge math:** new users, single-session history, exercises never logged, locale/week-boundary correctness (a known prior pain area — v1.6.2 was a stats-accuracy pass), unit consistency.

## Acceptance bar (ALL tiers apply)

Full severity bar — and because this is the user's worst-feeling area, hold it strictly: crashes & data-loss, wrong numbers/charts, visual/layout glitches, AND minor polish/inconsistencies.

## Adversarial / edge states to exercise (on-device)

Rotation on each tab and on charts; backgrounding/restore; empty/first-run (no sessions); single session; exercise with one data point; very long history; locale/week-boundary edges; rapid tab switching; selecting an exercise with no working sets.

## Acceptance criteria ("Done when …")

- **Done when** every Recap/Progress/History tab, chart, and computed number has been exercised on-device through the adversarial states **and** code-reviewed, with findings recorded against the all-tiers bar.
- **Done when** all displayed numbers and charts are correct for sparse, empty, single-point, and locale/week-boundary cases, with sensible empty states (no broken charts).
- **Done when** each confirmed defect is **diagnosed to root cause before any fix**, then fixed and re-verified on-device.
- **Done when** C1 and B3 readouts in this area are correct and degrade gracefully on thin data.

## Scope & constraints

- **Diagnose first.**
- **Coordination:** shares this surface with **F2 (PR-truth consolidation, P-3)** — coordinate so the legacy-widget retirement and this sweep don't edit the same screen blind. Findings here that are *experience* problems (not defects) feed the **Phase-2 UX brief (UX1)**, which is also weighted to this exact area.
- **Cross-cutting:** build via `./build.sh`; no commits/releases unless asked; on-device verification required (SEQUENCE.md).
