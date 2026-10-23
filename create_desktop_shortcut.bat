@echo off
title Create Spotify Server Manager Desktop Shortcut
echo Creating Desktop shortcut for Spotify Server Manager...

powershell -NoProfile -ExecutionPolicy Bypass -Command "$wsh = New-Object -ComObject WScript.Shell; $desktop = [Environment]::GetFolderPath('Desktop'); $s = $wsh.CreateShortcut((Join-Path $desktop 'Spotify Server Manager.lnk')); $s.TargetPath = '%~dp0Spotify_Server_Manager.exe'; $s.WorkingDirectory = '%~dp0'; $s.IconLocation = '%~dp0spotify_logo.ico,0'; $s.Description = 'Spotify Backend & Ngrok Server Control'; $s.Save(); Write-Host 'Done!'"

echo.
echo [SUCCESS] Shortcut with Spotify logo created on your Desktop!
echo.
pause
