# claude-env — machine transfer bundle

Everything needed to reproduce this project's working environment — the Android
build setup **and** the Claude Code CLI configuration (custom agents, memory,
settings, plugins) — on a different machine and a different CPU architecture.

Created 2026-07-25 from the Raspberry Pi 5 (aarch64) original.

## What's in here

| Path | What it is | Restored to |
|------|-----------|-------------|
| `agents/user/` | `project-lead-orchestrator` agent definition | `~/.claude/agents/` |
| `agents/project/` | `intake-understanding`, `ui-test-worker` agent definitions | `<repo>/.claude/agents/` |
| `settings/user-settings.json` | model, effort level, theme, enabled plugins | `~/.claude/settings.json` |
| `settings/user-settings.local.json` | user-scope permission allowlist | `~/.claude/settings.local.json` |
| `settings/project-settings.local.json` | project-scope permission allowlist | `<repo>/.claude/settings.local.json` |
| `memory/` | main-session memory (41 files + `MEMORY.md` index) | `~/.claude/projects/<encoded-path>/memory/` |
| `agent-memory/` | per-agent memory (orchestrator, intake, ui-test-worker) | `<repo>/.claude/agent-memory/` |
| `plugins/` | marketplace + installed-plugin manifests (reference) | see [Plugins](#4-plugins) |
| `claude-md/global-CLAUDE.md` | the home-dir `CLAUDE.md` from the Pi | optional, see below |
| `restore.sh` | does steps 1–3 below automatically | — |
| `SECRETS.md` | the 5 files that are **not** in git and must move out-of-band | — |
| `BUILD-ENV.md` | Android/Java/Gradle setup, incl. the aarch64-only workarounds | — |
| `STATE.md` | where the work actually stands: branches, worktrees, next steps | — |

The project's own `CLAUDE.md` is at the repo root and is already version-controlled —
nothing to do for it.

## Restore, start to finish

### 0. Clone

```bash
git clone https://github.com/MelonNO/treningsprogram.git
cd treningsprogram
git checkout qol-batch-2026-07-25     # the active branch — see STATE.md
```

### 1–3. Run the restore script

```bash
./claude-env/restore.sh
```

It installs the agents, settings, and both memory stores, backing up anything it
would overwrite to `*.bak-<timestamp>`. Pass `--dry-run` to see the plan first.

If you prefer to do it by hand, the script is short and every step is a plain
`cp` — read it.

> **Memory path note.** Claude Code keys per-project memory by the *absolute path*
> of the project, with `/` replaced by `-`. On the Pi that made
> `~/.claude/projects/-home-migul-treningsprogram/`. If the new machine has a
> different username or clone location, the directory name changes — `restore.sh`
> computes it from `$PWD`, so just run it from the repo root.

### 4. Plugins

Two plugins were enabled. `settings.json` already declares them, so Claude Code
will offer to install them on next launch. If it doesn't, add them manually:

```
/plugin marketplace add nextlevelbuilder/ui-ux-pro-max-skill
/plugin marketplace add forrestchang/andrej-karpathy-skills
/plugin install ui-ux-pro-max@ui-ux-pro-max-skill
/plugin install andrej-karpathy-skills@karpathy-skills
```

Versions that were running here (`plugins/installed_plugins.json` has the pinned
commit SHAs if you need the exact ones):

- `ui-ux-pro-max` **2.6.2** — commit `8e43c9d`
- `andrej-karpathy-skills` **1.0.0** — commit `2c60614`

`plugins/*.json` are **reference only** — do not copy them into `~/.claude/plugins/`.
They contain absolute install paths from the Pi that won't exist on the new box.

### 5. Secrets — the part git can't do

Five files are deliberately not in this repo. **Read `SECRETS.md`** and move them
across yourself (USB, `scp`, password manager). Without them: no signed release
builds, no live API generation, no `git push` over HTTPS.

### 6. Build environment

See `BUILD-ENV.md`. Short version for an **x86_64** host: install JDK 21 +
Android SDK 34 (platform + build-tools 34.0.0), write `local.properties`, drop
`QEMU_LD_PREFIX` from `build.sh`, and you're done — the QEMU shim was purely an
aarch64 workaround for `aapt2`.

### 7. Claude Code itself

The Pi ran **Claude Code 2.1.220** on **Node 24.16.0**.

```bash
npm install -g @anthropic-ai/claude-code
claude          # then /login
```

Auth does not transfer — `~/.claude/.credentials.json` is an OAuth token bound
to this install. Log in fresh on the new machine.

### 8. Verify

```bash
./build.sh test          # unit tests — baseline is 916 tests, both variants green
claude
# then, inside Claude Code:
/agents                  # should list project-lead-orchestrator, intake-understanding, ui-test-worker
/memory                  # should show the restored MEMORY.md index
```

## The home-directory CLAUDE.md

`claude-md/global-CLAUDE.md` is the `CLAUDE.md` that sat at `/home/migul/` on the
Pi. It documents that machine's **labwc/Wayland desktop configuration** — panel,
GTK dark-theme layers, autostart. It is not about this project and is almost
certainly irrelevant on the new machine. `restore.sh` does **not** install it.
It's included only so nothing is lost in the move.

## How this environment is meant to be driven

The memory files carry the full working agreement, but the load-bearing rules are:

- **The coordinator (main session) never codes, edits, builds, or ships.** It
  spawns agents and verifies their work with read-only tools.
- **The orchestrator** (`project-lead-orchestrator`) does the work itself by
  default, spawning workers only for genuine parallelism or isolation. It owns
  build, tests, version choice, and the release step.
- **`intake-understanding`** runs first for large or ambiguous requests, and its
  output is relayed to the user verbatim.
- **`ui-test-worker` is dormant** — there is a standing instruction never to run
  Waydroid/Maestro/on-device UI tests. Verification is build + unit tests only;
  the user does the device check.
- **Never push a GitHub release unless explicitly asked.**

See `memory/feedback_orchestrator_owns_changes.md`,
`memory/feedback_agent_usage_guideline.md`, and
`memory/feedback_always_skip_waydroid.md` for the full versions.
