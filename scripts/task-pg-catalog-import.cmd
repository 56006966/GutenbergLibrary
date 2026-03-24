@echo off
setlocal

set "PG_MIRROR_GENERATED_DIR=C:\gutenberg\generated"
set "CATALOG_IMPORT_OUTPUT=C:\Users\kdhuf\AndroidStudioProjects\ProjectGutenberg\backend\data\catalog.tsv"

cd /d "C:\Users\kdhuf\AndroidStudioProjects\ProjectGutenberg"
powershell.exe -ExecutionPolicy Bypass -File "%~dp0import-from-local-mirror.ps1"

endlocal
