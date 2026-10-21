@echo off
title Enable Spotify Backend Auto-Start
echo ========================================================
echo   Enabling Spotify Backend Auto-Start on Windows Boot
echo ========================================================
echo.

powershell -Command "$ws = New-Object -ComObject WScript.Shell; $s = $ws.CreateShortcut([Environment]::GetFolderPath('Startup') + '\Spotify_Backend_AutoStart.lnk'); $s.TargetPath = 'wscript.exe'; $s.Arguments = '\"%~dp0run_backend_silent.vbs\"'; $s.WorkingDirectory = '%~dp0'; $s.Save()"

if %ERRORLEVEL% EQU 0 (
    echo [SUCCESS] Auto-start shortcut added to Windows Startup folder!
    echo The backend and ngrok tunnel will now start silently in the background whenever your PC boots up.
) else (
    echo [ERROR] Failed to create startup shortcut.
)

echo ========================================================
echo.
pause
