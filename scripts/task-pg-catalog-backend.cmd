@echo off
setlocal

set "CATALOG_BACKEND_HOST=0.0.0.0"
set "CATALOG_BACKEND_PORT=8080"
set "CATALOG_PUBLIC_BASE_URL=https://api.yourdomain.com"

cd /d "C:\Users\kdhuf\AndroidStudioProjects\ProjectGutenberg"
powershell.exe -ExecutionPolicy Bypass -File "%~dp0run-catalog-backend.ps1"

endlocal
