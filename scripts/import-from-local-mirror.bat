@echo off
setlocal

if "%PG_MIRROR_GENERATED_DIR%"=="" set "PG_MIRROR_GENERATED_DIR=C:\gutenberg\generated"
if "%CATALOG_IMPORT_OUTPUT%"=="" set "CATALOG_IMPORT_OUTPUT=backend\data\catalog.tsv"

if not exist "%PG_MIRROR_GENERATED_DIR%" (
  echo Mirror directory not found: %PG_MIRROR_GENERATED_DIR%
  exit /b 1
)

set "CATALOG_IMPORT_INPUT=%PG_MIRROR_GENERATED_DIR%"
echo Importing RDF metadata from: %CATALOG_IMPORT_INPUT%
echo Writing catalog TSV to: %CATALOG_IMPORT_OUTPUT%

call scripts\import-catalog-from-rdf.bat

endlocal
