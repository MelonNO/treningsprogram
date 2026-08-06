# Brief 03 — Title band: wash reaches the screen edge, and all four tabs match

**Type:** Bug (visual) + consistency sweep
**Cluster:** none — fully independent of every other item in this batch
**Source:** User item 3

> **Outcome-only.** This brief describes the end result and user experience. It does not prescribe
> how to build it.

---

## Context

Each of the four bottom-navigation tabs opens with a "hero" title band at the top: a small teal
beacon dot, an all-caps eyebrow label, and a large title, sitting on a soft teal wash that glows from
the upper left.

- **Home** — `OVERVIEW` / "Today"
- **Program** — `PROGRAM` / "This week"
- **Stats** — `INSIGHTS` / "History"
- **Profile** — `PROFILE` / "You"

## Current (incorrect) behaviour

Two separate inconsistencies, both confirmed by the user.

**1. The wash does not reach the screen edge on three of the four tabs.**
On **Home, Program and Profile** the coloured wash stops short of the screen edge, leaving a visible
sliver of plain background between the wash and the edge of the display. The user notices it on the
right-hand side. On **Stats** the wash runs the full width, which is what they consider correct.

**2. The accent bar is missing on Stats.**
Home, Program and Profile each have a **short teal accent bar** beneath the title. **Stats does
not.**

## Correct behaviour

All four tabs present the same title band:

- The **wash runs edge to edge**, touching both sides of the screen, on **all four** tabs.
- The **text stays indented** where it is now — only the colour extends outward. The user asked for
  the text treatment to match Stats.
- The **accent bar is present on all four tabs, including Stats.** The user was offered the
  opposite resolution (remove the bar from the other three to match Stats) and explicitly chose to
  **add it to Stats instead**: *"include the bar all tabs also the stats tab"*.

## Acceptance criteria

- **Done when** the coloured wash behind the title touches the left and right edges of the screen on
  **Home, Program, Stats and Profile** — no sliver of background between the wash and the screen
  edge on any of them.
- **Done when** the eyebrow label and title text remain indented from the screen edge, aligned as
  Stats aligns them today — the text does **not** move out to the edge along with the wash.
- **Done when** the short teal accent bar appears beneath the title on **all four** tabs, Stats
  included.
- **Done when** the four bands read as the same component: same eyebrow treatment, same title
  treatment, same accent bar, same vertical spacing above and below.
- **Done when** the content below each band is unaffected — cards, lists and the Stats tab strip keep
  their current position and indentation.
- **Done when** nothing overlaps or clips at the top of the screen on a narrow display.

## Scope and constraints

**In scope — exactly four screens**
- Home, Program, Stats (the History screen's top band), Profile.

**Out of scope — confirmed by inspection, do not touch**
- The **Stats sub-tabs** (Log, Stats, Progress, Recap, Body). They have **no title band of their
  own** — they sit underneath the shared Stats band. Nothing to sweep.
- The **"THIS WEEK" week-pulse card** inside the Stats sub-tab, which uses the same wash drawable as
  a *card* background. It is not a title band.
- The **per-session header inside the Recap sub-tab**, which builds a hero-style header
  programmatically. It is a session header, not a screen title band. **Judgment call flagged
  below.**

**Hard constraints**
- Purely visual. No behavioural, data or navigation change.

## Decisions baked in (confirmed by the user)

1. Direction of the fix: make the other three match **Stats' full-bleed wash**, not the reverse. (3a)
2. Text stays indented, styled as Stats does it. (3b)
3. Accent bar goes on **all four** tabs, **including Stats** — the user's explicit choice over the
   alternative of removing it. (Q2)
4. This is a **sweep** for consistency, not a point fix on one screen. (3c)

## Decisions the user deferred — flag for whoever builds it

- The **Recap sub-tab's per-session header** uses the same wash and eyebrow styling but is a
  different kind of element and sits inside a padded container. The user's "all tabs" answer refers
  to the four bottom-nav tabs. Whether this header should also change was never put to them.
  **Recommend leaving it alone; confirm before touching it.**

## Considerations for whoever builds it (surfaced, not decided)

- The wash is a **radial gradient glowing from the upper left**, so it is brightest on the left and
  fades to nothing on the right. Making the band full-bleed changes where that gradient's centre
  falls relative to the screen. The bands should be compared side by side afterwards so the four do
  not end up with visibly different glow intensity purely because their widths changed.
- Stats' band currently sits directly under the system status bar with no scroll container above it,
  while the other three scroll. Matching them visually needs care that the wash does not scroll away
  differently on one tab than another, or start behaving differently at the top of a scroll.
- The app does **no** edge-to-edge or window-inset handling anywhere. Nothing here should introduce
  it as a side effect.

## Grounded facts (verified 2026-08-06, for orientation only)

- Shared wash drawable: `/home/migul/treningsprogram/app/src/main/res/drawable/bg_hero_wash.xml`
  (radial gradient, centre at 16% / 32%, fading to transparent).
- The cause of the sliver: Home (`fragment_home.xml`), Program (`fragment_program.xml`) and Profile
  (`fragment_profile.xml`) each place the band **inside a scroll container carrying 16dp padding on
  all sides**, so the band is inset. Stats (`fragment_history.xml`, line 17) places the band at the
  top level at full width and applies its 16dp **inside** the band, so the wash bleeds to the edges.
- The accent bar is a 52dp × 3dp view: `fragment_home.xml:62`, `fragment_program.xml:48`,
  `fragment_profile.xml:48`. **Absent from `fragment_history.xml`.**
- Out-of-scope wash usages confirmed: `fragment_history_stats.xml:74` (card) and
  `HistoryRecapFragment.kt:207` (per-session header).

## Standing constraints

- Build via `./build.sh`, never `./gradlew` directly.
- No commits or releases unless the user asks.
- No on-device or automated UI tests; verify via build + unit tests. **This item is visual — unit
  tests cannot prove it. The user's device check is the real proof.**
