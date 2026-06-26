# S8 — Sweep: Navigation & app-lifecycle (cross-cutting)

**Type:** Bug sweep (diagnose-first)
**Phase:** 1 (bug sweep & fixes)
**Cluster:** SW-D (settings & lifecycle) — see INDEX.
**Outcome-only:** Defines *where to look* and *the bar to hold*. Workers diagnose and fix.

---

## Context

Cross-cutting robustness that no single-screen sweep fully owns. Single-Activity MVVM with Jetpack Navigation; `nav_graph.xml` has ~20 destinations; args passed as plain `Bundle` (e.g. `sessionId` Long); `StateFlow` + `repeatOnLifecycle`; ViewBinding throughout. This brief catches the failures that live *between* screens and across the process lifecycle — the classic source of crashes and silent state loss.

## The hunt (where to look)

- **Navigation:** every destination reachable and returnable; back-stack correctness; deep/conditional entry points (e.g. opening a session by `sessionId`, opening exercise detail); navigating to a screen whose data no longer exists (e.g. deleted session/program); double-navigation from rapid taps.
- **Lifecycle:** rotation on every screen; background→foreground restore; **process death + restore** (the harshest test) across the app; configuration changes; ViewBinding null-after-destroy hazards in async callbacks.
- **First-run vs returning:** routing into onboarding vs home; state when no plan/no history/no API key exists app-wide.
- **Concurrency:** in-flight coroutine (`viewModelScope`) completing after the view is gone.

## Acceptance bar (ALL tiers apply)

Full severity bar: crashes (especially lifecycle/back-stack crashes), data/state loss across lifecycle, visual glitches on rotation/restore, minor polish.

## Adversarial / edge states to exercise (on-device)

Rotate on every screen; background/restore on every screen; force process death (developer setting) and restore from each screen; rapid back-button and rapid double-navigation; navigate to a now-deleted entity; cold start vs warm start; first-run routing; no-API-key app-wide.

## Acceptance criteria ("Done when …")

- **Done when** every navigation path and every screen has been exercised through rotation, backgrounding, and process-death restore on-device **and** the navigation/lifecycle code paths reviewed, with findings recorded against the all-tiers bar.
- **Done when** no screen crashes or loses state on rotation, backgrounding, or process death, and no back-stack/dead-entity navigation crashes.
- **Done when** first-run vs returning routing is correct for all empty/no-config states.
- **Done when** each confirmed defect is **diagnosed to root cause before any fix**, then fixed and re-verified on-device.

## Scope & constraints

- **Diagnose first.** This brief is the **cross-cutting net**; per-screen sweeps (S1–S7) also exercise lifecycle on their own surfaces. Findings should be de-duplicated against those sweeps at integration (orchestrator's call).
- **Cross-cutting:** build via `./build.sh`; no commits/releases unless asked; on-device verification required (SEQUENCE.md).
