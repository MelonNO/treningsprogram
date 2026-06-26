# F2 — Fix: one source of PR truth (retire legacy warm-up-counting PR widget)

**Type:** Bug fix (cause known) + consolidation
**Phase:** 1 (bug sweep & fixes)
**Cluster:** SW-C (analytics & display) — see INDEX.
**Outcome-only:** Describes the desired end result, not the implementation.

> **DECISION (recorded):** the user endorsed **P-3 — one source of PR truth**. Resolution chosen by the user: **retire/replace the legacy widget with the correct C1 estimated-1RM logic**, rather than separately patching the old widget.

---

## Context

The Stats/History area (the user's worst-feeling flows) contains a **legacy "Personal Records" widget** that **counts warm-up sets as PRs** — a pre-existing correctness bug. The v1.8.0 **C1** feature (`Epley`, `OneRmTrend`, on `RecapTrendsFragment`) introduced a *correct* estimated-1RM PR timeline that excludes warm-ups. The app therefore currently risks showing **two contradictory "PR" truths in the same area** — itself a correctness-and-UX defect.

## Current (incorrect) behavior

The legacy Personal Records widget reports PRs that include warm-up sets, producing wrong PR values that can also disagree with the correct C1 PR timeline shown elsewhere in the same area.

## Correct behavior

The app presents a **single, correct source of PR truth** based on the C1 estimated-1RM logic (warm-ups excluded). The legacy warm-up-counting widget no longer presents a contradictory PR number — it is retired or replaced so there is one consistent PR story.

## Acceptance criteria ("Done when …")

- **Done when** the app shows **one** PR/strength-record story in the Stats/History area, driven by the correct C1 estimated-1RM logic (warm-ups excluded).
- **Done when** the legacy warm-up-counting Personal Records widget no longer displays incorrect or contradictory PR values (retired or replaced).
- **Done when** no PR-related regression appears elsewhere that relied on the old widget (verify the surrounding Stats/History screens still make sense).
- **Done when** the change is verified on-device against logged data that includes warm-up sets, confirming warm-ups are excluded.

## Scope & constraints

- **In scope:** removing/replacing the legacy PR widget and ensuring the C1 logic is the single PR source in this area.
- **Out of scope:** redesigning the broader Stats/History layout (that's the Phase-2 UX brief UX1) and the C1 feature's own internals (already shipped).
- **Coordination:** shares the Stats/History surface with **S4** — coordinate so the retirement and the S4 sweep don't edit the same screen blind. Whatever PR/1RM formula convention C1 uses is authoritative.
- **Cross-cutting:** build via `./build.sh`; no commits/releases unless asked; on-device verification required (SEQUENCE.md).
