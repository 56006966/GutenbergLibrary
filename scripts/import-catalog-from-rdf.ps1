$ErrorActionPreference = "Stop"

$JavaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Android\Android Studio\jbr" }
$Javac = Join-Path $JavaHome "bin\javac.exe"
$Java = Join-Path $JavaHome "bin\java.exe"

$SourceRoot = "backend\src\main\java"
$BuildRoot = "backend\build\classes"
$MainClass = "com.phunkypixels.projectgutenberglibrary.backend.CatalogImportTool"

New-Item -ItemType Directory -Force -Path $BuildRoot | Out-Null

$Sources = Get-ChildItem -Path $SourceRoot -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& $Javac -d $BuildRoot $Sources
& $Java -cp $BuildRoot $MainClass
