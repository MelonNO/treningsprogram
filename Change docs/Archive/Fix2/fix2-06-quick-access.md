# fix2-06 · Quick-access exercise menu (inspect / jump / add)

**Type:** Feature. **Merged from old 7 + 9.**

## Context
Native Android app. During a workout, the top **"Exercise X / Y" progress bar** is shown.

## Desired behavior

**Open & inspect**
- Tapping the top progress bar opens a **quick-access** window listing **all exercises for that day**, clearly showing which are **finished**, which is **current**, and which are **upcoming / left**.

**Jump**
- Tapping an exercise jumps directly to it.
- Jumping to a **finished** exercise behaves exactly like pressing **Back** to it (so it can be viewed and have sets edited/added, just as Back allows).
- **Nothing logged is ever lost** by navigating around.

**Add an exercise**
- The list includes an **"Add exercise"** empty entry.
- Tapping it opens a window to **search the local exercise database**.
  - Selecting a found exercise adds it.
  - If the exercise isn't found, an **"Add anyway"** option creates a **custom exercise** (placeholder image, no DB info, fully loggable).
- An added exercise is inserted **immediately after the current exercise**: if the user is on exercise 2, the new one becomes exercise 3 and the old 3→4, 4→5, etc. The **X / Y** count updates accordingly.
- **Custom (added) exercises have no AI target** — the user logs them freely (own sets/reps/weight).
- Additions apply to the **current session** (the plan is per-session anyway).

## Acceptance
- [ ] Tapping the top progress bar opens the quick-access window listing the day's exercises with finished / current / upcoming status.
- [ ] Tapping an exercise jumps to it; jumping to a finished one behaves like Back (viewable/editable), losing nothing.
- [ ] An "Add exercise" entry opens local-DB search; selecting one inserts it **right after the current exercise** and renumbers the rest; X/Y updates.
- [ ] "Add anyway" creates a custom, loggable exercise with placeholder image and no DB info.
- [ ] Custom exercises have no AI target and are logged freely.
- [ ] Exercise order and the X/Y count stay correct after additions and jumps; nothing logged is lost.

## Coordination / constraints
- Touches active-session state & navigation — same area as Item 1 (persistence). **Do Item 1 first** (or same agent) to avoid conflicts.
- Nothing lost on navigation; verify on-device (Maestro / AVD).

## Decisions (pick a sensible default, report)
- Visual style of the quick-access window and how DB search results are presented.
