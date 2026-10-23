@echo off
title Disable Spotify Backend Auto-Start
echo ========================================================
echo   Disabling Spotify Backend Auto-Start on Windows Boot
echo ========================================================
echo.

REM Remove Task Scheduler task (new method)
schtasks /delete /tn "Spotify_Backend_AutoStart" /f >nul 2>&1

REM Also remove legacy Startup folder shortcut (old method cleanup)
powershell -Command "Remove-Item -Path ([Environment]::GetFolderPath('Startup') + '\Spotify_Backend_AutoStart.lnk') -ErrorAction SilentlyContinue"

echo [SUCCESS] Auto-start has been fully disabled (Task Scheduler + Startup folder).
echo ========================================================
echo.
pause
