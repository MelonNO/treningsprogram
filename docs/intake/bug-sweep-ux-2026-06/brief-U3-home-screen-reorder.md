# U3 — Home screen reorder

**Type:** UX change (concrete user-tested item #8)
**Phase:** 2 (UX)
**Track:** Concrete user-tested items.
**Outcome-only:** Describes desired layout outcome, not implementation.

---

## Context

The Home screen (`ui/home`) currently presents its sections (XP bar, weekly challenge, today's plan, body-weight logging widget, muscle recovery panel, recent workout) in a different order than the user wants. The body-weight logging widget **already exists** on Home — this is a **reorder only**, not adding anything new.

## Desired behavior (end result)

Home presents its existing sections in this exact order, **top → bottom**:

1. **XP bar**
2. **Weekly Challenge**
3. **Today's Plan**
4. **Body weight** (the existing body-weight logging widget)
5. **Muscle recovery** (the recovery panel — note its content is reworked by U1)
6. **Recent workout**

## Acceptance criteria ("Done when …")

- **Done when** the Home screen shows its sections in the order: XP bar → Weekly Challenge → Today's Plan → Body weight → Muscle recovery → Recent workout, top to bottom.
- **Done when** all sections remain fully functional after reordering (nothing lost or broken by the move), verified on-device including scroll, rotation, and empty states.

## Scope & constraints

- **In scope:** reordering the existing Home sections only.
- **Out of scope:** adding/removing Home sections (body weight already exists; recovery content changes are U1's scope, not this brief).
- **Coordination:** the Home sweep **S1** should verify the reordered layout (not the old order); the **muscle recovery** section's *content* is governed by **U1**. Do these consistently so S1 verifies the final Home.
- **Cross-cutting:** build via `./build.sh`; no commits/releases unless asked; on-device verification required (SEQUENCE.md).
