$ErrorActionPreference = "Stop"

$OutputPath = if ($env:CATALOG_POPULARITY_OUTPUT) {
    $env:CATALOG_POPULARITY_OUTPUT
} else {
    "backend\data\popular_books.tsv"
}

$MaxBooks = if ($env:CATALOG_POPULARITY_LIMIT) {
    [int]$env:CATALOG_POPULARITY_LIMIT
} else {
    100
}

$PageSize = 25
$Collected = New-Object System.Collections.Generic.List[object]
$StartIndex = 1

while ($Collected.Count -lt $MaxBooks) {
    $Url = "https://www.gutenberg.org/ebooks/search/?sort_order=downloads&start_index=$StartIndex"
    $Response = Invoke-WebRequest -Uri $Url -UseBasicParsing
    $Matches = [regex]::Matches(
        $Response.Content,
        '/ebooks/(?<id>\d+)".*?(?<downloads>\d+)\s+downloads',
        [System.Text.RegularExpressions.RegexOptions]::Singleline
    )

    if ($Matches.Count -eq 0) {
        break
    }

    foreach ($Match in $Matches) {
        $BookId = [int]$Match.Groups["id"].Value
        $Downloads = [int]$Match.Groups["downloads"].Value

        if (-not ($Collected | Where-Object { $_.Id -eq $BookId })) {
            $Collected.Add([pscustomobject]@{
                Id = $BookId
                Downloads = $Downloads
            })
        }

        if ($Collected.Count -ge $MaxBooks) {
            break
        }
    }

    $StartIndex += $PageSize
}

if ($Collected.Count -eq 0) {
    throw "No popularity entries were parsed from Project Gutenberg."
}

$Directory = Split-Path -Parent $OutputPath
if ($Directory) {
    New-Item -ItemType Directory -Force -Path $Directory | Out-Null
}

$Lines = @(
    "# Project Gutenberg popularity snapshot"
    "# Source: https://www.gutenberg.org/ebooks/search/?sort_order=downloads"
    "# Format: ebook_id<TAB>download_count"
) + ($Collected | Select-Object -First $MaxBooks | ForEach-Object {
    "$($_.Id)`t$($_.Downloads)"
})

Set-Content -Path $OutputPath -Value $Lines -Encoding UTF8

Write-Host "Wrote $($Collected.Count) popularity entries to $OutputPath"
