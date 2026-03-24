@echo off
setlocal

if "%PG_MIRROR_MAIN_DIR%"=="" set "PG_MIRROR_MAIN_DIR=C:\gutenberg\main"
if "%PG_MIRROR_GENERATED_DIR%"=="" set "PG_MIRROR_GENERATED_DIR=C:\gutenberg\generated"
if "%PG_RSYNC_HOST%"=="" set "PG_RSYNC_HOST=aleph.gutenberg.org"

powershell -ExecutionPolicy Bypass -File "%~dp0sync-mirror.ps1"

endlocal
