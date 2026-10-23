@echo off
title Spotify Server Manager
cd /d "%~dp0"

if exist "Spotify_Server_Manager.exe" (
    start "" "%~dp0Spotify_Server_Manager.exe"
    exit /b 0
)

if exist "C:\Python313\pythonw.exe" (
    start "" "C:\Python313\pythonw.exe" "%~dp0spotify_server_control.pyw"
    exit /b 0
)

start "" pythonw.exe "%~dp0spotify_server_control.pyw"
exit /b 0
