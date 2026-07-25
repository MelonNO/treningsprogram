---
name: project-achievements-orphan-rows
description: "Known unfixed bug — achievements table accumulates orphan rows, so the app shows more achievements than are defined (e.g. 286 vs 200). User chose NOT to fix."
metadata: 
  node_type: memory
  type: project
  originSessionId: 94237cab-1ba8-4b81-9250-68349a7eb770
---

The app's achievements count is dynamic (`ProfileFragment`: `state.achievements.size` = row count in the `achievements` table), not a hardcoded total. `GamificationRepository.ensureAchievementsSeeded()` only ever **inserts** (`@Insert(onConflict = IGNORE)`) and never deletes. When achievement IDs are renamed/replaced across versions, the old rows stay in users' DBs forever. So a device that ran the 200-achievement build before an overhaul can show **286** (= 200 current + 86 orphaned replaced IDs).

**Why it matters:** Do NOT treat a >200 achievement count as a fresh bug to investigate again — this is the known cause. A clean install shows the correct count.

**The fix (NOT applied — user explicitly said "dont apply this" on 2026-06-23):** add `@Query("DELETE FROM achievements WHERE id NOT IN (:validIds)") suspend fun deleteNotIn(validIds: List<String>)` and call it after `insertAll` in `ensureAchievementsSeeded()`, passing `PREDEFINED_ACHIEVEMENTS.map { it.id }`. Prunes orphans while preserving unlock state of the valid 200. Only implement if the user asks.

Related review findings (also reported, not fixed): duplicate display name "Diamond" (`workouts_60` + `diamond_level`), and `combo_hercules`/`combo_strength` share identical description AND unlock condition (`sp>=5 && vol>=3000f`) so they always unlock together. See [[project-treningsprogram]].
