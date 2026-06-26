# U2 — XP log / history (tap the XP bar to see what earned XP)

**Type:** New feature (concrete user-tested item #6)
**Phase:** 2 (UX/feature addition)
**Track:** Concrete user-tested items.
**Outcome-only:** Describes desired behavior + acceptance criteria, not implementation.

---

## Context

The app tracks a user's total XP/level (in `UserStats`, surfaced via the XP bar — e.g. on Profile/Home), but it does **not** keep a per-event record of *what* earned each XP. There is currently no XP-event history to show. The user wants tapping the XP bar to reveal that history.

## Desired behavior (end result)

Tapping the **XP bar** opens an **XP log** — a readable history of the events that have granted the user XP (what happened, how much XP, and when). Because no per-event history exists today, the log **records XP events going forward from when this ships** — past XP is not backfilled, and the log simply starts accumulating entries from that point.

## Acceptance criteria ("Done when …")

- **Done when** tapping the XP bar opens an XP log/history screen.
- **Done when** each XP-earning event from this feature's ship date forward is recorded with **what earned it, the XP amount, and when**.
- **Done when** the log shows a sensible **empty state** before any new events have accrued (and for a fresh install), rather than a blank or broken screen.
- **Done when** the total XP shown on the bar remains consistent with the user's understanding (the log explains new XP; it does not need to reconcile historical totals it never recorded — see scope).

## Scope & constraints

- **In scope:** capturing XP events going forward and a tappable XP log to view them.
- **Out of scope:** historical backfill/reconstruction of XP earned before this ships (the user accepted "going forward only"); changing how much XP anything awards.
- **Tap location:** wherever the XP bar appears — the builder uses best judgment (consistent across surfaces is ideal). Decision delegated by the user.
- **Coordination:** lives in the gamification area swept by **S6** — S6 should verify the XP log alongside achievement/unlock correctness; coordinate so XP-event capture doesn't disturb existing XP/level math.
- **Cross-cutting:** build via `./build.sh`; no commits/releases unless asked; on-device verification required (SEQUENCE.md).
