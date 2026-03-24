$ErrorActionPreference = "Stop"

$MirrorMainDir = if ($env:PG_MIRROR_MAIN_DIR) {
    $env:PG_MIRROR_MAIN_DIR
} else {
    "C:\gutenberg\main"
}

$MirrorGeneratedDir = if ($env:PG_MIRROR_GENERATED_DIR) {
    $env:PG_MIRROR_GENERATED_DIR
} else {
    "C:\gutenberg\generated"
}

$RsyncHost = if ($env:PG_RSYNC_HOST) {
    $env:PG_RSYNC_HOST
} else {
    "aleph.gutenberg.org"
}

New-Item -ItemType Directory -Force -Path $MirrorMainDir | Out-Null
New-Item -ItemType Directory -Force -Path $MirrorGeneratedDir | Out-Null

function Convert-ToWslPath([string]$WindowsPath) {
    $normalized = $WindowsPath -replace '\\', '/'
    if ($normalized -match '^([A-Za-z]):/(.*)$') {
        $drive = $matches[1].ToLower()
        $rest = $matches[2]
        return "/mnt/$drive/$rest"
    }
    return $normalized
}

function Invoke-Rsync([string]$ModuleName, [string]$DestinationWindowsPath) {
    $useWsl = $env:PG_USE_WSL_RSYNC
    if ([string]::IsNullOrWhiteSpace($useWsl)) {
        $useWsl = "1"
    }

    if ($useWsl -eq "1") {
        $destinationWslPath = Convert-ToWslPath $DestinationWindowsPath
        Write-Host "Running rsync through WSL -> $destinationWslPath"
        wsl rsync -avHS --timeout 600 --delete "${RsyncHost}::${ModuleName}" "$destinationWslPath"
    } else {
        Write-Host "Running native rsync -> $DestinationWindowsPath"
        rsync -avHS --timeout 600 --delete "${RsyncHost}::${ModuleName}" $DestinationWindowsPath
    }
}

Write-Host "Syncing Project Gutenberg main collection to: $MirrorMainDir"
Invoke-Rsync "gutenberg" $MirrorMainDir

Write-Host "Syncing Project Gutenberg generated collection to: $MirrorGeneratedDir"
Invoke-Rsync "gutenberg-epub" $MirrorGeneratedDir

Write-Host "Mirror sync complete."
