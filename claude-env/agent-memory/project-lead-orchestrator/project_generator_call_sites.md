---
name: generator-call-sites
description: The 4 call sites of AiRepository.generateAdaptedProgram/generateSingleDayProgram + how injuries/profile fields thread to the prompt
metadata:
  type: project
---

When adding a new user-profile field that the AI generator must consume, it has to be threaded through ALL of these (confirmed 2026-06-24). Grep `generateAdaptedProgram|generateSingleDayProgram` to re-verify before editing.

**`generateAdaptedProgram` call sites (3):**
- `MainActivity.kt` (~line 242) — auto-generate path; reads everything from `prefsManager.*`.
- `ui/setup/SetupWizardViewModel.kt` (~line 91) — onboarding finish; reads `prefs.*`.
- `ui/settings/SettingsViewModel.kt` (~line 214) — manual "Generate" from settings; reads `prefs.*`.

**`generateSingleDayProgram` call site (1):**
- `ui/program/ProgramViewModel.kt` (~line 119) — the per-day "Swap day" regenerate; reads `prefsManager.*`.

**`validateProgram`** is private in AiRepository; only called inside `generateAdaptedProgram` (peer-review gate). Signature as of session-duration work: `(planJson, daysPerWeek, sessionDurationMinutes, goal, experience, injuries)`.

**Profile-field plumbing pattern (injuries example):**
- Storage: `PreferencesManager.injuries` (String, `KEY_INJURIES="injuries"`). Add new keys/props in the same block + companion.
- Wizard write: `SetupWizardFragment` (~line 341) sets `viewModel.prefs.<field>` directly from the EditText.
- Settings write: `SettingsTrainingFragment` (load ~line 70 `binding.et*.setText(prefs.X)`; save ~line 188 reads text → passes to `viewModel.saveTraining(...)` → `SettingsViewModel.saveTraining(...)` sets `prefs.X`).
- ExportRepository also reads injuries (export/import) — check it if a field must round-trip through export.

**Layouts with the injuries field:** `res/layout/fragment_setup_wizard.xml` (id `et_wizard_injuries`) and `res/layout/fragment_settings_training.xml` (id `et_injuries`). Both already use Material `ChipGroup`/`Chip` elsewhere in the same layout — no new dep needed for chip selectors.

Related: [[time-estimator-shared-helper]] (the other AiRepository workstream — both edit AiRepository.kt; build on each other, don't revert).
