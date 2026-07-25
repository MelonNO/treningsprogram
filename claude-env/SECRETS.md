# Secrets — move these by hand

This repository is **public**. None of the files below are in git, and none of
them should ever be committed. Move them to the new machine over a channel you
trust (USB stick, `scp` over SSH, a password manager) and place them at the
paths given.

`.gitignore` already covers every one of them — but double-check with
`git status --ignored` after copying, before your first commit on the new box.

| File | Path on new machine | What breaks without it |
|------|--------------------|------------------------|
| `keystore.jks` | `<repo>/keystore.jks` | Signed release builds. Debug builds still work. |
| `keystore.properties` | `<repo>/keystore.properties` | Same — holds `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. |
| `local.properties` | `<repo>/local.properties` | Gradle can't find the SDK. Trivial to recreate: one line, `sdk.dir=/path/to/android-sdk`. |
| `claude k` | `<repo>/claude k` | Live Anthropic API generation tests. Just the `sk-ant-…` key. |
| `tkn` | `<repo>/tkn` | `git push` / release publishing over HTTPS. A GitHub PAT. |

Plus one outside the repo:

| File | Path | Notes |
|------|------|-------|
| `~/.git-credentials` | `$HOME/.git-credentials` | Used by the release flow to push. Recreate it, or just use `gh auth login` / an SSH remote instead. |

## Do **not** copy

- `~/.claude/.credentials.json` — Claude Code OAuth token, bound to the old
  install. Run `claude` then `/login` on the new machine instead.

## Losing the keystore matters

`keystore.jks` is the **release signing key**. If it's lost, you cannot publish
an update that Android will accept as the same app — users would have to
uninstall and reinstall, losing their local workout database. Back it up
somewhere durable before wiping the Pi, not just onto the new machine.

## If a secret ever does get committed

Rotate it rather than trying to scrub history — a public repo is scraped within
minutes. Revoke the Anthropic key in the console, revoke the PAT in GitHub
settings, and generate replacements.
