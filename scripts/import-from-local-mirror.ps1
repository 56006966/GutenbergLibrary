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

$PopularitySnapshot = if ($env:CATALOG_POPULARITY_INPUT) {
    $env:CATALOG_POPULARITY_INPUT
} else {
    "backend\data\popular_books.tsv"
}

if (-not (Test-Path $MirrorGeneratedDir)) {
    throw "Mirror directory not found: $MirrorGeneratedDir"
}

$env:CATALOG_IMPORT_INPUT = $MirrorGeneratedDir
$env:CATALOG_IMPORT_OUTPUT = $CatalogOutput
if (Test-Path $PopularitySnapshot) {
    $env:CATALOG_POPULARITY_INPUT = $PopularitySnapshot
}

Write-Host "Importing RDF metadata from: $MirrorGeneratedDir"
Write-Host "Writing catalog TSV to: $CatalogOutput"
if ($env:CATALOG_POPULARITY_INPUT) {
    Write-Host "Merging popularity snapshot from: $env:CATALOG_POPULARITY_INPUT"
} else {
    Write-Host "No popularity snapshot found; download counts will default to 0."
}

powershell -ExecutionPolicy Bypass -File ".\scripts\import-catalog-from-rdf.ps1"
