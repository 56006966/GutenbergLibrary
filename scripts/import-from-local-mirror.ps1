$ErrorActionPreference = "Stop"

$MirrorGeneratedDir = if ($env:PG_MIRROR_GENERATED_DIR) {
    $env:PG_MIRROR_GENERATED_DIR
} else {
    "C:\gutenberg\generated"
}

$CatalogOutput = if ($env:CATALOG_IMPORT_OUTPUT) {
    $env:CATALOG_IMPORT_OUTPUT
} else {
    "backend\data\catalog.tsv"
}

if (-not (Test-Path $MirrorGeneratedDir)) {
    throw "Mirror directory not found: $MirrorGeneratedDir"
}

$env:CATALOG_IMPORT_INPUT = $MirrorGeneratedDir
$env:CATALOG_IMPORT_OUTPUT = $CatalogOutput

Write-Host "Importing RDF metadata from: $MirrorGeneratedDir"
Write-Host "Writing catalog TSV to: $CatalogOutput"

powershell -ExecutionPolicy Bypass -File ".\scripts\import-catalog-from-rdf.ps1"
