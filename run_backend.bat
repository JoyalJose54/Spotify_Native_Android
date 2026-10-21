@echo off
title Spotify Ingestion Backend (PC + Cloudflare Tunnel)
echo ========================================================
echo   Starting Spotify Ingestion Backend on PC
echo ========================================================
echo.

cd /d "%~dp0cloud_functions"

set PY_EXE=python
if exist "C:\Python313\python.exe" set PY_EXE=C:\Python313\python.exe

echo [1/2] Launching Python Backend on http://127.0.0.1:8080...
start "Spotify Backend Server" cmd /k "%PY_EXE% main.py"

echo [2/2] Launching Cloudflare Tunnel for secure mobile access...
echo.
echo Look for the link ending in .trycloudflare.com below!
echo Copy that link and paste it in your Spotify app settings if needed.
echo ========================================================
echo.

"C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel --url http://127.0.0.1:8080
pause
