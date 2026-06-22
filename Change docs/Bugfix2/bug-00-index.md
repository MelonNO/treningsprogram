# Bug-fix Batch — Coordination Index (for a coding agent)

Seven bug briefs, **one agent per bug**. Read this first for clusters, order, and shared constraints.

## The 7 bugs
1. Rest timer: swipe-down should hide, not destroy — `bug-01`
2. Rest timer: recall / manual start with a visible affordance — `bug-02`
3. Rest timer: persistent notification timer — `bug-03`
4. "Home gym" wrongly prescribes squat-rack exercises — `bug-04`
5. Intermittent navigation mismatch — `bug-05`
6. Equipment-change "regenerate plan" pop-up missing — `bug-06`
7. Training-profile: sticky, change-gated save button — `bug-07`

## Clusters — do NOT parallelize
- **Timer cluster (1 + 2 + 3): one agent.** Same subsystem. Bug 03 (notification-backed timer) is the backbone — once the timer runs as a background/notification timer, Bug 01's "keep counting while hidden" and the outside-app behavior follow, and Bug 02's recall/manual-start sits on top. Built separately they will collide.
- **Equipment (4 + 6): related but SEPARATE fixes; may run in parallel.** 4 = the plan filter must honor "no squat rack"; 6 = an informational pop-up reminding the user to regenerate after an equipment change. Different code, different concerns.
- **5 (nav) and 7 (save button): independent.**

## Suggested order (by user impact)
1. **Bug 5** — navigation is core usability and currently unreliable.
2. **Timer cluster (1 + 2 + 3).**
3. **Bug 4** — wrong exercises prescribed.
4. **Bug 6, Bug 7.**

## Global constraints
- Platform: **native Android (Kotlin/Java).**
- **Diagnose before patching** — especially Bug 5 (intermittent, no repro) and Bug 4 (find where the equipment mapping fails). Don't mask symptoms.
- **Do not regress** previously-fixed behavior or working features.
- Where a fix implies an undecided design choice (e.g. Bug 7 placement), pick a sensible default, apply it consistently, and report it.
- Each agent reports: root cause, fix, how verified, residual risk, any new dependency.

## Testing (layered strategy for this native app)
- **Logic / data / dialog bugs (4, 6):** verifiable on the JVM via Robolectric (fast, `./gradlew test`); pop-up presence (6) via a Robolectric/Compose UI test.
- **Gesture / navigation / notification / layout bugs (1, 2, 3, 5, 7):** need real on-device behavior — verify on a headless **AVD + KVM** instance driven by **Maestro**. Bug 03's outside-app notification must specifically be checked with the app **backgrounded and closed**.
