@echo off
rem Double-click to sync the phone with iTunes. See sync-library.ps1.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0sync-library.ps1" %*
pause
