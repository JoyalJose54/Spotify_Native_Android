@echo off
title Enable Spotify Backend Auto-Start (Low Impact)
echo ========================================================
echo   Enabling Spotify Backend Auto-Start (Low Impact Mode)
echo ========================================================
echo.
echo [INFO] Using Task Scheduler with a 2-minute delay after login.
echo        This avoids high startup impact on Windows boot.
echo.

REM Remove old Startup folder shortcut if it exists (cleanup legacy setup)
powershell -Command "Remove-Item -Path ([Environment]::GetFolderPath('Startup') + '\Spotify_Backend_AutoStart.lnk') -ErrorAction SilentlyContinue"

REM Register a scheduled task that runs 2 minutes after login (low startup impact)
REM Requires Administrator privileges - will auto-elevate via PowerShell
powershell -Command "Start-Process powershell -ArgumentList '-NoProfile -ExecutionPolicy Bypass -Command \"$action = New-ScheduledTaskAction -Execute ''wscript.exe'' -Argument ''\"\"\"\"''%~dp0run_backend_silent.vbs''\"\"\"\"''; $trigger = New-ScheduledTaskTrigger -AtLogOn; $trigger.Delay = ''PT2M''; $settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Hours 0) -MultipleInstances IgnoreNew; Register-ScheduledTask -TaskName ''Spotify_Backend_AutoStart'' -Action $action -Trigger $trigger -Settings $settings -RunLevel Highest -Force; Write-Host ''Task registered.''\"' -Verb RunAs -Wait"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [SUCCESS] Scheduled task created!
    echo The backend will auto-start 2 minutes after you log in,
    echo keeping your Windows startup fast and low-impact.
) else (
    echo.
    echo [ERROR] Failed. Please run this script as Administrator.
)

echo ========================================================
echo.
pause
