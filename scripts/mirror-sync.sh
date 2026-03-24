#!/usr/bin/env bash
set -euo pipefail

# Example Project Gutenberg mirror sync script.
# Adjust the destination paths for your environment before running.

MAIN_DEST="${MAIN_DEST:-/srv/gutenberg-main}"
GENERATED_DEST="${GENERATED_DEST:-/srv/gutenberg-generated}"
RSYNC_HOST="${RSYNC_HOST:-aleph.gutenberg.org}"

mkdir -p "$MAIN_DEST"
mkdir -p "$GENERATED_DEST"

rsync -avHS --timeout 600 --delete \
  "${RSYNC_HOST}::gutenberg" \
  "$MAIN_DEST"

rsync -avHS --timeout 600 --delete \
  "${RSYNC_HOST}::gutenberg-epub" \
  "$GENERATED_DEST"

echo "Mirror sync complete."
