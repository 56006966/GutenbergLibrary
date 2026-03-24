@echo off
setlocal EnableDelayedExpansion

if "%JAVA_HOME%"=="" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"

set "JAVAC=%JAVA_HOME%\bin\javac.exe"
set "JAVA=%JAVA_HOME%\bin\java.exe"
set "BUILDROOT=backend\build\classes"
set "SOURCEROOT=backend\src\main\java"

if not exist "%BUILDROOT%" mkdir "%BUILDROOT%"

set "SOURCES="
for /r "%SOURCEROOT%" %%f in (*.java) do (
  set SOURCES=!SOURCES! "%%f"
)

call "%JAVAC%" -d "%BUILDROOT%" %SOURCES%
call "%JAVA%" -cp "%BUILDROOT%" com.kdhuf.projectgutenberglibrary.backend.CatalogImportTool

endlocal
