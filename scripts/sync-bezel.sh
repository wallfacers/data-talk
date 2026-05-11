#!/usr/bin/env bash
# sync-bezel.sh — vendor bezel skill content into server resources at a pinned tag.
set -euo pipefail

BEZEL_TAG="${1:-}"
if [[ -z "$BEZEL_TAG" ]]; then
  echo "usage: $0 <git-tag-or-branch-or-commit>" >&2
  exit 2
fi

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERVER_RES="$REPO_ROOT/server/data-talk-infrastructure/src/main/resources/opencode"
TMP="$(mktemp -d)"
trap "rm -rf $TMP" EXIT

# Prefer local checkout if available (for offline / dev), else clone from remote.
if [[ -d "$HOME/workspace/github/bezel/.git" ]]; then
  # Use --local clone to avoid worktree conflicts when the ref is already checked out.
  git clone --local --branch "$BEZEL_TAG" "$HOME/workspace/github/bezel" "$TMP/bezel" 2>/dev/null \
    || git clone --local "$HOME/workspace/github/bezel" "$TMP/bezel"
  if [[ "$BEZEL_TAG" != "main" && "$BEZEL_TAG" != "master" ]]; then
    git -C "$TMP/bezel" checkout "$BEZEL_TAG" 2>/dev/null || true
  fi
else
  git clone --depth 1 --branch "$BEZEL_TAG" https://github.com/wallfacers/bezel "$TMP/bezel"
fi

rm -rf "$SERVER_RES/skills-src/bezel"
mkdir -p "$SERVER_RES/skills-src/bezel"
cp -r "$TMP/bezel/." "$SERVER_RES/skills-src/bezel/"
rm -rf "$SERVER_RES/skills-src/bezel/.git"

mkdir -p "$SERVER_RES/skills"
echo "$BEZEL_TAG" > "$SERVER_RES/skills/bezel.version"

git -C "$REPO_ROOT" add "$SERVER_RES/skills-src/bezel" "$SERVER_RES/skills/bezel.version"
echo "synced bezel@$BEZEL_TAG into $SERVER_RES; review with 'git diff --cached' and commit"
