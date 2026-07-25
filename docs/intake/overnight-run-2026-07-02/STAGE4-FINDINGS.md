# Stage 4 — Whole-app on-device test sweep (v1.24.0)

Harness: Waydroid 192.168.240.112:5555, Maestro 2.6.1, animations off.
APK: app/build/outputs/apk/release/app-release.apk, md5 195294a5744832105ae5adf074f664e2 (verified on-device). versionCode 63 / versionName 1.24.0.
Clean install (uninstalled stale v1.10.4 first → first-run state).
Live API budget this stage: <=20 calls.

## HARNESS CONSTRAINT (important)
- Release APK is `not debuggable` → `run-as` + sqlite3 DB seeding is UNAVAILABLE. All test data must be driven through the UI or live generation. (Debug builds allowed run-as; release does not.)
- adb needed key install: pushed ~/.android/adbkey.pub to /data/misc/adb/adb_keys via `sudo waydroid shell` then restarted adbd (device was "unauthorized" on first connect).

## API calls ledger
- Onboarding "Generate My Program": 2 POST to api.anthropic.com, both HTTP 200 (1.3s + 1.6s, ~60s apart = generate + peer-review). Running total: 2 / 20.

## Findings ledger (append as found, severity-ranked at report time)

### F1 (MEDIUM, possible regression on stage-3 item #16) — Numeric keypad does NOT dismiss on outside tap
- Repro: Program > Start Workout (guided log). Tap the Reps value -> system numeric keypad opens. Then tap any of: (a) the Warm-up toggle, (b) empty space between cards, (c) scroll the content.
- Observed: keypad stays open in ALL THREE cases. The "pass-through" HALF works (tapping Warm-up toggled it ON while keypad stayed). But the "tap-outside dismisses" half never fires.
- Expected per item #16: "keypad tap-outside dismisses (pass-through tap; ±2.5 buttons exempt)".
- Evidence: shots/14_keypad.png (open), 15_keypad_dismiss.png (Warm-up toggled, kbd stays), 16_tap_empty.png (empty tap, kbd stays), 17_scroll_kbd.png (scroll, kbd stays).
- CAVEAT: this is the Waydroid AOSP SYSTEM IME. The app dismiss path (TouchObservingLinearLayout -> hideSoftInputFromWindow) may behave differently vs a real device's IME. Not blocking (Done key + Back both dismiss). Needs a real-device check to confirm app-side vs harness.

## Per-area pass/fail (running)
- Clean install + first-run: PASS. Launches to Home "Today", POST_NOTIFICATIONS prompt shown (R2), Allow works.
- Home XP card (card_home_xp: L1 badge, 0 XP bar, streak indicator, arrow): renders PASS. (This is the XP orb, NOT the removed v1.22 rest sphere.)
- Onboarding 5-step wizard (goal/exp -> schedule+duration+manual-rest -> equipment presets -> profile injuries/priorities/dislikes -> API key + Generate): PASS all steps.
- Generate program LIVE: PASS. "Building your program..." with rotating tip -> "Your program is ready!" -> plan saved. 2 API calls.
- Program tab: PASS. Program spinner (My Program), Save-as-new/Options, This Week day chips Mon-Fri STR + Sat/Sun rest, 0/5 done, "Why your program changed" rationale (collapsed), Thursday selected = 5 exercises Back focus ~38m. Exercise rows show muscle chip + sets/reps/load chips + per-exercise time (~12m/~7m) + RPE progression note + Edit/up/down/Delete (E1). Names correctly muscle-classified (Back).
- Weekly Challenges 2.0 (R4): PASS render. Leg Day +75, Volume Day +75 (10+ sets), Working Dozen +85 (12+ sets).
- Guided log flow (start/log/complete): PASS. Exercise 1..5, per-exercise timer resets each exercise + persists/counts up, Warm-up toggle, Weight/Reps steppers, Effort chips, Set N of M counter, LOG SET, rest timer between sets (AI suggested 3:00 + Skip/-30s/+30s), Next/Finish. Logged 16 working sets across 5 exercises (Weighted Pull-Up/Lat Pulldown Underhand/Dumbbell Incline Row = Back; Dumbbell Hammer Curl/Cable Bicep Curl = Arms). Finish -> confirm dialog (Keep going/Complete).
- R6 workout-complete celebration: PASS. "Workout Complete!" + count-up "+290 XP" with breakdown (Workout +50 / 16 sets +80 / Challenges +160), "1-day streak" (R1), "5 exercises / 16 sets / 3405 kg volume", "Achievement Unlocked" list (R5) with rarity tags: First Step COMMON, Leveled Up COMMON, Three's Company COMMON. Buttons View full analysis / Awesome. Evidence 29_celebration.png.
- R1 schedule-aware streak: first completed workout -> 1-day streak shown in celebration. (Rest/missed-day break behavior not testable without multi-day clock manipulation.)
- R7 Beat chip / gold PR flash: on the FIRST-ever session there is no historical best, so no Beat: chip and no gold flash appeared during guided logging - this is CORRECT (nothing to beat). Needs a 2nd session exceeding a prior value to fully exercise (attempted below).
- Recap overhaul (item #10): PASS (comprehensive). Insights section, sub-tabs Recap/Stats/Progress/History. Recap: Session dropdown selector, no overview block, SESSION RECAP header + stat capsule (13 min/16 sets/3405 kg), EARNED THIS SESSION achievements w/ rarity (COMMON/RARE/EPIC colors, EPIC=purple), per-exercise rows (weight x reps + "First time - baseline" + "Best on record: X - today"), MUSCLES HIT THIS SESSION, effort breakdown, ADHERENCE 16/16, DURATION planned~40/actual 13, REST & PACING avg + explanation. Auros teal restyle throughout. Evidence 30/31/32/33_recap*.png.
- Finer muscle labels (item #14): PASS. "MUSCLES HIT THIS SESSION": Biceps 10 sets, Upper Back 10 sets, Front Delts 2 sets, Rear Delts 2 sets (fine-grained, not coarse Arms/Back/Shoulders), colored bars.
- R7 baseline capture: PASS. Each exercise "First time - baseline" + "Best on record: <load> - today". These become the beat targets for the next session.

- Stats sub-tab visual overhaul: PASS. THIS WEEK pulse card (16 sets +16 vs last wk, 1 session +1), stat tiles (Workouts/Sets/Total Volume 3.4t/Best Streak), WEEKLY VOLUME muscle heatmap (Back/Arms rows, 8-week cols), MUSCLE BALANCE bars (Back 10 blue, Arms 6 orange), CONSISTENCY 90-day calendar (today lit), REP RANGE DISTRIBUTION capsule. No CSV export button anywhere (confirms "CSV export gone from Stats").
- Heatmap tap -> Recap (item #11): PASS. Tapping the lit WEEKLY VOLUME "Back" this-week cell navigated to the Recap sub-tab with the Thu 02 Jul session selected. Accurate cell hit. Evidence 36_weeklyvol.png -> 37_heatmap_tap.png.

- Progress date-range picker (item #13): PASS. btn_progress_range default "All time"; opens themed Material date-range calendar (Filter progress by date, Start-End, Save). Picked Jul 1-2 -> button shows "1 Jul 2026 - 2 Jul 2026" + "Reset" (btn_progress_range_clear) appears; Reset returns to "All time". Evidence 39/40_range*.png.
- Progress exercise selector: PASS. Filterable AutoComplete (type "Lat" -> "Lat Pulldown (Underhand Grip)"). Selecting a loaded exercise shows HEAVIEST WEIGHT chart + Est 1RM ~76 kg (Epley 57.5x10 correct) + PR HISTORY row.
- BW reps chart (item #1): PASS. Selecting bodyweight "Weighted Pull-Up" shows a "REPS - Best working-set reps per session" chart instead of weight. Single-session sparse case shows "Not enough data yet" (graceful, no crash). Evidence 47_bw_reps_chart.png.

- R3 body-weight (Home trend + weigh-in): PASS (sparse). Home BODY WEIGHT card: Weight(kg) input + Log. 0-weigh-in state = input only, no trend, no crash. Logged 80 -> shows "02 Jul 2026  80 kg" entry. 1-weigh-in state graceful (no trend line drawn from single point - correct). Multi-point trend not testable in one day. Home also shows TODAY'S PLAN "session logged / View Recap" and MUSCLE RECOVERY (fine labels Front/Rear Delts, Biceps, Upper Back; effort-scaled hours-left, colored bars).
- NOTE: R3 "Progress chart" body-weight chart not located on the Progress sub-tab (only exercise strength/reps charts seen there). Body-weight trend surfaces on Home. Not necessarily a bug (may need >=2 weigh-ins or a different surface) - flagging for confirmation, not asserting a defect.

### F3 (HIGH, likely v1.24.0 REGRESSION from item #2 skeleton loaders) — History default list stuck on skeleton loaders; sessions never appear until you search
- Repro (100%, incl. after force-stop + fresh relaunch): open Stats bottom-nav -> "History" sub-tab. The SESSIONS list shows 3-4 empty skeleton placeholder cards that NEVER resolve to real content. Waited 5s+ multiple times; re-entered the tab; full app restart - always stuck. Tapping a placeholder does nothing (not a real card). uiautomator dump shows NO session text nodes in this state.
- The session DOES exist and renders correctly everywhere else: Recap session dropdown, Stats heatmap/muscle-balance, Progress exercise charts.
- Proof it is a bind/skeleton-swap bug (not missing data): typing a DATE in the search field ("Jul") immediately makes the real session card appear ("Thu, 02 Jul 2026", Edit date, 13 min, Show sets). So a search re-query binds the list; the initial/default load's skeleton->content swap never fires.
- Secondary: searching an exercise/muscle term ("Back") shows "No sessions yet. Complete a workout to see your history!" even though the session contains Back exercises - the empty-state copy is wrong for a no-match filter, and exercise-name search may not match (hint says "Search exercises or dates...").
- Impact: History browse (a core flow, and the destination of Home "View Recap"? no - that goes to Recap) appears permanently EMPTY on entry. Non-obvious workaround (type a date). No crash; data intact.
- Evidence: 50/51/53/54_history*.png (stuck skeletons across re-entries + fresh launch), 55_history_search.png ("No sessions yet" on "Back"), 56_history_searchjul.png (session renders on date search).
- Env note: reproduced with animations BOTH disabled (scale 0) and enabled (scale 1), so not an animator-scale artifact. Recommend a real-device confirmation given it's the Waydroid harness, but repro is very consistent.

### F2 (COSMETIC / low) — Bodyweight exercise shows "0 kg x 6" in Recap per-exercise row
- Repro: log a bodyweight exercise (Weighted Pull-Up, shows "BW" on log screen) -> Recap per-exercise list shows "0 kg x 6".
- Expected: "BW x 6" or "Bodyweight" for consistency with the log screen's "BW" label. "0 kg" reads as no load.
- Severity: cosmetic. Evidence 33_recap_musclelabels.png.

---
## WORKER #2 continuation (took over after worker #1 hit session limit). Same harness/APK (md5 195294a…, v1.24.0 vc63, re-verified on-device). Shots continue in same scratchpad shots/ dir.

### F3 REFINED (upgrade — HIGH, likely v1.24.0 REGRESSION) — History SESSIONS list is PERMANENTLY stuck on skeletons; the date-search "workaround" does NOT reproduce
Worker #1 found the list stuck on default but claimed typing a date ("Jul") reveals the session. I could NOT reproduce ANY workaround this session. Exhaustive matrix (fresh force-stop+relaunch each baseline, animations off, IME + adb input both tried, keyboard dismissed before every read so nothing is hidden below the fold):
- Default entry (empty search, range "All time"): skeletons, session absent. (shot 61) 100%.
- Wait 25 s untouched on default: still skeletons, does NOT self-resolve. (shot 76)
- Apply date range 1 Jul–2 Jul (INCLUDES the Thu-02-Jul session) + Save: range button + "Reset" update correctly, but list STAYS skeletons — session never binds. (shots 62/63/64) => **Range filtering does NOT bind the list.**
- Reset range back to All time: still skeletons. (shot 67)
- Search "Jul" typed via adb input, keyboard dismissed: skeletons. (shots 66)
- Search "Jul" typed via **Maestro IME** (field confirmed = "Jul", cursor present, keyboard dismissed): skeletons — session absent. (shots 73/75) => could NOT reproduce worker #1's shot-56 result.
- Warm path: visit Recap (loads the session via dropdown) then return to History, then search "Jul": still skeletons. (shots 77/78) => "warm data in memory" hypothesis disproven.
- Data provably EXISTS: same session renders in Recap dropdown (shot 70), Stats heatmap, Progress charts.
- ANSWER to the F3-refinement question: **NO — picking a date range does NOT bind the list the way typing a search (per worker #1) supposedly does. In this session NOTHING binds it; the list is effectively permanently empty on History.** The search-bind worker #1 saw (shot 56) appears to be a non-deterministic race, not a reliable workaround.
- Diagnosis pointer: the SESSIONS RecyclerView/Compose loader never swaps skeleton->content; neither the initial Flow emission, the range-filter re-query, nor the search TextWatcher re-query drives a successful bind on a cold app process. Worker #1's one success was likely leftover in-memory state right after workout completion.
- Severity HIGH: History browse (a core surface) is unusable/empty with no reliable user workaround. No crash; data intact elsewhere. Harness = Waydroid; recommend a real-device confirm, but repro here is 100% across ~10 attempts and 2 input methods.
- Evidence: shots 61,62,63,64,66,67,73,75,76,77,78. Flows: scratchpad/f3_search_bind.yaml, f3_ime_only.yaml.

### R7 second-session (Beat chip + gold PR flash) — SETUP BLOCKER (harness), see below for resolution attempts
- Goal: start a 2nd guided session containing a session-1 exercise, log a set beating a prior best (Lat Pulldown > 57.5 kg), verify "Beat:" chip + gold PR flash + no re-flash on resume + celebration old->new.
- BLOCKER: "Start Workout" only exists on Home for an UN-logged today. Today (Thu 02 Jul) is fully logged -> Home shows only "Today's session is logged. Great work! / View Recap" (shot 83). Program tab has NO start action for any day (only Edit/up/down/Delete + "+ Add exercise"; shots 80/81/86). Recap screen has no add/continue/repeat action (shot 87). The "do another day's workout today" (v1.11.0 P2) affordance is not present while today is already worked out.
- Only Thursday's plan contains the 5 session-1 exercises (the beatable records). Other days do NOT repeat any: Mon=Bench/Bent-Over Row/Incline DB Press/Pec Deck/Seated Cable Row; Tue=Squat/Leg Ext/Lying Leg Curl/RDL; Wed=Shoulders; Fri=Legs. So no other day can beat a session-1 record.
- Clock-advance to make "today" un-logged is NOT possible: Waydroid shares the host kernel clock (no time namespace); `date` set via root reverts instantly even with auto_time=0 (verified). Changing the Pi HOST clock is out of scope (would disrupt the user's whole desktop/services). => harness limitation.
- Next attempt: App Settings day-reset boundary trick (shift logical-day boundary so "now" is a fresh logical day while the earlier session's records persist). Result recorded below.

### Stage-3 items 4 & 5 (Profile tab) — PASS (with one observation)
- Item 5 PASS: the OLD 4-stat block is GONE. Profile now = section header "PROFILE / You" -> Settings row (↗) -> L2/Novice XP orb + XP bar (290 XP • 510 to Level 3) -> "PRS · LAST 7 DAYS" card -> "ACHIEVEMENTS (17/200)" expandable. No 4-stat row anywhere. (shots 88/89/90/91)
- Item 5 PASS: R5 achievement gallery + NEXT UP strip INTACT (not clobbered). NEXT UP shows Getting Started (Reach Level 3, 2/3), First Harvest (Earn 500 total XP, 290/500), Double Down (Complete 2 workouts, 1/2) — each with icon + COMMON rarity tag + visible progress bar. Expanding ACHIEVEMENTS reveals categories ("Workouts 1/37 ▸" navigable). (shots 89/91)
- Achievements count = 17/200 — HEALTHY. The old orphan-rows bug (count >200, e.g. 286) does NOT reproduce here. (Denominator 200, 17 unlocked.)
- Item 4 PARTIAL/OBSERVATION: "PRS · LAST 7 DAYS" card is PRESENT but shows empty-state "No PRs in the last 7 days — your next one is waiting." even though session-1 (TODAY) captured 5 per-exercise "Best on record" baselines. => The app treats a first-ever lift (baseline) as NOT a PR. Task item 4 expected baselines might surface here; they don't. Likely BY DESIGN (a baseline has nothing to beat), but flagging for product confirmation. The POPULATED state (a real session-2 PR) could NOT be verified because the 2nd-session start is blocked (see R7 above). Severity: low / needs-product-decision, not a defect per se.

### R7 BEAT/FLASH — BLOCKED (harness/data). Baseline half already PASS (worker #1).
Cannot obtain a 2nd guided session that beats a session-1 record:
- Day-boundary trick FAILED: App Settings "Day starts at" picker only offers 00:00-06:00 (early-morning day-start hours). Cannot pick a boundary between afternoon session-1 (~17:45, "trained 3h ago") and evening now (20:45), so the completed session can't be re-attributed to a previous logical day to free "today". (shots 99/100)
- Clock-advance FAILED: Waydroid shares host kernel clock; guest date reverts instantly (auto_time=0 tested), and changing the Pi host clock is out of scope.
- No in-app re-start for a completed day; "do another day's workout today" is gated behind today-not-yet-worked-out.
- Only remaining lever = delete session-1, but that also deletes the records to beat.
=> R7 Beat chip + gold PR flash + no-re-flash-on-resume + celebration old->new = NOT VERIFIED (harness-blocked). Recommend real-device / debug build with 2 real sessions across 2 days. Worker #1 already PASS'd the correct NO-flash-on-first-session + baseline capture.

### R2 (four notification toggles) — PASS
App Settings > NOTIFICATIONS has FOUR independent toggles, each with switch + description:
1. Workout-day reminder (OFF) — notify on days with an unlogged planned session; rest days silent.
2. Streak warning (ON) — "Warn me at 20:00"; evening nudge when today's session unlogged and a 2+ day streak would end.
3. Weigh-in reminder (OFF) — weekly body-weight reminder; skipped if already weighed in.
4. Program ready (ON) — notify when a background AI generation finishes.
- Individually toggleable CONFIRMED: flipped Weigh-in reminder OFF->ON (checked false->true, shot 98) then back OFF. (shots 96/97/98)
- Actual delivery not exercised (time/condition-gated; per task "don't force it"). Worker #1 saw the POST_NOTIFICATIONS runtime prompt at first-run.

### Stage-3 item 6 (Settings IA — App Settings first) — PASS
Settings order: App Settings (first) / Training Profile / AI & Program / Exercise Library / Backup & Data / About. App Settings = DAY BOUNDARY (default 04:00) + AUTO-REBALANCE (ON) + the 4 NOTIFICATIONS toggles. (shots 92/93/94)

### Stage-3 item 7 (Debug moved to About; removed from Backup) — PASS
- About screen rows (in order): 🏋️ Adaptive Workout Planner / Installed version v1.24.0 / What's new in this version / SOFTWARE UPDATE (Check for Updates) / **Debug (LAST row)** — "Prompt log, rejection log, crash log, diagnostics". (shot 101)
- Backup & Data screen: BACKUP (Export Backup, Import Backup) + CLOUD BACKUP (not configured; Connect/Back up/Restore). NO "Debug" anywhere (grep count 0). (shot 102)
- Cloud backup shows "not configured" (expected — Google OAuth client unconfigured; manual Export/Import is the live path).

### Stage-3 item 8 (Exercise Library detail — two-frame image alternation) — PASS (2-image case)
- Exercise Library = 873 exercises, search + All muscles/All equipment filters.
- "3/4 Sit-Up" detail: image ALTERNATES between two distinct frames (up-position / down-position) ~every 600-1200ms — md5 pattern A/B/A/B/A over 400ms samples. (shots 104_frame_1 vs 104_frame_2 are visibly the two sit-up positions)
- "90/90 Hamstring" detail: same — alternates 2 frames (md5 A/B/A/B). (shots 105_ex2_*)
- Static (1-image) case NOT located — both exercises I sampled have 2 images. Alternation logic is confirmed working; the static-for-single-image path is low-risk and unverified (would need an exercise known to have exactly 1 image).

### Skeleton-scope check (is F3 stuck-skeleton History-only?) — F3 is HISTORY-ONLY
- Cold start (force-stop -> relaunch) -> Stats bottom-nav -> each Insights sub-tab:
  - Recap: binds (SESSION RECAP / session content present). (shot 106)
  - Stats: binds fully (THIS WEEK 16 sets +16 / 1 session +1; tiles 1 Workouts/16 Sets/3.4t/1 Best Streak; WEEKLY VOLUME heatmap). No skeletons. (shot 107)
  - Progress: binds (range button + exercise selector + content). (shot 108)
- => The stuck-skeleton bug is SPECIFIC to the History SESSIONS list. Recap/Stats/Progress loaders swap skeleton->content correctly on a cold process. This narrows F3 to the History fragment's list-bind path only.

### v1.22.0 rest-ux regression pass (item #6) — PARTIAL (2 of 4 verified; 2 blocked by no-live-session)
- Manual rest mode + per-type times: PASS. Settings > Training Profile > "Use my own rest times" toggle (OFF by default). Enabling it reveals per-type editable fields: "Heavy compounds (m:ss) = 3:00" and "Accessories (m:ss) = 1:30", with note "The rest timer uses these instead of the AI's suggestions, and generated programs are sized around them." Toggling OFF (discard via Back, no Save) hides them and reverts cleanly (verified checked=false, fields gone). (shots 109/110)
- Program ~Xm labels: OBSERVED consistent. Program day exercise cards each show a per-exercise time chip (~6m/~7m/~12m) and the day header shows a total (Thursday ~38m ≈ sum). Consistent across cards. (shots 79/80)
- Exercise timer persists across backgrounding + app kill/relaunch mid-workout: NOT VERIFIED — requires an ACTIVE guided session, which cannot be started (today logged; see R7 blocker). BLOCKED (harness/data).
- Session rest memory (rest adjustment sticks for the session): NOT VERIFIED — same live-session blocker. BLOCKED.
- Note: the manual-rest toggle also coexists with B08 rest-day two-mode on the same screen ("Let the AI choose which days to train" OFF; rest days Sat+Sun checked; Training 5 days/week Mon-Fri; Session duration 45 min) — all render correctly.

### Backup export -> import round-trip (item #7) — PASS (merge idempotent); UI export-share + true clear-restore = harness-limited
- EXPORT CONTENT PASS: Export writes `/data/data/com.migul.treningsprogram/cache/treningsprogram-backup-2026-07-02.json` (schema_version 5, 39519 bytes) BEFORE the share step. Pulled via root; verified it contains ALL expected data:
  - session (1, 13 min, completed), 16 sets (name/muscle/reps/weight e.g. Cable Bicep Curl 15kg x12), 200 achievements w/ unlock state, user_stats (290 XP / level 2 / streak), 1 body_measurement = 80.0 kg weigh-in, 24 planned_exercises, program meta, 3 gym_presets, settings dict (goal Hypertrophy / exp Intermediate / duration 45 / equipment list / rest fields / weekly challenges).
- EXPORT UI SHARE = harness-blocked: Export fires ACTION_CHOOSER (share sheet, confirmed via logcat ChooserActivity) -> "No apps can perform this action" because Waydroid has no share-target apps. Well-formed intent; NOT an app bug. (shots 111/113)
- IMPORT PASS: Import Backup opens DocumentsUI (ACTION_OPEN_DOCUMENT) fine. Staged the exported JSON in /sdcard/Download, selected it -> confirm dialog "Import Backup? This merges the backup file into your current data. Existing workouts/achievements/measurements are kept — nothing is deleted; backup entries are added in. Stats recomputed, settings reconciled. Older backup upgraded automatically." -> Import. (shots 114-119)
- MERGE IDEMPOTENCY PASS (the key round-trip check): re-importing the app's OWN identical backup did NOT duplicate anything — after import Stats still show 1 Workout / 16 Sets Logged / 3.4 t / Best Streak 1, and Profile still 290 XP / L2 / 17/200 achievements (NOT doubled). Dedupes by primary key. (shots 120 + profile dump)
- NOT tested: import into a CLEARED/empty state (true restore) — would require the destructive Reset which wipes everything and forces API re-onboarding (out of budget/scope). Full-content export + idempotent merge together demonstrate the round-trip is sound.
- Cleanup: staged /sdcard/Download backup removed; device left clean.

---
## WORKER #2 CONSOLIDATED LEDGER (folds in worker #1)
Env: Waydroid 192.168.240.112:5555, Maestro 2.6.1, app v1.24.0 vc63 md5 195294a… (re-verified). Live API calls this stage: 0 by worker #2 (running total 2/20 — worker #1's onboarding gen). No generation calls made.
| Area | Verdict | Notes |
|---|---|---|
| History date-range picker (item 12) + F3 | RANGE PICKER RENDERS OK; **F3 upgraded to HIGH** | Range apply/reset does NOT bind list; no reliable workaround reproduces; list permanently skeletons. History-only. |
| Profile items 4/5 | PASS (item 5); item 4 observation | 4-stat block gone; R5 gallery+next-up intact; achievements 17/200 healthy; PRs-last-7-days empty (baselines != PRs, by design?) |
| Settings item 6 (App Settings first) | PASS | |
| Settings item 7 (Debug in About, not Backup) | PASS | |
| Settings item 8 (exercise 2-frame image alt) | PASS (2-img); static-1-img not located | |
| R2 (4 notification toggles) | PASS | individually toggleable confirmed |
| Rest-ux (item #6) | PARTIAL | manual rest mode + per-type times PASS; Program ~Xm labels OK; timer-persist + session-rest-memory BLOCKED (no live session) |
| Backup round-trip (item #7) | PASS | export content complete; import merge idempotent; UI export-share + clear-restore harness-limited |
| Skeleton scope (item #8) | DONE | F3 is History-ONLY (Recap/Stats/Progress bind fine) |
| R7 second-session beat/flash (item #1) | BLOCKED (harness) | cannot start 2nd session; baseline half already PASS (worker #1) |
BLOCKED items all trace to: (a) can't start a 2nd/fresh guided session (today logged; no re-start; Waydroid shared-clock; day-boundary limited to 00:00-06:00), (b) no share-target apps for backup export UI. Device left clean (auto_time=1, date correct, no crashes).

