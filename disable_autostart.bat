@echo off
title Disable Spotify Backend Auto-Start
echo ========================================================
echo   Disabling Spotify Backend Auto-Start on Windows Boot
echo ========================================================
echo.

powershell -Command "Remove-Item -Path ([Environment]::GetFolderPath('Startup') + '\Spotify_Backend_AutoStart.lnk') -ErrorAction SilentlyContinue"

echo [SUCCESS] Auto-start shortcut removed from Windows Startup folder.
echo ========================================================
echo.
pause
