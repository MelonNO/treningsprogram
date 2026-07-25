#!/usr/bin/env bash
# Restore the Claude Code environment from claude-env/ onto this machine.
# Safe to re-run: anything it would overwrite is backed up to *.bak-<timestamp>.
#
#   ./claude-env/restore.sh            apply
#   ./claude-env/restore.sh --dry-run  show what would happen, change nothing

set -euo pipefail

DRY_RUN=0
[[ "${1:-}" == "--dry-run" ]] && DRY_RUN=1

BUNDLE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$BUNDLE/.." && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"

# Claude Code keys per-project state by absolute path with '/' -> '-'.
PROJECT_KEY="${REPO//\//-}"
MEMORY_DEST="$HOME/.claude/projects/$PROJECT_KEY/memory"

say()  { printf '  %s\n' "$*"; }
head2() { printf '\n\033[1m%s\033[0m\n' "$*"; }

run() {
  if (( DRY_RUN )); then
    say "would: $*"
  else
    "$@"
  fi
}

# Back up a path if it already exists and differs from what we're about to write.
backup() {
  local target="$1"
  [[ -e "$target" ]] || return 0
  if (( DRY_RUN )); then
    say "would back up: $target -> $target.bak-$STAMP"
  else
    cp -r "$target" "$target.bak-$STAMP"
    say "backed up: $target -> $target.bak-$STAMP"
  fi
}

copy_into() {         # copy_into <src-dir> <dest-dir>  (merges, backs up dest)
  local src="$1" dest="$2"
  backup "$dest"
  run mkdir -p "$dest"
  if (( DRY_RUN )); then
    say "would copy: $src/. -> $dest/"
  else
    cp -r "$src/." "$dest/"
    say "copied: $(basename "$src")/ -> $dest/"
  fi
}

copy_file() {         # copy_file <src-file> <dest-file>
  local src="$1" dest="$2"
  backup "$dest"
  run mkdir -p "$(dirname "$dest")"
  if (( DRY_RUN )); then
    say "would copy: $src -> $dest"
  else
    cp "$src" "$dest"
    say "copied: $(basename "$src") -> $dest"
  fi
}

(( DRY_RUN )) && printf '\n*** DRY RUN — nothing will be written ***\n'

printf '\nrepo:        %s\n' "$REPO"
printf 'project key: %s\n' "$PROJECT_KEY"

head2 "1. Agents"
copy_file "$BUNDLE/agents/user/project-lead-orchestrator.md" \
          "$HOME/.claude/agents/project-lead-orchestrator.md"
copy_into "$BUNDLE/agents/project" "$REPO/.claude/agents"

head2 "2. Settings"
copy_file "$BUNDLE/settings/user-settings.json"        "$HOME/.claude/settings.json"
copy_file "$BUNDLE/settings/user-settings.local.json"  "$HOME/.claude/settings.local.json"
copy_file "$BUNDLE/settings/project-settings.local.json" "$REPO/.claude/settings.local.json"
say "note: project-settings.local.json carries a 'remote.defaultEnvironmentId'"
say "      bound to the old machine's account. Harmless; clear it if unused."

head2 "3. Memory"
copy_into "$BUNDLE/memory"       "$MEMORY_DEST"
copy_into "$BUNDLE/agent-memory" "$REPO/.claude/agent-memory"

head2 "Done — remaining manual steps"
cat <<'EOF'
  4. Plugins    — launch claude; it should offer to install the two declared
                  plugins. Otherwise see README.md section 4.
  5. Secrets    — read claude-env/SECRETS.md. Five files must move by USB/scp:
                  keystore.jks, keystore.properties, local.properties,
                  'claude k' (API key), tkn (GitHub token).
  6. Build env  — read claude-env/BUILD-ENV.md. On x86_64, delete the
                  QEMU_LD_PREFIX line from build.sh.
  7. Log in     — claude, then /login (auth does not transfer).
  8. Verify     — ./build.sh test   (baseline: 916 tests, both variants green)
                  claude -> /agents  and  /memory
  Then read claude-env/STATE.md for where the work actually stands.
EOF
