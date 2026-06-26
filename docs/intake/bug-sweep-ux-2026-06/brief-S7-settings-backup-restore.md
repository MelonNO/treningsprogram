# S7 — Sweep: Settings, onboarding/setup & backup/restore

**Type:** Bug sweep (diagnose-first)
**Phase:** 1 (bug sweep & fixes)
**Cluster:** SW-D (settings & lifecycle) — see INDEX.
**Outcome-only:** Defines *where to look* and *the bar to hold*. Workers diagnose and fix.

> **UPDATE (2026-06-24) — Import = MERGE is the intended behavior.** On-device S7 verification found that `ExportRepository.importFromJson` actually **MERGES** the backup into existing data (via `BackupMerger.*`; the import dialog states "nothing is deleted"), NOT the **wipe-and-replace** this brief describes below. The wipe-and-replace text is the STALE artifact (the in-flight cloud-backup work changed the behavior). Per a user product decision relayed by the coordinator on 2026-06-24, the app should **KEEP MERGE** — no import code change is required. Treat every "wipe-and-replace" mention below as superseded by "merge". (Provenance: this closure was conveyed via a coordinator relay, not a direct user message; revert this note if the user later contradicts it.)

---

## Context

Configuration, first-run, and the data-safety surface:
- **Settings:** `ui/settings` — main settings plus sub-screens (About, Training, AI, PromptLog, RejectionLog, Debug, CrashLog, Unrecognized, Backup, GymPresets). API key (EncryptedSharedPreferences), days/week, goal, experience, rest timer, injuries/severity, gym presets.
- **Onboarding/Setup:** `ui/onboarding`, `ui/setup` (`setupWizardFragment`) — first-run experience.
- **Backup/Restore:** `ExportRepository` (`exportToJson`/`importFromJson`), `settingsBackupFragment` (Export via share sheet, Import via picker — **wipe-and-replace**, rejects `schema_version != 1`), plus Reset Workouts and Factory Reset. Known coverage gaps recorded: the custom/edited **Exercise library** and **GymPreset** table are NOT in the export set; several PreferencesManager fields excluded; B1 weekly summaries deliberately not yet in the backup set.

## The hunt (where to look)

- **Settings round-trip:** every setting saves, persists across restart, and takes effect (API key change taking effect immediately is a documented behavior — verify).
- **Backup/Restore:** export produces a valid file; import wipe-and-replace restores faithfully; confirm dialogs and destructive actions (Reset, Factory Reset) behave and can't fire accidentally; behavior on a malformed/old/newer-schema file (brittle `schema_version` check).
- **Export completeness vs expectation:** confirm what *is* and *isn't* captured matches intent; flag any newly-shipped v1.8.0 user data (named programs, manual edits, summaries) missing from backup as a finding (this is the "data-loss" tier — high priority).
- **Setup/onboarding:** clean first-run completes correctly into a usable state; partial/interrupted onboarding.

## Acceptance bar (ALL tiers apply)

Full severity bar — and **data-loss tier is paramount here**: any way a user loses data through backup/restore, reset, or migration is top priority.

## Adversarial / edge states to exercise (on-device)

Import a malformed/empty/wrong-schema file; cancel mid-import; Factory Reset then restart; rotation/backgrounding mid-onboarding; no-API-key flows; export with empty DB; restore over existing data; rapid taps on destructive buttons.

## Acceptance criteria ("Done when …")

- **Done when** settings, onboarding, and backup/restore have been exercised on-device through the adversarial states **and** code-reviewed, with findings recorded against the all-tiers bar.
- **Done when** no path through backup/restore, reset, or onboarding loses or corrupts user data unexpectedly; destructive actions are clearly confirmed and not accidentally triggerable.
- **Done when** import handles malformed/old/newer-schema files without crashing and with a clear message.
- **Done when** any v1.8.0 user data missing from the export set is **flagged as a finding** (decision on whether to add it to backup is deferred to the user — see Deferred decisions).
- **Done when** each confirmed defect is **diagnosed to root cause before any fix**, then fixed and re-verified.

## Deferred decisions (flagged for whoever builds it)

- **Expanding the backup set** to include newly-shipped v1.8.0 user data (named programs, manual edits, B1 summaries) and the previously-noted gaps (custom Exercise library, GymPresets, excluded prefs) is a **scope decision the user has not made**. This sweep should *surface* the gap; *adding* to the backup format (which may require a schema_version bump and is entangled with the in-flight cloud-backup work) is out of scope unless the user asks.

## Scope & constraints

- **Diagnose first.**
- **Coordination:** an in-flight cloud-backup/restore task modifies `PreferencesManager`/`ExportRepository`; coordinate to avoid blind concurrent edits.
- **Cross-cutting:** build via `./build.sh`; no commits/releases unless asked; on-device verification required (SEQUENCE.md).
