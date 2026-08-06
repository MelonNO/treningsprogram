# Intake batch — Weight steps, plate readout, title bands, plateau detection, exercise feedback (2026-08-06)

**Prepared for:** Project-lead orchestrator
**Source:** Five user requests (the user's own numbering 1–5, preserved as brief IDs 01–05), clarified over three rounds.
**Status:** **CONFIRMED by the user.** Replies were relayed verbatim via the coordinator; the confirming word — *"correct"* — is the user's own, given against the full restatement of all five items.
**Baseline:** post-v1.35.0 (`d04d221` on `main`).

> Creating these documents does **not** dispatch the orchestrator. That is a separate user instruction.

---

## Items

| ID | Title | Type | Brief file | Status |
|----|-------|------|-----------|--------|
| 01 | Weight +/− buttons step to the next sensible, achievable weight | Improvement | `brief-01-smart-weight-steps.md` | Confirmed |
| 02 | Per-side plate readout is always correct while visible | Bug | `brief-02-plate-readout-freshness.md` | Confirmed |
| 03 | Title band: wash reaches the screen edge, all four tabs match | Bug (visual) + sweep | `brief-03-hero-band-consistency.md` | Confirmed |
| 04 | Adding reps at a lower weight must not be called a plateau | Bug — **diagnose first** | `brief-04-plateau-rep-progress.md` | Confirmed |
| 05 | Per-exercise feedback that shapes the next generated program | New feature | `brief-05-exercise-feedback-to-ai.md` | Confirmed |

Nothing was merged. All five are distinct pieces of work, but two pairs share files — see below.

---

## Merge / cluster / parallelisation guidance

### Cluster A — items 01 + 02: **one worker, not two**

Both items live in **the same file, in the same handful of lines**:
`app/src/main/java/com/migul/treningsprogram/ui/log/LogWorkoutFragment.kt`.

- 01 rewrites what the **+/−** buttons produce (lines 94–104).
- 02 fixes what those same buttons **fail to refresh** (`reseedWeightKeypadIfOpen`, line 493, which
  omits `updatePlateHint`, line 456).

Splitting these across two workers guarantees a conflict, and 02's acceptance criteria explicitly
reference 01's new button behaviour. **Do 01 and 02 as a single unit, 01 first.**

### Cluster B — items 04 + 05: **serialise, same file**

Both change what the AI generator is told, in
`app/src/main/java/com/migul/treningsprogram/data/repository/AiRepository.kt`, **in the same region**
(around lines 1844–1881, where the per-exercise trend and stalled-lift blocks are assembled).

- 04 (improvement C) replaces the weight-only `PLATEAUED` trend label at line 1858 with a rep-aware
  judgement, so the AI and the Progress screen agree.
- 05 adds a new user-feedback block to the same prompt assembly.

**Do not run these in parallel.** Suggested order: **04 → 05**. 04 is a correction to an existing
signal and is the smaller change; 05 builds on a prompt that is already correct. 04 also has a
second, independent home in `domain/StallDetector.kt` that 05 never touches.

### Item 03 — fully independent

Layout-only, touching four fragment layouts and no shared code with anything else in this batch. Can
run in parallel with either cluster, at any time.

### Summary

| Group | Items | Parallelism | Notes |
|---|---|---|---|
| A | 01 → 02 | **One worker** | Same file, same lines. 02 verifies against 01's output. |
| B | 04 → 05 | **Serialise** | Same prompt-assembly region. 04 first. |
| — | 03 | **Independent** | Layout only. Parallel with anything. |

A and B are independent of each other and can run concurrently.

### Cross-group hazards

- **B carries the batch's real risk.** Both items change what the AI is told, and generation quality
  has been the most repeatedly painful area of this app. Changes here want careful review and the
  standing frugal-API rule respected.
- **04 is diagnose-first.** Two candidate causes are documented in its brief. They are orientation,
  not a prescribed fix — confirm before acting, and check both, because either alone could produce
  the user's report.
- **03 cannot be proven by unit tests.** It is a visual change; the user's device check is the real
  proof.

### Suggested order

1. **03** — smallest, independent, zero risk to anything else. Good early win.
2. **01 → 02** — one worker, self-contained, testable.
3. **04** — diagnose first, then fix, then extend to the AI label (improvement C).
4. **05** — largest item; schema change, backup bump, new UI on two screens, prompt change.

---

## Confirmed decisions (the user's own answers)

**Item 01**
1. The **app decides** the step. No per-exercise user override. (1c)
2. Step **scales with the magnitude of the load**, not a per-exercise name list — chosen because AI-generated exercise names would fall off any maintained list. (Improvement A)
3. + and − **snap to the next achievable weight**, including from odd hand-typed values. (1b)
4. **No** per-gym dumbbell-rack or machine-stack entry — the user was offered this and declined. (1a-i)
5. The "always loadable" guarantee is **absolute for barbells and plate-loaded dumbbells**, and a sensible default step applies where the app is blind (fixed dumbbells, machines, cables). The user was shown this trade-off explicitly and confirmed it. (Q1)
6. **−** mirrors **+** exactly. (1d)

**Item 02**
7. Fixed **generally** — "the readout is correct whenever it is visible" — not as a patch to the single reported trigger. Includes the gym-not-yet-loaded case the user has not personally seen. (Improvement B)

**Item 03**
8. Direction: the other three tabs match **Stats' full-bleed wash**, not the reverse. (3a)
9. **Text stays indented**, styled as Stats does it; only the colour extends to the edge. (3b)
10. The teal **accent bar goes on all four tabs, including Stats** — the user was offered the opposite resolution (remove it from the other three) and explicitly chose to add it to Stats. (Q2)
11. This is a **sweep**, not a point fix. (3c)

**Item 04**
12. Adding reps at the same weight is progress, **even when an older heavier set still scores a higher estimated 1RM**. (4f)
13. Reported surface: the **Stats → Progress "Plateau detected" card**. (4a-i)
14. The extra reps were on the **lower** weight. (4c)
15. **Bodyweight lifts: no change.** The user was asked and declined. (4d)
16. The AI's separate weight-only plateau label is brought in line with the rep-aware rule. (Improvement C)

**Item 05**
17. Feedback given from the **logging screen and the Program tab**. (5f)
18. **Quick selectable reason + optional free text.** (5b)
19. Treated as a **hint**, never a hard filter. (5c)
20. Default response to "can't do this yet" is **keep it and program toward it**, not drop it. (5a-ii)
21. Applies to **that exercise only**, at every gym. (5e)
22. Feedback gets a **review-and-undo home**. (Improvement D)

**All four proposed improvements (A, B, C, D) were explicitly accepted** — the user answered
"implement" to each, adding one constraint to A: *"it must always be loadable with the weights
available in that gym."*

---

## Decisions made under delegation — **veto-able**

The user answered item 5d with **"u choose the best solution"**. These were decided per standing
practice, shown back to the user, and drew no objection.

- **D1** — Feedback is **its own mechanism**, not folded into the existing exclusion lists. Those are hard filters; this is a nuanced hint, and merging them would turn *"this is hard"* into *"never show me this"*.
- **D2** — It gets a **review screen in Settings → Training, beside "Exercises to exclude"**, so everything steering the AI is visible and undoable in one place.
- **D3** — Feedback **persists until removed**, and the AI is told **when** it was given, so old feedback fades in weight as the user improves.

---

## Assumptions applied (user may override)

- **A1** — The six proposed feedback reasons in brief 05 were shown to the user and not objected to, but never ratified item by item.
- **A2** — One piece of feedback per exercise, replaced on re-submission, rather than an accumulating log. Never put to the user.
- **A3** — The exact affordance for reaching "leave feedback" is the builder's, bound by "must not slow down logging". The logging screen has **no exercise-level menu** today, so something new is needed.
- **A4** — Item 03's sweep covers the **four bottom-nav tabs only**. The Stats sub-tabs have no title band of their own, and the Recap tab's per-session header was judged out of scope.

---

## Open decisions the user deferred — **flag before building**

| Item | Question | Note |
|---|---|---|
| 01 | What should +/− do on **bodyweight** exercises (empty or zero weight)? | Never asked. Item 04's answer (4d) shows the user thinks of these as rep-driven. |
| 01 | Should the **calculator pad's own typed + and −** also snap to a loadable weight? | Items 1 and 2 are both about the *external* buttons. Recommend leaving typed arithmetic alone as explicit user intent — **not confirmed**. |
| 03 | Should the **Recap tab's per-session header** also change? | Uses the same wash and eyebrow styling, but is a session header, not a screen title band. Recommend leaving alone. |

---

## Cross-cutting constraints

- Build via **`./build.sh`**, never `./gradlew` directly.
- **No commits or releases** unless the user asks.
- **No on-device / automated UI tests** (standing rule). Verify via build + unit tests only; the user does the device and live check.
- **Live Anthropic API calls stay minimal and decision-driven** — a past sweep drained both the API credits and the monthly spend limit. Relevant to cluster B.
- Item 05 implies a **Room schema change and a backup export/import version bump**: exported backups must round-trip the new data, and older backups must still import.
- Item 03 is **visual** — unit tests cannot prove it.
