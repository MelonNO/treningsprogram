# Brief 02 — Body progress tab with built-in body-fat calculation

Type: New feature (user's original items 2 + 4, MERGED — the "body fat calculator" is not a separate screen; it is the automatic computation inside this tab)
Cluster: A (shares the Stats-screen surface with brief 03 — serialize or one worker)

> Outcome-only: this brief describes the end result and user experience. The "how" belongs to the orchestrator and its workers.

## Context

The Stats screen (bottom-nav destination) currently has four tabs: Recap, Stats, Progress, History. Body weight already exists as a Room entity (`BodyMeasurement`: date + weightKg) with a chart card inside the Progress tab and a quick-add on the Home screen. Waist, neck, hip, body fat, height, and sex are not stored anywhere today.

## What the user wants (end result)

### New "Body progress" tab
- A new tab on the Stats screen alongside the existing four.
- **Primary function: displaying graphs and showing progress. Secondary: logging.** The logging input must **not take up any unnecessary space when not in use** (compact/collapsed until the user wants to log).
- Charts over time for: **body weight, body fat %, waist, neck** (and hip for women — see below).

### Measurement logging
- An entry logs any subset of: weight, waist, neck (+ **hip, shown only when the profile sex is woman**; a male user never sees hip anywhere in the app). Fields are individually optional — e.g. weight alone is a valid entry.
- Units: cm for girths and height, kg for weight (A1).
- The Home screen's existing **quick-add for weight stays** and keeps feeding the same data.

### Automatic body fat
- Whenever an entry contains **waist + neck** (for women: waist + neck + hip, A4), the app computes body fat % as the **average of the US Navy method and RFM (Relative Fat Mass)**, using the profile height and sex.
- Body fat is a **percentage only** — no weight is involved and no fat-mass figure is shown (user explicitly withdrew the earlier weight remark).
- No standalone calculator screen exists; computation happens automatically on qualifying entries and appears in the body-fat chart.

### Profile: height and sex
- Height and sex become profile settings, collected in **first-time setup** and editable in **App Settings**.
- Existing users without these values can still log and see measurement charts; body fat simply cannot compute until height/sex are set (A5).

### Time scale
- All charts default to a **3-month** window.
- Adjustable: **1 month / 3 months / 6 months / 1 year / All**, plus a **calendar-specific custom range** (pick exact start and end dates, A6).

### Relocation
- The body-weight chart card currently inside the Progress tab is **removed from Progress** — body weight now lives here.

## Acceptance criteria

- Done when the Stats screen shows a fifth "Body progress" tab whose default view is charts, with the logging UI occupying minimal space until invoked.
- Done when the user can log weight, waist, and neck (hip too when profile = woman) in any combination, and entries appear on the correct charts.
- Done when an entry with waist + neck (+ hip for women) yields a body fat % equal to the average of the Navy and RFM results for the stored height/sex, and that value charts over time.
- Done when a male profile never encounters a hip field anywhere.
- Done when first-time setup asks for height and sex, and App Settings can edit both.
- Done when the time window defaults to 3 months and can be switched to 1M/6M/1Y/All or a custom calendar-picked start–end range, affecting all charts in the tab.
- Done when the body-weight card no longer appears in the Progress tab, the Home quick-add for weight still works, and previously logged weights show in the new tab.
- Done when backup export/import round-trips all new measurement and profile data, and older backups still import cleanly.

## Scope and constraints

- **In scope:** new tab, measurement logging + storage, body-fat computation, profile height/sex (setup + App Settings), time-range control, card relocation, backup coverage.
- **Out of scope:** any standalone calculator screen; fat-mass (kg) display; feeding these measurements into AI program generation (not requested — flag as a possible future idea only).
- Hard constraint: the two formulas are the standard published ones — Navy (log10-based, sex-specific) and RFM (height/waist, sex-specific) — averaged with equal weight.

## Decisions baked in

- Graphs primary, logging secondary and space-efficient (user decision 10).
- Body fat only when waist + neck logged; % only (decisions 3–4).
- Hip only for women, invisible for men (decision 2).
- Ranges incl. calendar custom (decision 7); Progress-tab card removal (decision 5); Home quick-add stays (decision 6).

## Assumptions (user may override)

- **A1** cm/kg units. **A4** women need hip present for the computation. **A5** missing profile → no body fat until set, with a gentle pointer. **A6** custom range = start + end date pickers. **A7** exact compact-logging UI shape is the builder's choice within decision 10.

## Considerations for whoever builds it

- Extending `BodyMeasurement` (or companioning it) is a Room schema change → DB version migration + backup format version bump; older-backup import must be preserved.
- Which timestamp/most-recent-value rules apply when charts mix sparse series (e.g. weight logged daily, waist weekly) is a presentation judgment — keep each series independent rather than interpolating across fields.
- The setup wizard also hosts brief 01's tip rotation — coordinate if built simultaneously.
