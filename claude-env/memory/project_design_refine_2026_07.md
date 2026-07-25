---
name: project-design-refine-2026-07
description: "Design-refinement + 6-feature + bug-audit batch — SHIPPED v1.20.0 (2026-07-02, commits 18a8323 + 97c5a13, tag+release API-verified live); coded DIRECTLY by coordinator (user waived intake/orchestrator for this task)"
metadata: 
  node_type: memory
  type: project
  originSessionId: 0388e015-1980-48ce-9f66-2988bf3e102b
---

**Design-refine batch 2026-07-02 — SHIPPED v1.20.0** (user: "ship it, once this is done continue").
Release commit 18a8323 (batch) + 97c5a13 (hardening follow-ups; tag v1.20.0 points here); tag+release
API-verified live, asset `treningsprogram-v1.20.0.apk` (103.5 MB, md5 59ae7e760bc8f84b3334cbd03d960ad7).
The "continue" work SHIPPED IN THE SAME RELEASE: **backup schema v4** (restDaysCsv + autoRebalanceEnabled
+ dayBoundaryHour now survive restore; V3_TO_V4 stamps the version, Gson defaults fill; BackupV4PrefsTest
+ guard test now version_isFour), **auto-gen retry-on-failure** (a failed weekly auto-generation no longer
marks the week done; retries next launch, capped MAX_AUTO_GEN_LAUNCH_TRIES=3 via autoGenFailWeek/
autoGenFailCount prefs), pause button ✕→⏸. Final suite 677 tests green (only failures along the way:
the deliberate v3→v4 guard + the known H5 flake).

User explicitly waived the
intake-understanding + orchestrator agents for this task ("do not use the sub agents of the understander
and the orchestrator") — a ONE-TASK exception to [[feedback_orchestrator_owns_changes]]; coordinator
coded it directly. Scope confirmed via AskUserQuestion: ALL of R1–R7 + ALL of F1–F6, static vector art,
keep the two legibility deviations (card hairline, 550–640 button weights).

Delivered (working tree only, ~30 files + new):
- R1 eyebrows/empty-states on the ~13 untouched screens; R2 `AuroraGhostButton` (cyan→white→lavender
  1.2dp gradient border, first use = Log Workout "Next"); R3 ↗ ghost arrow-links replace all "›" chevrons
  (14 sites) + Home XP card; R4 generated vector art `ill_particle_sphere` (180-dot golden-angle globe,
  Home hero) + `ill_node_network` (empty states ×5) via scratchpad python script; R5 Auros dialogs/menus/
  snackbars in themes.xml; R6 `TextAppearance.Auros.DisplayStat` on Stats+Profile numerals; R7 palette
  strays fixed (#4CAF50→game_green, #5CCB7E→game_green, #FF5252→auros_error, #607D8B→auros_fog_dim ×3).
- F1 haptics (log-set CONFIRM + finish-workout); F2 `TodayWorkoutWidgetProvider` widget (Hilt
  @AndroidEntryPoint, 30-min cycle + refresh from MainActivity.onStart); F3 reminder = AlarmManager (NO
  WorkManager dep) + `WorkoutReminderReceiver`/`ReminderBootReceiver`/`ReminderScheduler`, opt-in toggle
  + 24h TimePicker in App Settings, prefs workoutRemindersEnabled/reminderHour/reminderMinute; F4
  `PlateMath` per-side plate readout in the weight keypad (bar 20 kg, barbell-name gated); F5
  `VolumeHeatmap` (pure) + `VolumeHeatmapView` + DAO `getMuscleSetDaysSince` in History→Stats; F6
  `Changelog`/`WhatsNewBottomSheet` + `lastSeenWhatsNewVersion` gate in MainActivity + About-screen row —
  needed `buildConfig = true` in buildFeatures (AGP 8 default off).
- Audit fixes: Stats best-streak, Stats calendar "today" anchor, Recap "days ago" all used raw UTC-day
  math → now DayBoundary logical days (matches gamification streak).
- Tests 651→672 (PlateMathTest, VolumeHeatmapTest, ReminderSchedulerTest); assembleDebug green; the one
  failure was [[reference_flaky_h5dispatcher_test]] (passes in isolation).

Still open (small, non-blocking):
- Recap-overview weekly graphs + per-exercise weekly-volume SQL bucket weeks THURSDAY-based (epoch
  floor) while the new Stats heatmap is Monday-based — cosmetic inconsistency, unify someday.
- Plate calculator assumes a fixed 20 kg bar + standard metric plates — could become per-gym settings.
- Gotcha learned: `./build.sh … | tail` masks the real exit code — first "successful" build had actually
  FAILED; capture exit codes with redirection instead.
- On-device checks pending (widget, reminders, what's-new sheet, art).
