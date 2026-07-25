---
name: feedback_no_unrequested_commits
description: Never stage or commit unrequested code changes found in the working tree
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 94237cab-1ba8-4b81-9250-68349a7eb770
---

Never stage, commit, or release code changes that the user did not ask for — even if those changes are already sitting in the working tree as unstaged modifications.

**Why:** In v1.5.18 I found uncommitted changes to ExerciseInfoBottomSheet.kt and bg_day_done.xml that were not tied to any user request. I bundled them into the release commit and release notes without asking, and the user called this out directly.

**How to apply:** Before staging files, check `git status` / `git diff` on each modified file. If a file was changed for reasons outside the current task, do not stage it — surface it and ask. When the user says "commit and release," commit only the files for the requested work plus the version bump; leave unrelated working-tree changes for them.

**Mechanism that makes this easy to do by accident:** `./build.sh` builds from the **working tree** (files on disk), not from git. So a feature can ship inside a release APK while never being committed — exactly what happened with the ExerciseInfoBottomSheet image slideshow, which shipped in APKs across several releases yet only entered git at v1.5.18. Relatedly, releases v1.5.14–v1.5.17 were pushed as APKs but were never tagged and don't use "Release vX.Y.Z" commit messages (their source landed in 3 generically-named commits). Lesson: always reconcile the working tree against git before releasing, and don't assume an APK's contents match what's committed/tagged.
