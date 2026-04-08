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

$PopularityOutput = if ($env:CATALOG_POPULARITY_OUTPUT) {
    $env:CATALOG_POPULARITY_OUTPUT
} else {
    "backend\data\popular_books.tsv"
}

if ($env:CATALOG_REFRESH_POPULARITY -eq "1") {
    Write-Host "Refreshing official popularity snapshot..."
    $env:CATALOG_POPULARITY_OUTPUT = $PopularityOutput
    powershell -ExecutionPolicy Bypass -File ".\scripts\refresh-popularity-snapshot.ps1"
}

$env:PG_MIRROR_GENERATED_DIR = $MirrorGeneratedDir
$env:CATALOG_IMPORT_OUTPUT = $CatalogOutput
$env:CATALOG_POPULARITY_INPUT = $PopularityOutput

Write-Host "Building offline catalog from mirror metadata..."
powershell -ExecutionPolicy Bypass -File ".\scripts\import-from-local-mirror.ps1"
