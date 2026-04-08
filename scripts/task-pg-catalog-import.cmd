@echo off
setlocal

set "PG_MIRROR_GENERATED_DIR=C:\gutenberg\generated"
set "CATALOG_IMPORT_OUTPUT=C:\Users\kdhuf\AndroidStudioProjects\ProjectGutenberg\backend\data\catalog.tsv"
set "CATALOG_POPULARITY_OUTPUT=C:\Users\kdhuf\AndroidStudioProjects\ProjectGutenberg\backend\data\popular_books.tsv"
set "CATALOG_REFRESH_POPULARITY=1"

cd /d "C:\Users\kdhuf\AndroidStudioProjects\ProjectGutenberg"
powershell.exe -ExecutionPolicy Bypass -File "%~dp0build-offline-catalog.ps1"

endlocal
