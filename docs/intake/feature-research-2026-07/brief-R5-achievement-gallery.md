# Brief R5 — Achievement gallery: categories, rarity, and visible progress

**Type:** Feature (gamification presentation) — under the user's "complete creative freedom" grant (Q4)
**Cluster:** G-presentation (with R6) — same worker, R5 first.

> Outcome-only brief: describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context
The app has ~200 predefined achievements (`AppDatabase.PREDEFINED_ACHIEVEMENTS`, checked in `GamificationRepository.checkAchievements`) across clear implicit families — workout count, current streak, best streak, PRs, per-session PRs, levels, total XP, sets/session, volume/session, exercise variety, and 27 cross-stat combos — many with genuinely fun names ("Eight Days a Week", "The Octopus", "Combo: Hercules"). But the presentation sells them short: Profile shows a **collapsed single flat list** of unlocked items plus a few locked "shadow" rows. There is no grouping, no sense of which are special, and — the biggest miss — **no progress toward locked ones**, even though nearly every achievement is a simple threshold over stats the app already has (e.g. 43/50 workouts). The user keeps the gamification layer deliberately vivid.

## What the user wants (end result)
The achievement collection becomes a browsable, motivating gallery:

1. **Grouped by category** (Workouts, Streaks, Records, Levels, XP, Big Sessions, Variety, Combos — final naming free), each category showing its unlocked/total count.
2. **Rarity tiers** — every achievement gets a tier (e.g. Common / Rare / Epic / Legendary) derived from how hard its threshold is, with distinct vivid styling per tier (colors/glow in the Auros language; emoji stay).
3. **Visible progress on locked achievements:** threshold-based ones show a progress bar + numbers ("43/50 workouts", "12/14-day streak"). Combos/per-session ones may show their condition instead of a bar.
4. **"Next up" motivation on Profile:** a short strip of the 2–3 closest-to-unlock achievements with their progress, replacing the arbitrary shadow rows as the teaser.
5. The full gallery is comfortably browsable at ~200 items (collapsible categories, or a dedicated screen — builder's choice), and the unlocked count line ("Achievements 87/200") stays.

## Acceptance criteria
- Done when every defined achievement appears exactly once, in a category, with a tier, and the per-category and total counts are correct on both fresh installs and upgrades (the existing seed/reconcile behavior keeps working).
- Done when locked threshold achievements show live, correct progress from current stats, and progress never exceeds the bar before unlock.
- Done when tiers are visually distinct and vivid, consistent with the Auros theme, and unlocked vs locked states are unmistakable.
- Done when the Profile "next up" strip shows the genuinely nearest unlocks (by fraction of threshold reached) and updates after workouts.
- Done when nothing about *unlock logic, IDs, or timing* changes — presentation and metadata only; no achievement is gained or lost by this brief.
- Rendering/derivation logic unit-tested where pure (tier mapping, progress math, nearest-next selection).

## Scope and constraints
- **In scope:** presentation, grouping, tier metadata, progress display, the Profile teaser.
- **Out of scope:** adding/removing/rebalancing achievements; XP; changing when checks run. (R1/R4 change *inputs* to some checks — that's theirs.)

## Decisions baked in
- Creative freedom granted (Q4); keep the vivid/emoji character (established user preference).

## Assumptions (user may override)
- **A-G1:** four tiers, assigned by threshold difficulty per family (e.g. workouts: ≤10 Common, ≤50 Rare, ≤200 Epic, above Legendary — analogous per family; combos rated by strictness).
- **A-G2:** the gallery lives where achievements live today (Profile), expanded in place or via one tap into a full screen — no new bottom-nav tab.

## Considerations for whoever builds it
- R6 (celebration) styles unlock cards by tier — R5's tier model must exist first; same worker recommended.
- Achievement metadata refresh on upgrade (`ensureAchievementsSeeded`) already reconciles name/description/emoji — tier/category metadata should ride the same reconcile so upgrades stay consistent.
