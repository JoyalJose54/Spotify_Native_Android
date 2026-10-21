#!/bin/bash

# 1. Kill any existing instances of main.py to avoid port conflicts
pkill -f "python3 main.py" || true

# Get the absolute path of the project root
PROJECT_ROOT=$(pwd)

echo "🚀 Activating Virtual Environment and Installing Dependencies..."
cd "$PROJECT_ROOT/cloud_functions"

# Create venv if somehow it's missing, otherwise just activate
if [ ! -f "venv/bin/activate" ]; then
    python3 -m venv venv
fi
source venv/bin/activate

# Install requirements inside the venv
pip install -r requirements.txt --quiet

echo "🚀 Starting Python Ingestion Backend..."
# Run backend in background and save its process ID
python3 main.py > backend.log 2>&1 &
BACKEND_PID=$!

echo "⚡ Backend started on http://127.0.0.1:8080 (PID: $BACKEND_PID)"
echo "📱 To install and run the Native Android app on a connected device/emulator:"
echo "   ./gradlew installDebug"
echo ""
echo "Press Ctrl+C to stop the backend."

trap "echo 'Stopping Backend...'; kill $BACKEND_PID; exit" INT TERM
wait $BACKEND_PID
