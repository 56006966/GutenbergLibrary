@echo off
setlocal

set "PG_MIRROR_MAIN_DIR=C:\gutenberg\main"
set "PG_MIRROR_GENERATED_DIR=C:\gutenberg\generated"
set "PG_RSYNC_HOST=aleph.gutenberg.org"
set "PG_USE_WSL_RSYNC=1"

powershell.exe -ExecutionPolicy Bypass -File "%~dp0sync-mirror.ps1"

endlocal
