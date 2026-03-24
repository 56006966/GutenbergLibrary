$ErrorActionPreference = "Stop"

# Example Project Gutenberg mirror sync script.
# Adjust the destination paths for your environment before running.

$MainDest = if ($env:MAIN_DEST) { $env:MAIN_DEST } else { "C:\gutenberg\main" }
$GeneratedDest = if ($env:GENERATED_DEST) { $env:GENERATED_DEST } else { "C:\gutenberg\generated" }
$RsyncHost = if ($env:RSYNC_HOST) { $env:RSYNC_HOST } else { "aleph.gutenberg.org" }

New-Item -ItemType Directory -Force -Path $MainDest | Out-Null
New-Item -ItemType Directory -Force -Path $GeneratedDest | Out-Null

rsync -avHS --timeout 600 --delete "$RsyncHost`::gutenberg" $MainDest
rsync -avHS --timeout 600 --delete "$RsyncHost`::gutenberg-epub" $GeneratedDest

Write-Host "Mirror sync complete."
